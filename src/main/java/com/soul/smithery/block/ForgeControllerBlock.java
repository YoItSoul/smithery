package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.network.ForgeLeakDebugPayload;
import com.soul.smithery.network.SmitheryPayloads;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Brain block of the Forge multiblock. Exactly one is required per valid structure;
 * the attached {@link ForgeControllerBlockEntity} stores temperature, fluid contents,
 * validation state, and runs the per-tick simulation. Right-clicking opens the
 * controller menu and ships any pending leak-debug visualization to the player.
 */
public class ForgeControllerBlock extends Block implements EntityBlock {
    /**
     * What the controller face is showing. Purely cosmetic; driven from
     * {@link ForgeControllerBlockEntity#serverTick}, never set by the player.
     */
    public static final EnumProperty<ForgeControllerStatus> STATUS =
            EnumProperty.create("status", ForgeControllerStatus.class);

    /**
     * Constructs the forge controller with the given block properties.
     */
    public ForgeControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STATUS, ForgeControllerStatus.IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeControllerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != SmitheryBlockEntities.FORGE_CONTROLLER.get()) return null;
        return (lvl, pos, st, be) -> ((ForgeControllerBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ForgeControllerBlockEntity fc) {
            fc.validateStructure();
        }
    }

    /**
     * Returns the forge's contents when the controller itself is removed.
     *
     * <p>Interior items live only in the controller's block entity, and
     * {@link ForgeControllerBlockEntity#invalidate} — which exists so a broken forge gives
     * them back — only runs while that block entity is still alive. Breaking a wall brick
     * therefore returns your items, but breaking the controller, the natural way to dismantle
     * or move a forge, would eat them. Covers explosions and {@code Level#destroyBlock} too,
     * which {@code playerWillDestroy} would miss.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ForgeControllerBlockEntity fc) {
            List<BlockPos> positions = fc.slotPositions();
            for (int i = 0; i < fc.slots().size(); i++) {
                ItemStack stack = fc.slots().get(i);
                if (stack.isEmpty()) continue;
                // Drop at the cell the item occupied when there is one, else at the controller.
                BlockPos at = i < positions.size() ? positions.get(i) : pos;
                Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ForgeControllerBlockEntity fc)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        ForgeControllerBlockEntity.ValidationResult result = fc.lastValidation();
        if (!result.holePositions.isEmpty()) {
            SmitheryPayloads.sendToPlayer(sp,
                    new ForgeLeakDebugPayload(new ArrayList<>(result.holePositions), 60));
        }

        final int forgeSlotCount = fc.slots().size();
        NetworkHooks.openScreen(sp, fc, buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(forgeSlotCount);
        });
        return InteractionResult.SUCCESS;
    }
}
