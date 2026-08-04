package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.stage.SmitheryStages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Enforces {@link SmitheryStages} gates on the player holding the item.
 *
 * <p>A locked tool or part is inert rather than confiscated: it can be held, moved
 * and stored, but will not mine, attack or activate. That mirrors how ItemStages
 * treats gated items in the packs this feature exists for, and it keeps a tool the
 * player legitimately obtained — from a quest reward or a dungeon chest — from being
 * destroyed just because its material outranks their progress.</p>
 *
 * <p>Everything routes through the player-facing events rather than
 * {@code Item} overrides, because the mining and attack hooks on {@code Item}
 * ({@code isCorrectToolForDrops}, {@code getDestroySpeed}) receive no player and so
 * cannot answer a per-player question.</p>
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class StageGateHandler {

    /** Throttles the "you can't use this" message so held-down clicks do not spam chat. */
    private static final WeakHashMap<UUID, Long> LAST_MESSAGE = new WeakHashMap<>();
    private static final long MESSAGE_COOLDOWN_TICKS = 40L;

    private StageGateHandler() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (deny(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (deny(event.getEntity(), event.getItemStack())) {
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (deny(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (deny(event.getEntity(), event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && deny(player, event.getItem())) {
            event.setCanceled(true);
        }
    }

    /**
     * Adds the required-stage line to a locked item's tooltip, so the reason it does
     * nothing is legible in the inventory instead of only on a failed swing.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null || SmitheryStages.isEmpty()) {
            return;
        }
        Set<String> missing = SmitheryStages.missingStages(player, event.getItemStack());
        if (missing.isEmpty()) {
            return;
        }
        event.getToolTip().add(Component.translatable(
                "tooltip.smithery.stage_locked", String.join(", ", missing))
                .withStyle(ChatFormatting.DARK_RED));
    }

    /** True when the stack is locked for this player; also emits the throttled notice. */
    private static boolean deny(Player player, ItemStack stack) {
        if (player == null || player.level().isClientSide || !SmitheryStages.isLocked(player, stack)) {
            return false;
        }
        long now = player.level().getGameTime();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last == null || now - last >= MESSAGE_COOLDOWN_TICKS) {
            LAST_MESSAGE.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable(
                    "message.smithery.stage_locked", stack.getHoverName())
                    .withStyle(ChatFormatting.RED), true);
        }
        return true;
    }
}
