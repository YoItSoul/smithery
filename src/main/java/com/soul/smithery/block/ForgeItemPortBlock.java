package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeItemPortBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Item input port for the Forge multiblock. Right-click with an item to push one (or more,
 * if held stack size allows) into the connected forge's nearest empty interior slot. Hoppers
 * feeding the block via the item capability follow the same path. Insertion is gated by the
 * controller having at least one empty interior slot — overfilled forges reject inserts
 * without consuming the source item.
 *
 * <p>Bare-hand right-click prints a short status (linked controller pos + empty slot count)
 * for debugging.
 */
public class ForgeItemPortBlock extends Block implements EntityBlock {

    public ForgeItemPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeItemPortBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ForgeItemPortBlockEntity port)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        int wanted = stack.getCount();
        int inserted = port.tryInsert(stack, wanted);
        if (inserted <= 0) {
            player.sendSystemMessage(Component.literal("Forge has no empty space")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(inserted);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ForgeItemPortBlockEntity port)) return InteractionResult.PASS;
        BlockPos cp = port.controllerPos();
        player.sendSystemMessage(Component.literal("Item port — controller: "
                + (cp == null ? "<unlinked>" : cp.toShortString()))
                .withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }
}
