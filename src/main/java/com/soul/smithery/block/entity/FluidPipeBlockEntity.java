package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Visualization-only BE for fluid pipes. Pipes do NOT buffer fluid — fluid is pushed directly
 * from the drain into the casting table via the drain's wavefront-gated dispatch (see
 * {@link ForgeDrainBlockEntity}). This BE only stores a transient flow marker the renderer
 * uses to draw the molten cube as the visual wave propagates through the network.
 *
 * Removing the buffer model fixes the "1 button press != 1 cast" regression: with no fluid
 * sitting in pipes between casts, the drain pumps exactly the cast's requiredMb per press,
 * with no leftover backing up to confuse subsequent presses.
 *
 * State:
 *   - {@link #transientFluidId}: registry id of the fluid the drain last marked through us.
 *   - {@link #intensityTicks}: counts down each server tick. The drain refreshes it on
 *     every pump tick the wave is touching us; when the drain stops refreshing it decays to
 *     zero and the renderer fades out.
 */
public class FluidPipeBlockEntity extends BlockEntity {

    /** Server ticks the visual marker persists after the last drain refresh. */
    public static final int FLOW_PERSIST_TICKS = 20;

    private @Nullable Identifier transientFluidId;
    private int intensityTicks;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    public @Nullable Identifier transientFluidId() { return transientFluidId; }
    public int intensityTicks() { return intensityTicks; }

    /**
     * Drain refresh: latches the fluid id and resets the persistence timer to full. Called
     * each tick by the drain for every pipe currently inside its expanding wavefront.
     */
    public void markFlow(Identifier fluidId) {
        boolean changed = !Objects.equals(this.transientFluidId, fluidId)
                       || this.intensityTicks < FLOW_PERSIST_TICKS;
        this.transientFluidId = fluidId;
        this.intensityTicks = FLOW_PERSIST_TICKS;
        if (changed) {
            setChanged();
            if (level instanceof ServerLevel sl) {
                sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /** Server-tick decay. Clears the fluid id when intensity hits 0 so the renderer goes inert. */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (intensityTicks <= 0) return;
        intensityTicks--;
        if (intensityTicks <= 0 && transientFluidId != null) {
            transientFluidId = null;
            setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * Immediate cancel — used by the drain when this pipe stops being on the active path
     * (the cast it was feeding finished, or the drain switched to a different cast). Without
     * this the pipe would visually glow for the full {@link #FLOW_PERSIST_TICKS} decay window
     * after stopping, which reads as "fluid still flowing toward a completed cast".
     */
    public void clearFlow() {
        if (intensityTicks <= 0 && transientFluidId == null) return;
        intensityTicks = 0;
        transientFluidId = null;
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (transientFluidId != null && intensityTicks > 0) {
            output.putString("flow", transientFluidId.toString());
            output.putInt("flowTicks", intensityTicks);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<String> flowStr = input.getString("flow");
        transientFluidId = flowStr.map(Identifier::tryParse).orElse(null);
        intensityTicks = input.getInt("flowTicks").orElse(0);
    }

    // ---- Sync ----

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
