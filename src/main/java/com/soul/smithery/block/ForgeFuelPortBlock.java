package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Lava input/storage port for the Forge multiblock. Right-click with a lava bucket to add
 * 1000 mB (the bucket comes back empty). Right-click with an empty bucket to drain 1000 mB
 * (you get a lava bucket back). Capacity per port: {@link ForgeFuelPortBlockEntity#CAPACITY_MB}.
 */
public class ForgeFuelPortBlock extends Block implements EntityBlock {
    private static final int BUCKET_MB = 1000;

    public ForgeFuelPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeFuelPortBlockEntity(pos, state);
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
