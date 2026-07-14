package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.network.ForgeLeakDebugPayload;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

/**
 * Brain block of the Forge multiblock. Exactly one is required per valid structure;
 * the attached {@link ForgeControllerBlockEntity} stores temperature, fluid contents,
 * validation state, and runs the per-tick simulation. Right-clicking opens the
 * controller menu and ships any pending leak-debug visualization to the player.
 */
public class ForgeControllerBlock extends Block implements EntityBlock {
    /**
     * Constructs the forge controller with the given block properties.
     */
    public ForgeControllerBlock(Properties properties) {
        super(properties);
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ForgeControllerBlockEntity fc) {
            fc.validateStructure();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ForgeControllerBlockEntity fc)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        ForgeControllerBlockEntity.ValidationResult result = fc.lastValidation();
        if (!result.holePositions.isEmpty()) {
            PacketDistributor.sendToPlayer(sp,
                    new ForgeLeakDebugPayload(new ArrayList<>(result.holePositions), 60));
        }

        final int forgeSlotCount = fc.slots().size();
        sp.openMenu(fc, buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(forgeSlotCount);
        });
        return InteractionResult.SUCCESS;
    }
}
