package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.content.SmitheryModifiers;
import com.soul.smithery.item.tool.SmitheryToolData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps soulbound-modified smithery gear through death. On player death, stacks carrying the
 * {@code smithery:soulbound} modifier are pulled out of the drop list into the player's
 * {@link SoulboundStash} capability; the stash is copied across the death clone and pays back
 * into the respawned player's inventory (or drops at their feet if full) when they join the
 * level.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class SoulboundHandler {
    private SoulboundHandler() {}

    /** Pulls soulbound stacks out of a dying player's drops into the death-surviving stash. */
    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SoulboundStash stash = SoulboundStash.get(player).orElse(null);
        if (stash == null) return;

        List<ItemStack> stashed = null;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (!isSoulbound(stack)) continue;
            if (stashed == null) stashed = new ArrayList<>(stash.items());
            stashed.add(stack.copy());
            it.remove();
        }
        if (stashed != null) {
            stash.setItems(stashed);
        }
    }

    /**
     * Copies the stash from the dead player onto the respawn clone — 1.20.1 capabilities do
     * not survive death on their own.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        event.getOriginal().reviveCaps();
        SoulboundStash.get(event.getOriginal()).ifPresent(original ->
                SoulboundStash.get(event.getEntity()).ifPresent(clone ->
                        clone.copyFrom(original)));
        event.getOriginal().invalidateCaps();
    }

    /** Returns stashed soulbound items to the respawned player. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SoulboundStash stash = SoulboundStash.get(player).orElse(null);
        if (stash == null || stash.items().isEmpty()) return;
        List<ItemStack> stashed = stash.items();
        stash.setItems(List.of());
        for (ItemStack stack : stashed) {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }
    }

    private static final ResourceLocation SOA_SOULBOUND =
            ResourceLocation.fromNamespaceAndPath("soa_additions", "soulbound");
    private static final ResourceLocation SOA_SOULBOUND_ARMOR =
            ResourceLocation.fromNamespaceAndPath("soa_additions", "soulbound_armor");

    private static boolean isSoulbound(ItemStack stack) {
        for (ModifierEffect e : SmitheryToolData.getAppliedModifiers(stack)) {
            ResourceLocation id = e.modifierId();
            if (id.equals(SmitheryModifiers.SOULBOUND)
                    || id.equals(SOA_SOULBOUND)
                    || id.equals(SOA_SOULBOUND_ARMOR)) return true;
        }
        return false;
    }
}
