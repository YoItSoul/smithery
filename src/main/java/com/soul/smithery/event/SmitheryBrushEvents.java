package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Adapts vanilla's brush-use ticking to {@link CastingTableBlockEntity} so the casting table
 * accepts brushing the same way suspicious sand does.
 *
 * <p>Vanilla {@code BrushItem.onUseTick} only advances brush state when the targeted BE is a
 * {@code BrushableBlockEntity}; smithery's casting table is its own BE type and would otherwise
 * see the animation without state changes. This handler raycasts on the matching 10-tick cadence
 * and calls {@code tryBrush} when the player is aiming at a casting table.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryBrushEvents {

    private static final int BRUSH_USE_DURATION_TICKS = 200;
    private static final int BRUSH_ADVANCE_INTERVAL_TICKS = 10;

    /**
     * Server-side tick handler that advances casting-table brush state on the same cadence
     * vanilla brushing uses.
     *
     * @param event NeoForge's per-tick item-use event
     */
    @SubscribeEvent
    public static void onBrushTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getItem().is(Items.BRUSH)) return;
        if (player.level().isClientSide()) return;

        int elapsed = BRUSH_USE_DURATION_TICKS - event.getDuration();
        if (elapsed <= 0 || elapsed % BRUSH_ADVANCE_INTERVAL_TICKS != 0) return;

        HitResult hr = ProjectileUtil.getHitResultOnViewVector(
                player, EntitySelector.ENTITY_STILL_ALIVE, player.getBlockReach());
        if (!(hr instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return;

        BlockEntity be = player.level().getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof CastingTableBlockEntity ctbe)) return;
        ctbe.tryBrush();
    }

    private SmitheryBrushEvents() {}
}
