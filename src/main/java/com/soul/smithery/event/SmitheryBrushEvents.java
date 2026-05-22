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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Game-bus event handlers that adapt vanilla's brush-use mechanic to the Casting Table.
 *
 * Background: vanilla {@code BrushItem.useOn} puts the player into the long brush-use
 * animation regardless of target block, then {@code onUseTick} per-tick checks
 * {@code instanceof BrushableBlockEntity} before advancing the brush state. Since our
 * Casting Table BE is NOT a {@link net.minecraft.world.level.block.entity.BrushableBlockEntity}
 * (its constructor hardcodes its own BE type, so we can't simply extend it), the
 * vanilla per-tick logic skips us — we'd get the use animation but no brushing.
 *
 * This handler closes that gap: on every server-side tick where the player is using
 * a brush, we raycast their look direction and — if it lands on a CastingTableBlockEntity —
 * advance our state-machine on the same 10-tick cadence vanilla uses. The result is
 * a brushing experience visually identical to suspicious-sand.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryBrushEvents {

    /** Vanilla BrushItem.getUseDuration() returns 200 ticks. */
    private static final int BRUSH_USE_DURATION_TICKS = 200;
    /** Vanilla BrushItem advances brush state every 10 ticks (1 stroke = ½ second). */
    private static final int BRUSH_ADVANCE_INTERVAL_TICKS = 10;

    @SubscribeEvent
    public static void onBrushTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getItem().is(Items.BRUSH)) return;
        // Authoritative state lives on the server — particles/animation are vanilla
        // BrushItem's job and they're already running because useOn returned CONSUME.
        if (player.level().isClientSide()) return;

        // Only advance on the exact tick boundaries vanilla brush uses. Skip the
        // very first tick of use (elapsed == 0) so right-clicking once doesn't
        // immediately mutate state — matches "press and hold" feel.
        int elapsed = BRUSH_USE_DURATION_TICKS - event.getDuration();
        if (elapsed <= 0 || elapsed % BRUSH_ADVANCE_INTERVAL_TICKS != 0) return;

        // Same raycast vanilla BrushItem uses to find the targeted block.
        HitResult hr = ProjectileUtil.getHitResultOnViewVector(
                player, EntitySelector.CAN_BE_PICKED, player.blockInteractionRange());
        if (!(hr instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return;

        BlockEntity be = player.level().getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof CastingTableBlockEntity ctbe)) return;
        ctbe.tryBrush();
    }

    private SmitheryBrushEvents() {}
}
