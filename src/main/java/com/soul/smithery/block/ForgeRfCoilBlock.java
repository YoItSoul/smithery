package com.soul.smithery.block;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.ForgeRfCoilBlockEntity;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Electric heat source for the Forge. Counts as a shell block, and at most one may sit in
 * any single forge.
 *
 * <p>Target temperature is set by hand on the block — right-click raises it, sneak-click
 * lowers it — rather than through a screen. The coil has exactly one setting, so a whole
 * menu for it would be more ceremony than the thing deserves.
 */
public class ForgeRfCoilBlock extends Block implements EntityBlock {

    /** True while the coil is drawing power and driving the forge. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /**
     * Constructs the coil with the given block properties.
     */
    public ForgeRfCoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeRfCoilBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ForgeRfCoilBlockEntity coil)) {
            return InteractionResult.PASS;
        }
        int target = coil.adjustTarget(player.isShiftKeyDown() ? -1 : 1);
        int draw = ForgeRfCoilBlockEntity.drawAt(target, 0);
        player.displayClientMessage(Component.translatable(
                "message." + Smithery.MODID + ".rf_coil.target", target, draw)
                .withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }
}
