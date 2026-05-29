package com.soul.smithery.block;

import com.soul.smithery.api.forge.ForgeFuels;
import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
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
 * Fluid input and storage port for the Forge multiblock. Right-click with a bucket of
 * a registered forge fuel to add 1000 mB, or with an empty bucket to drain 1000 mB of
 * the port's own fluid. Vertical stacks of ports share fluid identity and capacity via
 * {@link ForgeFuelPortBlockEntity}; the blockstate carries connection flags so vertical
 * runs render as a single continuous tank.
 */
public class ForgeFuelPortBlock extends Block implements EntityBlock {
    private static final int BUCKET_MB = 1000;

    /** True when the port directly above shares this port's fluid group; drives the open-cap variant. */
    public static final BooleanProperty CONNECTED_UP   = BooleanProperty.create("connected_up");
    /** True when the port directly below shares this port's fluid group; drives the open-cap variant. */
    public static final BooleanProperty CONNECTED_DOWN = BooleanProperty.create("connected_down");

    /**
     * Constructs the fuel port with the given block properties and both connection flags false.
     */
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

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ForgeFuelPortBlockEntity fp ? fp.lightLevel() : 0;
    }

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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) return;
        ForgeFuelPortBlockEntity.settleStack(level, pos);
        ForgeFuelPortBlockEntity.refreshConnectivity(level, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ForgeFuelPortBlockEntity fp)) {
            return InteractionResult.PASS;
        }

        if (stack.getItem() instanceof BucketItem bucket && bucket.content != null
                && ForgeFuels.isFuel(bucket.content)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            Fluid fluid = bucket.content;
            int existing  = fp.stackFuelMb(fluid);
            int totalUsed = fp.stackFuelMb();
            int stackCap  = fp.stackCapacityMb();
            if (stackCap - totalUsed < BUCKET_MB) {
                player.sendSystemMessage(Component.literal("Fuel stack full ("
                        + totalUsed + " / " + stackCap + " mB)")
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            int added = fp.addFuelToStack(fluid, BUCKET_MB);
            if (added <= 0) {
                player.sendSystemMessage(Component.literal(
                        "No fuel port in this stack can accept " + fluidName(fluid))
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            player.sendSystemMessage(Component.literal(fluidName(fluid) + ": "
                    + fp.stackFuelMb(fluid) + " mB  |  Stack: "
                    + fp.stackFuelMb() + " / " + fp.stackCapacityMb() + " mB")
                    .withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.BUCKET)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            Fluid portFluid = fp.fuelFluid();
            if (portFluid == null || fp.fuelMb() <= 0) {
                player.sendSystemMessage(Component.literal("This port is empty")
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            if (fp.fuelMb() < BUCKET_MB) {
                player.sendSystemMessage(Component.literal("Not enough " + fluidName(portFluid)
                        + " in this port to fill a bucket (" + fp.fuelMb() + " mB)")
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            Item bucketItem = portFluid.getBucket();
            if (bucketItem == null || bucketItem == Items.AIR) {
                player.sendSystemMessage(Component.literal(
                        fluidName(portFluid) + " has no bucket form — cannot drain")
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            fp.drainFuel(BUCKET_MB);
            ForgeFuelPortBlockEntity.settleStack(level, pos);
            ForgeFuelPortBlockEntity.refreshConnectivity(level, pos);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack filled = new ItemStack(bucketItem);
                if (!player.getInventory().add(filled)) {
                    player.drop(filled, false);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static String fluidName(Fluid fluid) {
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        return id == null ? "fluid" : id.getPath();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ForgeFuelPortBlockEntity fp)) return InteractionResult.PASS;
        player.sendSystemMessage(Component.literal("Fuel: "
                + fp.stackFuelMb() + " / " + fp.stackCapacityMb() + " mB")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
