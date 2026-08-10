package com.soul.smithery.block;

import com.soul.smithery.block.entity.CastingBasinBlockEntity;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
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
 * Block-scale casting vessel. Hosts a {@link CastingBasinBlockEntity} that drives the
 * EMPTY -&gt; FILLING -&gt; COOLING -&gt; READY cycle; this class wires the retrieval click into that
 * state machine and supplies the open-topped voxel shape.
 *
 * <p>Unlike the Casting Table there is no sand or template step — a basin has one shape, so its only
 * interaction is taking the finished block back out.
 */
public class CastingBasinBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box( 0, 0,  0,  2,  2,  2),
            Block.box( 0, 0, 14,  2,  2, 16),
            Block.box(14, 0,  0, 16,  2,  2),
            Block.box(14, 0, 14, 16,  2, 16),
            Block.box( 1, 2,  1, 15,  3, 15),
            Block.box( 0, 2,  0,  1, 16, 16),
            Block.box(15, 2,  0, 16, 16, 16),
            Block.box( 1, 2,  0, 15, 16,  1),
            Block.box( 1, 2, 15, 15, 16, 16)
    );

    /**
     * Constructs the casting basin with the given block properties.
     */
    public CastingBasinBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingBasinBlockEntity(pos, state);
    }

    /**
     * Drops a finished cast at break time so a READY block is never lost to an impatient pickaxe.
     * A cast still filling or cooling is molten metal, not a block yet, and is lost with the basin.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof CastingBasinBlockEntity be
                && be.state() == CastingBasinBlockEntity.State.READY) {
            ItemStack cast = be.peekBlockItem();
            if (!cast.isEmpty()) {
                popResource(level, pos, cast);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SmitheryBlockEntities.CASTING_BASIN.get()) return null;
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> ((CastingBasinBlockEntity) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((CastingBasinBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingBasinBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (be.state() != CastingBasinBlockEntity.State.READY) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack result = be.tryRetrieveBlock();
        if (!result.isEmpty()) {
            if (!player.getInventory().add(result)) player.drop(result, false);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }
}
