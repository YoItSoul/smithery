package com.soul.smithery.block.entity;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Sand-casting workbench. State machine:
 *
 *   EMPTY ─[casting_sand]→ SAND
 *   SAND ─[part item template, not consumed]→ IMPRESSED  (records PartType + requiredMb)
 *   IMPRESSED ─[fluid pour]→ FILLING                       (rejects mismatched fluid mid-fill)
 *   FILLING@requiredMb → COOLING                            (timer = mB / 5 ticks)
 *   COOLING done → COVERED                                  (sand re-coats the cooled part)
 *   COVERED ─[brush × 4]→ READY
 *   READY ─[right-click empty hand]→ part item drops, state → EMPTY (sand destroyed)
 *
 * Step 2 implements EMPTY→SAND and SAND→IMPRESSED. The remaining transitions are
 * stubbed in the enum so save/load tolerates them when added later.
 */
public class CastingTableBlockEntity extends BlockEntity {

    public enum State {
        EMPTY, SAND, IMPRESSED, FILLING, COOLING, COVERED, READY;

        static State byName(String name) {
            for (State s : values()) if (s.name().equals(name)) return s;
            return EMPTY;
        }
    }

    /** Number of brush strokes needed to fully sweep the sand off (matches suspicious-sand pacing). */
    public static final int MAX_BRUSH = 4;

    private State state = State.EMPTY;
    private @Nullable Identifier impressedPartTypeId; // set when state >= IMPRESSED
    private int requiredMb = 0;                       // set when state >= IMPRESSED
    private int brushProgress = 0;                    // 0..MAX_BRUSH, resets on state transition

    public CastingTableBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.CASTING_TABLE.get(), pos, state);
    }

    // ---- Accessors ----

    public State state() { return state; }
    public @Nullable Identifier impressedPartTypeId() { return impressedPartTypeId; }
    public int requiredMb() { return requiredMb; }
    public int brushProgress() { return brushProgress; }

    // ---- Interactions ----

    /** EMPTY → SAND. Returns true if the state advanced (caller should consume 1 sand). */
    public boolean tryFillSand() {
        if (state != State.EMPTY) return false;
        state = State.SAND;
        markDirtyAndSync();
        return true;
    }

    /**
     * SAND → IMPRESSED. Returns true if the state advanced (template is NOT consumed by
     * the caller). Records the PartType and the mB this part requires when cast.
     */
    public boolean tryImpressPart(ItemStack stack) {
        if (state != State.SAND) return false;
        if (!(stack.getItem() instanceof PartItem partItem)) return false;
        PartType pt = SmitheryAPI.PART_TYPES.get(partItem.partTypeId());
        if (pt == null) return false;
        impressedPartTypeId = partItem.partTypeId();
        requiredMb = pt.castMb();
        state = State.IMPRESSED;
        markDirtyAndSync();
        return true;
    }

    /**
     * Apply one brush stroke. Works in any state where there's sand on top:
     * SAND, IMPRESSED, or COVERED. Increments {@link #brushProgress}; on hitting
     * {@link #MAX_BRUSH} the sand layer is considered swept away and the state
     * advances accordingly:
     *
     *   SAND      → EMPTY  (just sand, no impression — back to a bare table)
     *   IMPRESSED → EMPTY  (impression abandoned; PartType + requiredMb cleared)
     *   COVERED   → READY  (cooled part is exposed and can be picked up)
     *
     * Returns true if the brush was actually applied — false in states where
     * brushing has no effect (EMPTY, FILLING, COOLING, READY) so the caller can
     * pass the interaction through to vanilla behavior.
     */
    public boolean tryBrush() {
        boolean accepts = switch (state) {
            case SAND, IMPRESSED, COVERED -> true;
            default -> false;
        };
        if (!accepts) return false;

        brushProgress++;
        if (brushProgress >= MAX_BRUSH) {
            brushProgress = 0;
            switch (state) {
                case SAND, IMPRESSED -> {
                    state = State.EMPTY;
                    impressedPartTypeId = null;
                    requiredMb = 0;
                }
                case COVERED -> state = State.READY;
                default -> {}
            }
        }
        markDirtyAndSync();
        return true;
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("state", state.name());
        if (impressedPartTypeId != null) {
            output.putString("impressedPartType", impressedPartTypeId.toString());
        }
        if (requiredMb > 0) {
            output.putInt("requiredMb", requiredMb);
        }
        if (brushProgress > 0) {
            output.putInt("brushProgress", brushProgress);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        state = input.getString("state").map(State::byName).orElse(State.EMPTY);
        Optional<String> ptStr = input.getString("impressedPartType");
        impressedPartTypeId = ptStr.map(Identifier::tryParse).orElse(null);
        requiredMb = input.getInt("requiredMb").orElse(0);
        brushProgress = input.getInt("brushProgress").orElse(0);
    }

    // ---- Sync helpers ----

    /** Marks the BE dirty + nudges chunk sync so the BER picks up state changes. */
    private void markDirtyAndSync() {
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * sendBlockUpdated() above relies on this packet to actually carry our NBT to
     * the client. Default getUpdatePacket() returns null.
     */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Default getUpdateTag() returns an EMPTY CompoundTag — that's the actual data
     * shipped by ClientboundBlockEntityDataPacket.create(this). Without this
     * override the client receives a sync packet with no fields, leaving the
     * client BE stuck on state=EMPTY no matter what the server does. saveCustomOnly
     * runs our saveAdditional() through the BE's NBT pipeline.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
