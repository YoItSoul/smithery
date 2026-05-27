package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Lava input/storage port for the Forge multiblock. Right-click with a lava bucket to add
 * 1000 mB (the bucket comes back empty). Right-click with an empty bucket to drain 1000 mB
 * (you get a lava bucket back). Capacity per port: {@link ForgeFuelPortBlockEntity#CAPACITY_MB}.
 *
 * <p>Carries two boolean blockstate properties — {@link #CONNECTED_UP} and
 * {@link #CONNECTED_DOWN} — that are set when an adjacent vertical neighbor is also a
 * ForgeFuelPort. The blockstate JSON uses them to swap in the appropriate "open cap"
 * texture variants so a vertical stack of ports reads as one continuous tank.
 */
public class ForgeFuelPortBlock extends Block implements EntityBlock {
    private static final int BUCKET_MB = 1000;

    public static final BooleanProperty CONNECTED_UP   = BooleanProperty.create("connected_up");
    public static final BooleanProperty CONNECTED_DOWN = BooleanProperty.create("connected_down");

    public ForgeFuelPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(CONNECTED_UP, false)
                .setValue(CONNECTED_DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTED_UP, CONNECTED_DOWN);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeFuelPortBlockEntity(pos, state);
    }

    /** Recomputes connection flags when a neighbor changes; called via {@code updateShape}. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     net.minecraft.util.RandomSource random) {
        if (direction == Direction.UP) {
            state = state.setValue(CONNECTED_UP, neighborState.getBlock() instanceof ForgeFuelPortBlock);
        } else if (direction == Direction.DOWN) {
            state = state.setValue(CONNECTED_DOWN, neighborState.getBlock() instanceof ForgeFuelPortBlock);
        }
        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        boolean up   = level.getBlockState(pos.above()).getBlock() instanceof ForgeFuelPortBlock;
        boolean down = level.getBlockState(pos.below()).getBlock() instanceof ForgeFuelPortBlock;
        return defaultBlockState().setValue(CONNECTED_UP, up).setValue(CONNECTED_DOWN, down);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ForgeFuelPortBlockEntity fp)) {
            return InteractionResult.PASS;
        }

        // Fill: lava bucket → empty bucket
        if (stack.is(Items.LAVA_BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (fp.remainingCapacityMb() < BUCKET_MB) {
                player.sendSystemMessage(Component.literal("Fuel port full ("
                        + fp.lavaMb() + " / " + ForgeFuelPortBlockEntity.CAPACITY_MB + " mB)")
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            fp.addFuel(net.minecraft.world.level.material.Fluids.LAVA, BUCKET_MB);
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            player.sendSystemMessage(Component.literal("Fuel: "
                    + fp.lavaMb() + " / " + ForgeFuelPortBlockEntity.CAPACITY_MB + " mB")
                    .withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }

        // Drain: empty bucket → lava bucket
        if (stack.is(Items.BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (fp.lavaMb() < BUCKET_MB) {
                player.sendSystemMessage(Component.literal("Not enough fuel to fill a bucket ("
                        + fp.lavaMb() + " mB)").withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            fp.drainLava(BUCKET_MB);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.LAVA_BUCKET))) {
                    player.drop(new ItemStack(Items.LAVA_BUCKET), false);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Bare-hand peek at the fuel level.
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ForgeFuelPortBlockEntity fp)) return InteractionResult.PASS;
        player.sendSystemMessage(Component.literal("Fuel: "
                + fp.lavaMb() + " / " + ForgeFuelPortBlockEntity.CAPACITY_MB + " mB")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
