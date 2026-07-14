package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Visualization-only BE for fluid pipes. Pipes carry no buffered fluid; the
 * {@link ForgeDrainBlockEntity} pushes fluid directly into sinks and only marks the
 * pipes the wave is currently passing through. This BE stores that transient marker
 * (fluid id + decay timer) which the renderer reads to draw the moving molten cube.
 */
public class FluidPipeBlockEntity extends BlockEntity {

    /** Server ticks the visual marker persists after the last drain refresh. */
    public static final int FLOW_PERSIST_TICKS = 20;

    private @Nullable ResourceLocation transientFluidId;
    private int intensityTicks;

    /**
     * Constructs a fluid pipe BE bound to the given position and blockstate.
     */
    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    /** Registry id of the fluid the drain last marked passing through this pipe, or null. */
    public @Nullable ResourceLocation transientFluidId() { return transientFluidId; }
    /** Remaining persistence ticks until the visual marker fades. */
    public int intensityTicks() { return intensityTicks; }

    /**
     * Drain-side refresh: latches {@code fluidId} and resets the persistence timer.
     * Called each tick for every pipe currently inside the drain's wavefront.
     */
    public void markFlow(ResourceLocation fluidId) {
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

    /**
     * Per-tick decay of the flow marker. Clears the fluid id when intensity reaches zero
     * so the renderer goes inert.
     */
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
     * Immediate cancel of the flow marker. Used by the drain when a pipe stops being on
     * an active path, so the visual glow snaps off instead of decaying toward a cast that
     * is no longer being fed.
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (transientFluidId != null && intensityTicks > 0) {
            tag.putString("flow", transientFluidId.toString());
            tag.putInt("flowTicks", intensityTicks);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        transientFluidId = tag.contains("flow")
                ? ResourceLocation.tryParse(tag.getString("flow"))
                : null;
        intensityTicks = tag.getInt("flowTicks");
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }
}
