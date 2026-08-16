package com.soul.smithery.block.entity;

import com.soul.smithery.Config;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Electric heat source for the Forge multiblock.
 *
 * <p>Takes RF on every face and holds the forge at a player-set target temperature, in
 * place of burning fuel. Deliberately the least efficient heat source there is: the draw
 * rises with the <em>square</em> of the target temperature, so the very high ceilings it
 * can reach are paid for steeply. A linear draw would have made the coil cheaper per
 * millibucket the hotter it ran — melt rate rises with temperature too, and the two
 * cancel — which is the opposite of what an electric element should feel like.
 *
 * <p>The coil never cools the forge. When power runs out the forge keeps whatever heat it
 * has and cools on its own, and any fuel in the ports takes back over.
 */
public class ForgeRfCoilBlockEntity extends BlockEntity {

    /** Adjustment step for the target temperature, in degrees C. */
    public static final int TARGET_STEP = 250;

    private int energy;
    private int targetTemperatureC = 1200;
    /** Whether the coil could pay for its draw last tick; drives the lit blockstate. */
    private boolean runningLastTick;

    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(capacity() - energy, maxReceive);
            if (!simulate && accepted > 0) {
                energy += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    private final LazyOptional<IEnergyStorage> storageCap = LazyOptional.of(() -> storage);

    /**
     * Constructs a coil block entity bound to the given position and state.
     */
    public ForgeRfCoilBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_RF_COIL.get(), pos, state);
    }

    /**
     * Buffer size, derived from the draw at maximum temperature rather than configured
     * separately, so it stays worth the same number of seconds however the cost or the
     * ceiling are retuned.
     */
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE,
                (long) drawAt(maxTemperatureC(), 0) * 20L * Config.RF_COIL_BUFFER_SECONDS.get());
    }

    /** Configured ceiling for the target temperature. */
    public static int maxTemperatureC() {
        return Config.RF_COIL_MAX_TEMP.get();
    }

    /**
     * RF per tick to hold {@code target} with {@code storedMb} of melt in the forge.
     *
     * <p>Quadratic in temperature: doubling the target quadruples the bill. The thermal
     * mass term is the cost of keeping a full forge hot rather than an empty one.
     */
    public static int drawAt(int target, int storedMb) {
        double coeff = Config.RF_COIL_COEFFICIENT.get();
        double base = coeff * (double) target * (double) target / 1650.0;
        double mass = storedMb / (double) Config.RF_COIL_THERMAL_MASS_DIVISOR.get();
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base + mass));
    }

    /** The temperature the player has asked the coil to hold. */
    public int targetTemperatureC() { return targetTemperatureC; }

    /** True iff the coil paid its way on the previous controller tick. */
    public boolean isRunning() { return runningLastTick; }

    /** Current buffer contents in RF. */
    public int energyStored() { return energy; }

    /**
     * Nudges the target temperature by {@code steps} increments, clamped to the
     * configured ceiling. Returns the new target.
     */
    public int adjustTarget(int steps) {
        int max = maxTemperatureC();
        targetTemperatureC = Math.max(TARGET_STEP,
                Math.min(max, targetTemperatureC + steps * TARGET_STEP));
        setChanged();
        sync();
        return targetTemperatureC;
    }

    /**
     * Charges the coil for one tick of running at its target. Returns the temperature the
     * forge should climb toward, or -1 when the coil cannot pay and the forge should fall
     * back to fuel.
     *
     * <p>Called by the controller rather than a ticker of its own: the draw depends on how
     * much melt the forge is holding, which only the controller knows.
     */
    public float consumeForTick(int storedMb) {
        int cost = drawAt(targetTemperatureC, storedMb);
        if (energy < cost) {
            if (runningLastTick) {
                runningLastTick = false;
                sync();
            }
            return -1f;
        }
        energy -= cost;
        if (!runningLastTick) {
            runningLastTick = true;
            sync();
        }
        setChanged();
        return targetTemperatureC;
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return storageCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        storageCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy);
        tag.putInt("Target", targetTemperatureC);
        tag.putBoolean("Running", runningLastTick);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = tag.getInt("Energy");
        targetTemperatureC = tag.contains("Target") ? tag.getInt("Target") : 1200;
        runningLastTick = tag.getBoolean("Running");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
