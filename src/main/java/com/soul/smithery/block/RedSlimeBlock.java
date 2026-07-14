package com.soul.smithery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Slime block tinted red that also emits a full redstone signal on every side. All
 * vanilla slime physics (sticky neighbour-pull, fall bounce, low friction) are inherited
 * unchanged; only the {@code is/get*Signal} overrides add the constant power source.
 */
public class RedSlimeBlock extends SlimeBlock {
    /**
     * Constructs the red slime block with the given block properties.
     */
    public RedSlimeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }
}
