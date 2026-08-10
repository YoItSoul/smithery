package com.soul.smithery.compat.jade;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import snownee.jade.api.ITooltip;

/**
 * Tooltip lines the Casting Table and Casting Basin both show, so the two providers stay worded and
 * formatted the same.
 *
 * <p>Nothing here names the metal or reports the pour level: both vessels expose a Forge fluid
 * handler whose tank is the mould itself, so Jade's own fluid bar already draws the fluid and its
 * amount against capacity. These lines cover only what that bar cannot say.
 */
final class CastingJadeLines {

    private CastingJadeLines() {}

    /** Builds one grey tooltip line from a {@code jade.smithery.} key. */
    static Component line(String key, Object... args) {
        return Component.translatable("jade." + Smithery.MODID + "." + key, args)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Names the shape impressed in the sand, e.g. "Cast: Pick Head". */
    static void appendCast(ITooltip tooltip, ResourceLocation partTypeId) {
        tooltip.add(line("cast", Component.translatable(PartItem.partTranslationKey(partTypeId))));
    }

    /**
     * Cooling progress as a percentage completed.
     *
     * @param coolingFraction the block entity's remaining-time fraction, which counts down from 1
     */
    static void appendCooling(ITooltip tooltip, float coolingFraction) {
        int percent = Math.round((1.0f - coolingFraction) * 100f);
        tooltip.add(line("cooling", Math.max(0, Math.min(100, percent))));
    }

    /** Names what is waiting to be taken out. Silent when the cast no longer resolves to an item. */
    static void appendReady(ITooltip tooltip, ItemStack result) {
        if (result.isEmpty()) return;
        tooltip.add(line("ready", result.getHoverName()));
    }
}
