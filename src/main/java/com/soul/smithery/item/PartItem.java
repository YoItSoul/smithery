package com.soul.smithery.item;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A tool part: one Material shaped as one PartType. Auto-registered for every
 * (Material x PartType) combination. The model JSON is served dynamically by the
 * generated pack, and {@code RegisterColorHandlersEvent.Item} tints layer 0 at render
 * time using the material's part colour.
 */
public class PartItem extends Item {
    private final ResourceLocation materialId;
    private final ResourceLocation partTypeId;

    /**
     * Constructs a part item for the given (material, part type) pair.
     */
    public PartItem(Properties properties, ResourceLocation materialId, ResourceLocation partTypeId) {
        super(properties);
        this.materialId = materialId;
        this.partTypeId = partTypeId;
    }

    /** Returns the part's material id. */
    public ResourceLocation materialId() { return materialId; }
    /** Returns the part's PartType id. */
    public ResourceLocation partTypeId() { return partTypeId; }

    /** Resolves the live {@link Material} for this part, or null if the id is unregistered. */
    public Material material() { return SmitheryAPI.MATERIALS.get(materialId); }
    /** Resolves the live {@link PartType} for this part, or null if the id is unregistered. */
    public PartType partType() { return SmitheryAPI.PART_TYPES.get(partTypeId); }

    /**
     * Returns the ARGB tint colour used by the client-side ItemColor for layer 0;
     * defaults to opaque white when the material can't be resolved.
     */
    public int tintColor() {
        Material m = material();
        return m != null ? m.stats().partColor() : 0xFFFFFFFF;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
                "item." + Smithery.MODID + ".part_combo",
                Component.translatable(materialTranslationKey(materialId)),
                Component.translatable(partTranslationKey(partTypeId))
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Consumer<Component> tooltip = lines::add;
        Material mat = material();
        PartType pt = partType();
        if (mat == null || pt == null) {
            super.appendHoverText(stack, level, lines, flag);
            return;
        }

        SmitheryTooltips.Tier tier = SmitheryTooltips.currentTier();

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.part_summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".part.harvest_level", mat.stats().harvestLevel())));
        int slots = mat.stats().modifierSlotsFor(pt);
        if (slots > 0) {
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.modifier_slots", slots)));
        }

        if (tier == SmitheryTooltips.Tier.BASIC) {
            SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, level, lines, flag);
            return;
        }

        // DETAIL and FULL: per-tool-type contributions ----------------------
        boolean anyContributionDrawn = false;
        for (ToolType tt : SmitheryAPI.toolTypesUsingPart(pt)) {
            ToolType.Slot ourSlot = null;
            ToolType.Slot firstAdditive = null;
            for (ToolType.Slot s : tt.slots()) {
                if (firstAdditive == null && s.role() == DurabilityRole.ADDITIVE) firstAdditive = s;
                if (ourSlot == null && s.partType().equals(pt)) ourSlot = s;
            }
            if (ourSlot == null) continue;
            boolean isPrimaryAdditive = firstAdditive != null && firstAdditive.partType().equals(pt);

            if (!anyContributionDrawn) {
                tooltip.accept(SmitheryTooltips.sectionHeader(
                        Component.translatable("tooltip." + Smithery.MODID + ".section.contributions")));
                anyContributionDrawn = true;
            }

            tooltip.accept(SmitheryTooltips.bullet(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.in_tool",
                    Component.translatable(toolTypeTranslationKey(tt.id())))));

            if (ourSlot.role() == DurabilityRole.ADDITIVE) {
                int contribution = Math.round(mat.stats().durabilityPerIngot() * pt.durabilityScalar());
                tooltip.accept(SmitheryTooltips.subLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.durability_add", contribution)));
            } else {
                tooltip.accept(SmitheryTooltips.subLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.durability_mul",
                        String.format("%.2f", mat.stats().binderMultiplier()))));
            }
            if (isPrimaryAdditive) {
                tooltip.accept(SmitheryTooltips.subLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.attack_damage",
                        String.format("%.1f", mat.stats().attackDamage()))));
                tooltip.accept(SmitheryTooltips.subLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.mining_speed",
                        String.format("%.1f", mat.stats().miningSpeed()))));
            }

            for (ModifierEffect effect : mat.stats().modifiersFor(tt)) {
                int effectLevel = effect.paramInt("level", 1);
                MutableComponent line = Component.empty()
                        .append(Component.translatable(modifierTranslationKey(effect.modifierId()))
                                .withStyle(ChatFormatting.AQUA));
                if (effectLevel > 1) {
                    line.append(Component.literal(" " + SmitheryToolItem.toRoman(effectLevel))
                            .withStyle(ChatFormatting.AQUA));
                }
                tooltip.accept(SmitheryTooltips.subLine(line));

                String descKey = modifierDescriptionKey(effect.modifierId());
                if (I18n.exists(descKey)) {
                    tooltip.accept(SmitheryTooltips.subLine(
                            SmitheryTooltips.description(Component.translatable(descKey))));
                }

                if (tier == SmitheryTooltips.Tier.FULL && !effect.params().isEmpty()) {
                    for (var p : effect.params().entrySet()) {
                        tooltip.accept(SmitheryTooltips.subLine(Component.translatable(
                                "tooltip." + Smithery.MODID + ".modifier.param_line",
                                p.getKey(), formatParamValue(p.getValue()))
                                .withStyle(ChatFormatting.DARK_GRAY)));
                    }
                }
            }
        }

        SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, level, lines, flag);
    }

    /** Format an effect-parameter value for FULL-tier display: floats to 2 decimals, others as-is. */
    private static String formatParamValue(Object value) {
        if (value instanceof Float f)  return String.format("%.2f", f);
        if (value instanceof Double d) return String.format("%.2f", d);
        return String.valueOf(value);
    }

    /** Translation key shared by material display names. */
    public static String materialTranslationKey(ResourceLocation materialId) {
        return Smithery.MODID + ".material." + materialId.getNamespace() + "." + materialId.getPath();
    }

    /** Translation key shared by part-type display names. */
    public static String partTranslationKey(ResourceLocation partTypeId) {
        return Smithery.MODID + ".part." + partTypeId.getNamespace() + "." + partTypeId.getPath();
    }

    /** Translation key shared by tool-type display names. */
    public static String toolTypeTranslationKey(ResourceLocation toolTypeId) {
        return Smithery.MODID + ".tool." + toolTypeId.getNamespace() + "." + toolTypeId.getPath();
    }

    /** Translation key shared by modifier display names. */
    public static String modifierTranslationKey(ResourceLocation modifierId) {
        return Smithery.MODID + ".modifier." + modifierId.getNamespace() + "." + modifierId.getPath();
    }

    /** Translation key for the description text shown in tooltips when Shift is held. */
    public static String modifierDescriptionKey(ResourceLocation modifierId) {
        return modifierTranslationKey(modifierId) + ".description";
    }
}
