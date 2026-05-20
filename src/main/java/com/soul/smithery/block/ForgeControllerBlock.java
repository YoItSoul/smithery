package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.network.ForgeLeakDebugPayload;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

        ForgeControllerBlockEntity.ValidationResult result = fc.validateStructure();
        Component message;
        if (result.valid) {
            int holeCount = result.holes();
            String heatState = fc.isFueled()
                    ? (fc.temperatureC() < fc.targetTemperatureC() ? "heating" : "at temp")
                    : (fc.temperatureC() > 25f ? "cooling" : "cold");
            String base = String.format(
                    "Forge valid · %d buckets · %s · %.0f°C → %.0f°C (%s) · fuel %d/%d mB · fluid %d/%d mB",
                    result.capacityBuckets(),
                    result.openTop ? "open top" : "closed top",
                    fc.temperatureC(),
                    fc.targetTemperatureC(),
                    heatState,
                    fc.totalFuelMb(),
                    fc.totalFuelCapacityMb(),
                    fc.totalStoredFluidMb(),
                    fc.fluidCapacityMb());
            String holesText = holeCount > 0 ? " · " + holeCount + " holes" : "";
            message = Component.literal(base + holesText)
                    .withStyle(holeCount > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
            // Detailed per-fluid breakdown on a second line if anything is stored.
            if (fc.totalStoredFluidMb() > 0) {
                StringBuilder breakdown = new StringBuilder("  fluids:");
                fc.fluidStorageView().forEach((id, mb) ->
                        breakdown.append(' ').append(id.getPath()).append('=').append(mb).append("mB"));
                player.sendSystemMessage(Component.literal(breakdown.toString()).withStyle(ChatFormatting.GRAY));
            }
        } else {
            String invalidBase = "Forge invalid: " + result.reason;
            int locked = fc.totalStoredFluidMb();
            if (locked > 0) invalidBase += " · " + locked + " mB locked in controller";
            message = Component.literal(invalidBase).withStyle(ChatFormatting.RED);
        }
        player.sendSystemMessage(message);

        // Debug visualization: flash red wireframe boxes around any leak positions so the
        // player can locate missing wall blocks at a glance. 3 seconds at 20 tps = 60 ticks.
        if (player instanceof ServerPlayer sp && !result.holePositions.isEmpty()) {
            PacketDistributor.sendToPlayer(sp,
                    new ForgeLeakDebugPayload(new ArrayList<>(result.holePositions), 60));
        }
        return InteractionResult.SUCCESS;
    }
}
