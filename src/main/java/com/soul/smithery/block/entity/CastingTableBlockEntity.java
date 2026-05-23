package com.soul.smithery.block.entity;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
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
    /** Per-mB cooling rate (in ticks). matches the inline state-machine comment: timer = mB / 5. */
    public static final int COOLING_TICKS_PER_MB = 1;

    private State state = State.EMPTY;
    private @Nullable Identifier impressedPartTypeId; // set when state >= IMPRESSED
    private int requiredMb = 0;                       // set when state >= IMPRESSED
    private int brushProgress = 0;                    // 0..MAX_BRUSH, resets on state transition

    // ---- Filling / cooling state ----
    // pouredFluid stays Fluids.EMPTY until first pour. filledMb counts toward requiredMb;
    // when it hits requiredMb we flip to COOLING and set coolingTicksRemaining.
    private Fluid pouredFluid = Fluids.EMPTY;
    private int   filledMb = 0;
    private int   coolingTicksRemaining = 0;

    public CastingTableBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.CASTING_TABLE.get(), pos, state);
    }

    // ---- Accessors ----

    public State state() { return state; }
    public @Nullable Identifier impressedPartTypeId() { return impressedPartTypeId; }
    public int requiredMb() { return requiredMb; }
    public int brushProgress() { return brushProgress; }
    public Fluid pouredFluid() { return pouredFluid; }
    public int filledMb() { return filledMb; }
    public int coolingTicksRemaining() { return coolingTicksRemaining; }

    /** True iff this table can currently accept poured fluid (IMPRESSED, or partially FILLING). */
    public boolean acceptsPour() {
        return state == State.IMPRESSED || (state == State.FILLING && filledMb < requiredMb);
    }

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
     * Pour fluid into an IMPRESSED or partially-FILLING table. Mismatched fluids are
     * rejected outright (return 0). Returns the actual mB accepted. Advances:
     *   IMPRESSED → FILLING (on first pour; records {@link #pouredFluid})
     *   FILLING@requiredMb → COOLING (kicks off the cooldown timer)
     */
    public int tryPourFluid(Fluid fluid, int mb) {
        if (fluid == null || fluid == Fluids.EMPTY || mb <= 0) return 0;
        if (!acceptsPour()) return 0;
        // Lock the fluid identity on first pour. Subsequent pours must match.
        if (state == State.IMPRESSED) {
            pouredFluid = fluid;
            filledMb = 0;
        } else if (pouredFluid != fluid) {
            return 0;
        }

        int accepted = Math.min(mb, requiredMb - filledMb);
        if (accepted <= 0) return 0;
        filledMb += accepted;

        if (state == State.IMPRESSED) {
            state = State.FILLING;
            // Pour invalidates any partial brushing the player did pre-pour.
            brushProgress = 0;
        }
        if (filledMb >= requiredMb) {
            state = State.COOLING;
            coolingTicksRemaining = Math.max(1, requiredMb * COOLING_TICKS_PER_MB / 5);
        }
        markDirtyAndSync();
        return accepted;
    }

    /** Server-tick hook for cooling countdown. Other transitions are interaction-driven. */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState blockState) {
        if (state != State.COOLING) return;
        if (coolingTicksRemaining > 0) {
            coolingTicksRemaining--;
            if (coolingTicksRemaining == 0) {
                state = State.COVERED;
                markDirtyAndSync();
            }
        }
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
                    // Brush-out of an IMPRESSED template doesn't normally see a pour, but reset
                    // defensively so a half-poured-then-brushed-back-to-EMPTY table stays clean.
                    pouredFluid = Fluids.EMPTY;
                    filledMb = 0;
                    coolingTicksRemaining = 0;
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
        if (pouredFluid != Fluids.EMPTY) {
            Identifier fluidId = BuiltInRegistries.FLUID.getKey(pouredFluid);
            if (fluidId != null) output.putString("pouredFluid", fluidId.toString());
        }
        if (filledMb > 0) {
            output.putInt("filledMb", filledMb);
        }
        if (coolingTicksRemaining > 0) {
            output.putInt("coolingTicks", coolingTicksRemaining);
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

        Optional<String> fluidStr = input.getString("pouredFluid");
        if (fluidStr.isPresent()) {
            Identifier fluidId = Identifier.tryParse(fluidStr.get());
            pouredFluid = fluidId == null
                    ? Fluids.EMPTY
                    : BuiltInRegistries.FLUID.get(fluidId).<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
        } else {
            pouredFluid = Fluids.EMPTY;
        }
        filledMb = input.getInt("filledMb").orElse(0);
        coolingTicksRemaining = input.getInt("coolingTicks").orElse(0);
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

    // ---- Fluid capability exposure ----
    //
    // Casting tables expose a *write-only* fluid endpoint: fluid pipes (and any other
    // mod's plumbing) can fill them, but they don't release fluid via the capability —
    // the cast is consumed by the player retrieving the part, not by draining.
    // Acceptance is gated by the table state, which already enforces "IMPRESSED or
    // partially FILLING and not full" via acceptsPour(). Implementation commits
    // immediately without transaction journaling (see FluidPipeBlockEntity for the
    // same caveat).

    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new TableFluidHandler();
    }

    private final class TableFluidHandler implements ResourceHandler<FluidResource> {
        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int slot) {
            return (pouredFluid == Fluids.EMPTY || filledMb <= 0)
                    ? FluidResource.EMPTY : FluidResource.of(pouredFluid);
        }

        @Override public long getAmountAsLong(int slot) { return filledMb; }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            return Math.max(0, requiredMb);
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            if (!acceptsPour() || resource.isEmpty()) return false;
            return pouredFluid == Fluids.EMPTY || resource.getFluid() == pouredFluid;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || !acceptsPour() || resource.isEmpty() || amount <= 0) return 0;
            if (pouredFluid != Fluids.EMPTY && resource.getFluid() != pouredFluid) return 0;
            return tryPourFluid(resource.getFluid(), amount);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            // Write-only — tables don't yield fluid back to the network.
            return 0;
        }
    }
}
