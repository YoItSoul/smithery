package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.block.entity.ForgeDrainBlockEntity;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Molten fluid output port for the Forge. Right-click with an empty bucket drains a
 * bucket of the controller's currently-selected output fluid; the underlying
 * {@link ForgeDrainBlockEntity} also pumps fluid through any attached pipe network
 * each tick and exposes a passthrough fluid capability for external mods.
 */
public class ForgeDrainBlock extends Block implements EntityBlock {
    /**
     * Constructs the forge drain with the given block properties.
     */
    public ForgeDrainBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeDrainBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != SmitheryBlockEntities.FORGE_DRAIN.get()) return null;
        return (lvl, pos, st, be) -> ((ForgeDrainBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    private static final int BUCKET_MB = 1000;

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.BUCKET)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ForgeDrainBlockEntity drain)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos ctrlPos = drain.controllerPos();
        ForgeControllerBlockEntity controller = ctrlPos == null ? null
                : (level.getBlockEntity(ctrlPos) instanceof ForgeControllerBlockEntity c ? c : null);
        if (controller == null) {
            player.sendSystemMessage(Component.literal("Drain isn't linked to a valid forge controller")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }

        ResourceLocation outputFluidId = controller.outputFluidId();
        if (outputFluidId == null) {
            player.sendSystemMessage(Component.literal("No output fluid selected — pick one in the controller GUI")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(outputFluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            player.sendSystemMessage(Component.literal("Selected output fluid isn't registered")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        int available = controller.outputFluidMb();
        if (available < BUCKET_MB) {
            player.sendSystemMessage(Component.literal("Not enough " + fluidName(outputFluidId)
                    + " to fill a bucket (" + available + " mB)").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        Item bucketItem = fluid.getBucket();
        if (bucketItem == null || bucketItem == Items.AIR) {
            player.sendSystemMessage(Component.literal(fluidName(outputFluidId)
                    + " has no bucket form — cannot drain").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }

        int drained = controller.drainFluid(fluid, BUCKET_MB);
        if (drained < BUCKET_MB) {
            return InteractionResult.SUCCESS;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack filled = new ItemStack(bucketItem);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static String fluidName(ResourceLocation id) {
        return id == null ? "fluid" : id.getPath();
    }
}
