package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
}
