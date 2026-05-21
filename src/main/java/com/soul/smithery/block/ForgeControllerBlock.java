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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

/**
 * The brain of the Forge multiblock. Exactly one is required per valid structure; the
 * BlockEntity attached to it stores temperature, fluid contents, and validation state,
 * and runs the per-tick logic.
 *
 * Right-clicking with an empty hand prints a one-line validation readout to chat — this is
 * our pre-GUI test affordance and will be replaced by an actual menu once the simulation
 * layer is in.
 */
public class ForgeControllerBlock extends Block implements EntityBlock {
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

        // Debug leak visualization: flash red wireframe boxes around hole positions.
        ForgeControllerBlockEntity.ValidationResult result = fc.lastValidation();
        if (!result.holePositions.isEmpty()) {
            PacketDistributor.sendToPlayer(sp,
                    new ForgeLeakDebugPayload(new ArrayList<>(result.holePositions), 60));
        }

        // Capture the slot count NOW (on the server side) and ship it to the client
        // so the client allocates the same number of forge slots in its menu copy.
        // Reading be.slots().size() on the client is unreliable — the BE may not have
        // received its update packet yet, so it would default to 0 and mismatch the
        // server's ClientboundContainerSetContentPacket.
        final int forgeSlotCount = fc.slots().size();
        sp.openMenu(fc, buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(forgeSlotCount);
        });
        return InteractionResult.SUCCESS;
    }
}
