package com.soul.smithery.block;

import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Sand-casting workbench block. Hosts a {@link CastingTableBlockEntity} that drives the
 * full EMPTY -> SAND -> IMPRESSED -> FILLING -> COOLING -> READY state machine; this
 * class wires player interactions (sand placement, template impression, retrieval) into
 * those state transitions and supplies the four-legs-and-slab voxel shape.
 */
public class CastingTableBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box( 0, 0,  0,  4, 11,  4),
            Block.box(12, 0,  0, 16, 11,  4),
            Block.box( 0, 0, 12,  4, 11, 16),
            Block.box(12, 0, 12, 16, 11, 16),
            Block.box( 0, 11, 0, 16, 16, 16)
    );

    /**
     * Constructs the casting table with the given block properties.
     */
    public CastingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingTableBlockEntity(pos, state);
    }

    /**
     * Drops the casting table's contents at break time. A finished part in the READY
     * state is popped, and any non-EMPTY table also pops a {@code casting_sand} item;
     * any mid-pour molten fluid is lost.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof CastingTableBlockEntity be) {
            if (be.state() == CastingTableBlockEntity.State.READY) {
                ItemStack part = be.peekPartItem();
                if (!part.isEmpty()) {
                    popResource(level, pos, part.copy());
                }
            }
            if (be.state() != CastingTableBlockEntity.State.EMPTY) {
                popResource(level, pos, new ItemStack(SmitheryBlocks.CASTING_SAND_ITEM.get()));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SmitheryBlockEntities.CASTING_TABLE.get()) return null;
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> ((CastingTableBlockEntity) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((CastingTableBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingTableBlockEntity be)) {
            return InteractionResult.PASS;
        }

        if (be.state() == CastingTableBlockEntity.State.READY) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack result = be.tryRetrievePart();
            if (result.isEmpty()) {
                player.sendSystemMessage(Component.literal("Cast discarded — no matching part for the poured material")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                if (!player.getInventory().add(result)) player.drop(result, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(SmitheryBlocks.CASTING_SAND_ITEM.get())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be.tryFillSand()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (stack.getItem() instanceof PartItem) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be.tryImpressPart(stack)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        net.minecraft.resources.ResourceLocation castTypeId =
                com.soul.smithery.api.cast.CastTemplates.resolve(stack);
        if (castTypeId != null) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be.tryImpressTemplateItem(castTypeId)) return InteractionResult.SUCCESS;
            return InteractionResult.PASS;
        }

        return InteractionResult.PASS;
    }

}
