package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.SmitheryTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Tells a player that an ordinary item is a modifier ingredient, on the item itself.
 *
 * <p>Nothing about a redstone dust says "put me on an anvil with a tool to get Haste". In 1.12 that
 * knowledge lived in the Tinkers' book; here the ingredient is any vanilla item, so the tooltip is
 * the only place a player will meet it without going looking. The line names the modifier, what it
 * can go on, and — the part that is otherwise invisible — that the workstation is an
 * <em>anvil</em>, not the forge or the part press.</p>
 *
 * <p>Items that feed a tool modifier and an armour one (a slime ball is Knockback on a sword and
 * Sticky on a chestplate) list both, since {@link ModifierSources} now keeps an entry per target.</p>
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ModifierSourceTooltipHandler {

    private ModifierSourceTooltipHandler() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<ModifierSources.Entry> entries =
                ModifierSources.allEntries().get(event.getItemStack().getItem());
        if (entries == null || entries.isEmpty()) return;

        SmitheryTooltips.Tier tier = SmitheryTooltips.currentTier();
        event.getToolTip().add(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".modifier_source.header")));

        for (ModifierSources.Entry entry : entries) {
            ModifierEffect effect = entry.effect();
            Modifier mod = SmitheryAPI.MODIFIERS.get(effect.modifierId());

            Component name = Component.translatable(
                    PartItem.modifierTranslationKey(effect.modifierId())).withStyle(ChatFormatting.AQUA);
            event.getToolTip().add(SmitheryTooltips.bullet(Component.translatable(
                    "tooltip." + Smithery.MODID + ".modifier_source.applies",
                    name, targetLabel(mod))));

            if (tier == SmitheryTooltips.Tier.BASIC) continue;

            event.getToolTip().add(SmitheryTooltips.subLine(
                    SmitheryTooltips.description(PartItem.modifierDescription(effect.modifierId()))));

            // Worth spelling out only when one item is not one unit — the block forms are the
            // reason this field exists (a redstone block is nine dusts toward the same level).
            if (entry.unitValue() > 1) {
                event.getToolTip().add(SmitheryTooltips.subLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".modifier_source.units", entry.unitValue())));
            }
            if (mod != null && mod.maxLevel() > 1) {
                event.getToolTip().add(SmitheryTooltips.subLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".modifier_source.max_level", mod.maxLevel())));
            }
        }
    }

    /** "tools", "armor" or "tools & armor", from the modifier's own declaration. */
    private static Component targetLabel(Modifier mod) {
        String key = "tooltip." + Smithery.MODID + ".modifier_source.target.";
        if (mod == null) return Component.translatable(key + "both");
        return switch (mod.appliesTo()) {
            case TOOLS -> Component.translatable(key + "tools");
            case ARMOR -> Component.translatable(key + "armor");
            case BOTH -> Component.translatable(key + "both");
        };
    }
}
