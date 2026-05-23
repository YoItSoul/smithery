package com.soul.smithery.block;

import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import org.jspecify.annotations.Nullable;

/**
 * Sand-casting workbench. Steps 1–2: block + BE skeleton + state-machine interactions.
 *
 * Interactions handled here (more land in Steps 4+):
 *   - casting_sand item on EMPTY table → consumes 1, advances to SAND
 *   - PartItem on SAND table → advances to IMPRESSED (template NOT consumed)
 *
 * The 4-legs-and-slab voxel shape matches the Blockbench model so the outline/hitbox
 * reads as a table and arrows fly between the legs.
 */
public class CastingTableBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box( 0, 0,  0,  4, 11,  4),
            Block.box(12, 0,  0, 16, 11,  4),
            Block.box( 0, 0, 12,  4, 11, 16),
            Block.box(12, 0, 12, 16, 11, 16),
            Block.box( 0, 11, 0, 16, 16, 16)
    );

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

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != SmitheryBlockEntities.CASTING_TABLE.get()) return null;
        // Drives the COOLING → COVERED countdown. All other state transitions are interaction-driven.
        return (lvl, pos, st, be) -> ((CastingTableBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingTableBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // --- Casting sand: EMPTY → SAND, consume 1 ---
        if (stack.is(SmitheryBlocks.CASTING_SAND_ITEM.get())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be.tryFillSand()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // --- Part item template: SAND → IMPRESSED, template NOT consumed ---
        if (stack.getItem() instanceof PartItem) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be.tryImpressPart(stack)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // Brush: intentionally NOT handled here — we return PASS so vanilla
        // BrushItem.useOn fires, puts the player into the long brush-use animation,
        // and our SmitheryEventHandlers.onBrushTick subscription drives the actual
        // brushProgress increments on a 10-tick cadence (matching vanilla).
        return InteractionResult.PASS;
    }
}
