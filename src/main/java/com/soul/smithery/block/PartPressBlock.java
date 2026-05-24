package com.soul.smithery.block;

import com.soul.smithery.block.entity.PartPressBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Part Press — in-world part cutting workbench. Redstone-driven: powered = closed (cutting),
 * unpowered = open (player or hopper can swap input/output). Right-click empty hand cycles
 * the selected part type; right-click with an item inserts it as the press's input when open.
 *
 * <p>All state machine logic + slot semantics live on {@link PartPressBlockEntity}. This block
 * class only handles redstone wiring + player interactions and routes them into the BE.
 */
public class PartPressBlock extends Block implements EntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PartPressBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartPressBlockEntity(pos, state);
    }

    /**
     * INVISIBLE so vanilla doesn't render the empty {@code block/part_press} model on top
     * of (or instead of) the Geckolib BER output. The BER is the sole visual source.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PartPressBlockEntity pp)) return InteractionResult.PASS;
        // Shift + bare hand → cycle template.
        if (player.isShiftKeyDown()) {
            if (state.getValue(POWERED)) return InteractionResult.PASS;
            return cycleAndAnnounce(pp, player);
        }
        // Plain bare hand → take finished part out first; otherwise pull the un-pressed input
        // back to the player. Output has priority so a player who left a stack mid-cycle can
        // still grab their cut part. Both are only available while open.
        if (!pp.outputItem().isEmpty()) {
            ItemStack taken = pp.takeOutput();
            if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(POWERED)) return InteractionResult.PASS;
        if (!pp.inputItem().isEmpty()) {
            ItemStack taken = pp.takeInput();
            if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PartPressBlockEntity pp)) return InteractionResult.PASS;
        // Shift + item-in-hand → cycle template (the held item is irrelevant in this gesture).
        if (player.isShiftKeyDown()) {
            if (state.getValue(POWERED)) return InteractionResult.PASS;
            return cycleAndAnnounce(pp, player);
        }
        // Closed press is locked.
        if (state.getValue(POWERED)) return InteractionResult.PASS;
        // Held item is a valid raw material. Two preconditions:
        //   - Output slot must be empty (press isn't ready until the previous part is claimed).
        //   - If the input slot is non-empty, swap: return the existing item first, then insert.
        // Lets the player change their mind without breaking the press, but doesn't let them
        // queue new material on top of an unclaimed result.
        if (PartPressBlockEntity.resolveMaterialFor(stack) != null && pp.outputItem().isEmpty()) {
            if (!pp.inputItem().isEmpty()) {
                ItemStack taken = pp.takeInput();
                if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
            }
            int inserted = pp.insertOne(stack);
            if (inserted > 0 && !player.getAbilities().instabuild) {
                stack.shrink(inserted);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private static InteractionResult cycleAndAnnounce(PartPressBlockEntity pp, Player player) {
        pp.cycleSelectedPart();
        var pt = pp.selectedPartType();
        if (pt != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Action-bar overlay (the same slot Minecraft uses for jukebox / hotbar-swap labels).
            // ServerPlayer.connection.send pushes a SetActionBarTextPacket directly — avoids the
            // chat spam from sendSystemMessage.
            net.minecraft.network.chat.Component label = net.minecraft.network.chat.Component
                    .translatable("smithery.part." + pt.id().getNamespace() + "." + pt.id().getPath());
            sp.connection.send(new net.minecraft.network.protocol.game
                    .ClientboundSetActionBarTextPacket(label));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        boolean shouldBePowered = level.hasNeighborSignal(pos);
        if (shouldBePowered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);
            if (level.getBlockEntity(pos) instanceof PartPressBlockEntity pp) {
                pp.onPowerChanged(shouldBePowered);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide()) return;
        // Sync power state at placement — placing a press already adjacent to a redstone source
        // should start in the closed pose, not get stuck open until the next neighbor update.
        boolean shouldBePowered = level.hasNeighborSignal(pos);
        if (shouldBePowered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);
        }
    }
}
