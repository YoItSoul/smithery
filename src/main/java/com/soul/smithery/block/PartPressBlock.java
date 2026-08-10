package com.soul.smithery.block;

import com.soul.smithery.block.entity.PartPressBlockEntity;
import com.soul.smithery.client.PartPressScreenOpener;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * In-world part cutting workbench. Redstone-driven: powered means closed (cutting),
 * unpowered means open (player or hopper can swap input/output). Right-click with an
 * empty hand opens the shape picker; right-click with a held item inserts it
 * as the press's input while open. All slot semantics and the cut state machine live
 * on {@link PartPressBlockEntity}.
 */
public class PartPressBlock extends Block implements EntityBlock {
    /** Redstone power blockstate property: true = closed pose, false = open pose. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /**
     * Constructs the part press with the given block properties; defaults to unpowered.
     */
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

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);

        // Shape selection opens a picker rather than cycling blind through a dozen part types.
        // Handled before the client bail-out because the screen is client-only: the server has
        // nothing to do here and hears back only if the player actually picks something.
        if (player.isShiftKeyDown()) {
            if (state.getValue(POWERED)) return InteractionResult.PASS;
            if (level.isClientSide()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PartPressScreenOpener.open(pos));
            }
            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PartPressBlockEntity pp)) return InteractionResult.PASS;

        // Held pressable material: swap it into the input slot while open.
        if (!stack.isEmpty() && !state.getValue(POWERED)
                && PartPressBlockEntity.resolveMaterialFor(stack) != null && pp.outputItem().isEmpty()) {
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

        // Otherwise behave like an empty-hand interaction: pop output first, then input.
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos fromPos, boolean movedByPiston) {
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
        boolean shouldBePowered = level.hasNeighborSignal(pos);
        if (shouldBePowered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);
        }
    }
}
