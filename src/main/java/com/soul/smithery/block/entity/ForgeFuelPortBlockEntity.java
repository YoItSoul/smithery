package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Internal fuel tank for a Forge fuel port. Holds up to {@link #CAPACITY_MB} of liquid fuel
 * (currently lava only). Stored as int — the controller may consume sub-millibucket amounts
 * per tick, but accumulation lives on the controller side.
 */
public class ForgeFuelPortBlockEntity extends BlockEntity {

    public static final int CAPACITY_MB = 6000;

    private int lavaMb = 0;

    public ForgeFuelPortBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_FUEL_PORT.get(), pos, state);
    }

    public int lavaMb() { return lavaMb; }
    public int remainingCapacityMb() { return CAPACITY_MB - lavaMb; }

    /** Returns the amount actually added (clamped by remaining capacity). */
    public int addLava(int mb) {
        int added = Math.min(mb, remainingCapacityMb());
        if (added > 0) {
            lavaMb += added;
            setChanged();
        }
        return added;
    }

    /** Returns the amount actually drained (clamped by current contents). */
    public int drainLava(int mb) {
        int drained = Math.min(mb, lavaMb);
        if (drained > 0) {
            lavaMb -= drained;
            setChanged();
        }
        return drained;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lavaMb = Math.max(0, Math.min(CAPACITY_MB, input.getInt("lavaMb").orElse(0)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("lavaMb", lavaMb);
    }

    // ---- Fluid capability exposure ----
    //
    // A fuel port is a lava-only tank: pipes can both push lava in and drain lava out,
    // mirroring the bucket interaction. Anything other than vanilla lava is rejected.

    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new FuelPortHandler();
    }

    private final class FuelPortHandler implements ResourceHandler<FluidResource> {
        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int slot) {
            return lavaMb <= 0 ? FluidResource.EMPTY : FluidResource.of(Fluids.LAVA);
        }

        @Override public long getAmountAsLong(int slot) { return lavaMb; }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            return CAPACITY_MB;
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            return !resource.isEmpty() && resource.getFluid() == Fluids.LAVA;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || resource.isEmpty() || amount <= 0) return 0;
            if (resource.getFluid() != Fluids.LAVA) return 0;
            return addLava(amount);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || lavaMb <= 0 || amount <= 0) return 0;
            if (!resource.isEmpty() && resource.getFluid() != Fluids.LAVA) return 0;
            return drainLava(amount);
        }
    }
}
