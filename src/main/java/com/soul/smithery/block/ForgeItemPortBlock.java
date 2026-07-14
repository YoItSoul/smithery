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
import org.jetbrains.annotations.Nullable;

/**
 * Item input port for the Forge multiblock. Right-clicking with an item pushes one (or
 * more, up to the held stack size) into the connected forge's nearest empty interior
 * slot; hoppers route through the same path via the item capability on the underlying
 * {@link ForgeItemPortBlockEntity}.
 */
public class ForgeItemPortBlock extends Block implements EntityBlock {

    /**
     * Constructs the item port with the given block properties.
     */
    public ForgeItemPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeItemPortBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level.getBlockEntity(pos) instanceof ForgeItemPortBlockEntity port)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (stack.isEmpty()) {
            BlockPos cp = port.controllerPos();
            player.sendSystemMessage(Component.literal("Item port — controller: "
                    + (cp == null ? "<unlinked>" : cp.toShortString()))
                    .withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

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
}
