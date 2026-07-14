package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.content.SmitheryModifiers;
import com.soul.smithery.registry.SmitheryAttachments;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps soulbound-modified smithery gear through death. On player death, stacks carrying the
 * {@code smithery:soulbound} modifier are pulled out of the drop list into a {@code copyOnDeath}
 * attachment ({@link SmitheryAttachments#SOULBOUND_STASH}); when the respawned player joins the
 * level, the stash pays back into their inventory (or drops at their feet if full).
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class SoulboundHandler {
    private SoulboundHandler() {}

    /** Pulls soulbound stacks out of a dying player's drops into the death-surviving stash. */
    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        List<ItemStack> stashed = null;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (!isSoulbound(stack)) continue;
            if (stashed == null) stashed = new ArrayList<>(player.getData(SmitheryAttachments.SOULBOUND_STASH));
            stashed.add(stack.copy());
            it.remove();
        }
        if (stashed != null) {
            player.setData(SmitheryAttachments.SOULBOUND_STASH, List.copyOf(stashed));
        }
    }

    /** Returns stashed soulbound items to the respawned player. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        List<ItemStack> stashed = player.getData(SmitheryAttachments.SOULBOUND_STASH);
        if (stashed.isEmpty()) return;
        player.setData(SmitheryAttachments.SOULBOUND_STASH, List.of());
        for (ItemStack stack : stashed) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static boolean isSoulbound(ItemStack stack) {
        for (ModifierEffect e : stack.getOrDefault(
                SmitheryDataComponents.APPLIED_MODIFIERS.get(), List.<ModifierEffect>of())) {
            if (e.modifierId().equals(SmitheryModifiers.SOULBOUND)) return true;
        }
        return false;
    }
}
