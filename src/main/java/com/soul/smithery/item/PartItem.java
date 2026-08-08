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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Returns the static ARGB tint colour for this part's material; defaults to opaque white
     * when the material can't be resolved. The live (possibly animated) render color is
     * resolved by the item color handler via {@code MaterialColorAnimator}.
     */
    public int tintColor() {
        Material m = material();
        return m != null ? m.stats().partColor() : 0xFFFFFFFF;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parts of foil materials shimmer with the enchantment glint.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        Material m = material();
        return (m != null && m.stats().foil()) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
                "item." + Smithery.MODID + ".part_combo",
                Component.translatable(materialTranslationKey(materialId)),
                Component.translatable(partTranslationKey(partTypeId))
        );
    }

    /** How many tool types a scope qualifier names before collapsing the rest into "+N more". */
    private static final int TOOL_LIST_CAP = 4;

    /** One modifier this part grants, and the tool types it grants it in. */
    private record TraitEntry(ModifierEffect sample, List<ToolType> tools) {}

    /**
     * {@inheritDoc}
     *
     * <p>Everything here is stated once, per part, rather than once per tool type the part fits.
     * A handle is used by nineteen tool types and contributes the same durability to every one of
     * them, so the old shape drew nineteen near-identical blocks and ran off the top of the screen.
     * The stats below are all functions of (material, part type) alone, so they get a single line
     * each; only the traits can genuinely vary per tool type, and those are grouped by modifier
     * with a scope qualifier attached only where the modifier isn't granted everywhere.</p>
     */
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
        List<ToolType> usedIn = SmitheryAPI.toolTypesUsingPart(pt);

        // Roles and head-ness, gathered in one pass so the summary can be stated flatly.
        boolean addsDurability = false;
        boolean scalesDurability = false;
        boolean everHead = false;
        boolean gatesHarvest = false;
        List<ToolType> slotted = new ArrayList<>();
        List<Boolean> headHere = new ArrayList<>();
        for (ToolType tt : usedIn) {
            ToolType.Slot ourSlot = null;
            ToolType.Slot firstAdditive = null;
            for (ToolType.Slot s : tt.slots()) {
                if (firstAdditive == null && s.role() == DurabilityRole.ADDITIVE) firstAdditive = s;
                if (ourSlot == null && s.partType().equals(pt)) ourSlot = s;
            }
            if (ourSlot == null) continue;
            boolean isPrimaryAdditive = firstAdditive != null && firstAdditive.partType().equals(pt);
            slotted.add(tt);
            headHere.add(isPrimaryAdditive);

            if (ourSlot.role() == DurabilityRole.ADDITIVE) addsDurability = true;
            else scalesDurability = true;
            if (isPrimaryAdditive) {
                everHead = true;
                if (SmitheryToolItem.harvestLevelApplies(tt)) gatesHarvest = true;
            }
        }

        // --- summary: one line per stat, all of them (material x part type) functions --------
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.part_summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        // Only a head sets the tool's harvest level, and only where that level can gate a drop.
        if (gatesHarvest) {
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.harvest_level", mat.stats().harvestLevel())));
        }
        if (everHead) {
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.attack_damage",
                    String.format("%.1f", mat.stats().attackDamage()))));
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.mining_speed",
                    String.format("%.1f", mat.stats().miningSpeed()))));
        }
        if (addsDurability) {
            int contribution = Math.round(mat.stats().durabilityPerIngot() * pt.durabilityScalar());
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.durability_add", contribution)));
        }
        if (scalesDurability) {
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.durability_mul",
                    String.format("%.2f", mat.stats().binderMultiplier()))));
        }
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

        // --- traits: grouped by modifier, not repeated per tool type ------------------------
        // Head-scoped traits only exist where this part IS the head, part-scoped ones everywhere
        // it fits, so the tool list each entry accumulates is exactly its real scope.
        LinkedHashMap<String, TraitEntry> traits = new LinkedHashMap<>();
        for (int i = 0; i < slotted.size(); i++) {
            ToolType tt = slotted.get(i);
            for (ModifierEffect effect : mat.stats().modifiersFor(tt, pt, headHere.get(i))) {
                String key = effect.modifierId() + "#" + effect.paramInt("level", 1);
                traits.computeIfAbsent(key, k -> new TraitEntry(effect, new ArrayList<>()))
                      .tools().add(tt);
            }
        }

        if (!traits.isEmpty()) {
            tooltip.accept(SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".section.part_traits")));
            for (TraitEntry entry : traits.values()) {
                ModifierEffect effect = entry.sample();
                int effectLevel = effect.paramInt("level", 1);
                MutableComponent line = Component.empty()
                        .append(Component.translatable(modifierTranslationKey(effect.modifierId()))
                                .withStyle(ChatFormatting.AQUA));
                if (effectLevel > 1) {
                    line.append(Component.literal(" " + SmitheryToolItem.toRoman(effectLevel))
                            .withStyle(ChatFormatting.AQUA));
                }
                tooltip.accept(SmitheryTooltips.bullet(line));

                // A trait granted in every tool this part fits needs no qualifier — saying so
                // would just restate the "Used in" line below on every single entry.
                if (entry.tools().size() < slotted.size()) {
                    tooltip.accept(SmitheryTooltips.subLine(Component.translatable(
                            "tooltip." + Smithery.MODID + ".part.applies_to",
                            joinToolNames(entry.tools()))));
                }

                tooltip.accept(SmitheryTooltips.subLine(
                        SmitheryTooltips.description(modifierDescription(effect.modifierId()))));

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

        if (!slotted.isEmpty()) {
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.used_in_list", joinToolNames(slotted))));
        }

        SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, level, lines, flag);
    }

    /**
     * Comma-joins tool-type display names, collapsing anything past {@link #TOOL_LIST_CAP} into a
     * "+N more" tail. Tooltips don't wrap, so an uncapped list of nineteen names would run off the
     * side of the screen — the same overflow this rewrite exists to remove.
     */
    private static Component joinToolNames(List<ToolType> tools) {
        MutableComponent out = Component.empty();
        int shown = Math.min(tools.size(), TOOL_LIST_CAP);
        for (int i = 0; i < shown; i++) {
            if (i > 0) out.append(Component.literal(", "));
            out.append(Component.translatable(toolTypeTranslationKey(tools.get(i).id())));
        }
        int remaining = tools.size() - shown;
        if (remaining > 0) {
            out.append(Component.literal(", "))
               .append(Component.translatable("tooltip." + Smithery.MODID + ".part.and_more", remaining));
        }
        return out;
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

    /**
     * Modifier description for display, falling back to a localized "No description" when neither
     * Smithery nor the contributing mod supplies one. Callers can therefore always render a
     * description line without gating on {@link net.minecraft.client.resources.language.I18n}.
     *
     * @param modifierId id of the modifier
     * @return the modifier's description component, or the "No description" fallback
     */
    public static Component modifierDescription(ResourceLocation modifierId) {
        String key = modifierDescriptionKey(modifierId);
        return I18n.exists(key)
                ? Component.translatable(key)
                : Component.translatable("smithery.modifier.no_description");
    }
}
