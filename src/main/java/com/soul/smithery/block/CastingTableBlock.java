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

    /**
     * Drops the casting table's contents at break time. Contents include:
     * <ul>
     *   <li>The finished part item if the table is in {@code READY} state (sitting in the cast
     *       waiting for the player to pick it up).</li>
     *   <li>A {@code casting_sand} item if the table has any sand layer (any state above EMPTY).
     *       Note that mid-pour molten fluid is lost — it spills off the broken table.</li>
     * </ul>
     * The table block item itself is handled by the loot table at
     * {@code data/smithery/loot_table/blocks/casting_table.json}.
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
            // Client-side prediction of the cooling countdown so the renderer's
            // coolingFraction transitions smoothly (server doesn't re-sync intermediate
            // values; without this the molten tint would snap directly to partColor at READY).
            return (lvl, pos, st, be) -> ((CastingTableBlockEntity) be).clientTick();
        }
        // Drives the COOLING → READY countdown server-side. All other state transitions
        // are interaction-driven.
        return (lvl, pos, st, be) -> ((CastingTableBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingTableBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // --- READY → retrieve part. Lives in useItemOn (rather than useWithoutItem)
        //     so it fires even when the player is still holding the brush they just
        //     finished using to advance COVERED → READY. Brush is inert at READY anyway,
        //     so consuming the right-click as a retrieve is the right behavior. ---
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

        // --- Registered template item (vanilla ingot/nugget, modder-added ender pearl, etc) ---
        // CastTemplates is the modder-facing registry: ItemStack → cast-target PartType id.
        net.minecraft.resources.Identifier castTypeId =
                com.soul.smithery.api.cast.CastTemplates.resolve(stack);
        if (castTypeId != null) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be.tryImpressTemplateItem(castTypeId)) return InteractionResult.SUCCESS;
            return InteractionResult.PASS;
        }

        // Brush: intentionally NOT handled here — we return PASS so vanilla
        // BrushItem.useOn fires, puts the player into the long brush-use animation,
        // and our SmitheryEventHandlers.onBrushTick subscription drives the actual
        // brushProgress increments on a 10-tick cadence (matching vanilla).
        return InteractionResult.PASS;
    }

}
