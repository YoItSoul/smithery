package com.soul.smithery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Slime block tinted red that also emits a full redstone signal — vanilla slime physics
 * (sticky on adjacent blocks, bouncy on entity fall, low friction) plus a constant power
 * source on every side like the vanilla Redstone Block.
 *
 * <p>All other behaviour comes from extending {@link SlimeBlock} unchanged, so block-of-block
 * sticky pulls / piston interactions / sound type / falling-mob bounce all match vanilla
 * slime exactly. The only adds are the three {@code is/get*Signal} overrides.
 *
 * <p>{@code codec()} returns the parent SlimeBlock codec — overriding with a narrower generic
 * is a compile error in Java (return-type covariance can't move from {@code MapCodec<SlimeBlock>}
 * to {@code MapCodec<RedSlimeBlock>}). The block-codec system uses the runtime class for
 * registry lookups, so re-using the parent codec is correct as long as the constructor
 * signature matches (it does — both take just {@code BlockBehaviour.Properties}).
 */
public class RedSlimeBlock extends SlimeBlock {
    public static final MapCodec<RedSlimeBlock> CODEC = simpleCodec(RedSlimeBlock::new);

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
