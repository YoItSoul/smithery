package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.SmitheryTooltips;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * Smithery armor item: helmet, chestplate, leggings, or boots, with composition-driven defense,
 * toughness, and durability.
 *
 * <p>Extends {@link Item} directly rather than {@code ArmorItem} so we sidestep the static
 * {@code ArmorMaterial} system — every stat comes from the per-stack
 * {@link com.soul.smithery.item.tool.ToolStats} write into {@code ATTRIBUTE_MODIFIERS} at
 * compose time. Item.Properties carries the {@code EQUIPPABLE} data component (set via
 * {@code .equippable(slot)} at registration), so the vanilla right-click-to-equip path, mob
 * pickup, and dispenser behaviour all see us as an armor item.
 */
public class SmitheryArmorItem extends Item {
    private final Identifier toolTypeId;

    /**
     * Constructs the armor item bound to the given Smithery ToolType id (helmet/chestplate/leggings/boots).
     */
    public SmitheryArmorItem(Properties properties, Identifier toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:helmet}). */
    public Identifier toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this armor item, or null if unregistered. */
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    /** Reads the {@link ToolComposition} from the given stack's data component (may be null). */
    public ToolComposition compositionOf(ItemStack stack) {
        return stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
    }

    /**
     * Maps a Smithery armor tool-type path to its vanilla {@link EquipmentSlot}. Unknown paths
     * fall back to the chest slot so a malformed binding still equips somewhere sensible.
     */
    public static EquipmentSlot slotForToolTypeId(Identifier toolTypeId) {
        return switch (toolTypeId.getPath()) {
            case "helmet"     -> EquipmentSlot.HEAD;
            case "chestplate" -> EquipmentSlot.CHEST;
            case "leggings"   -> EquipmentSlot.LEGS;
            case "boots"      -> EquipmentSlot.FEET;
            default           -> EquipmentSlot.CHEST;
        };
    }

    /**
     * Writes the composition-derived durability and attribute modifiers (armor, toughness,
     * knockback resistance) onto the stack. Mirrors {@link SmitheryToolItem#applyComposition}
     * for tools but uses armor-slot attributes instead of attack damage / attack speed.
     */
    public static ItemStack applyComposition(ItemStack stack, ToolComposition comp) {
        stack.remove(DataComponents.ENCHANTMENTS);

        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(), java.util.List.of());
        ToolStats stats = ToolStats.compute(comp, applied);

        stack.set(SmitheryDataComponents.TOOL_COMPOSITION.get(), comp);
        stack.set(DataComponents.MAX_DAMAGE, stats.maxDurability);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.DAMAGE, 0);

        ToolType tt = comp.toolType();
        EquipmentSlot slot = tt != null ? slotForToolTypeId(tt.id()) : EquipmentSlot.CHEST;
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
        Identifier armorId = Identifier.fromNamespaceAndPath(Smithery.MODID,
                "armor." + (comp.toolType() != null ? comp.toolType().id().getPath() : "unknown"));

        ItemAttributeModifiers.Builder attrs = ItemAttributeModifiers.builder();
        attrs.add(Attributes.ARMOR,
                new AttributeModifier(armorId, stats.armorDefense, AttributeModifier.Operation.ADD_VALUE),
                group);
        if (stats.armorToughness > 0.001f) {
            attrs.add(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(armorId, stats.armorToughness, AttributeModifier.Operation.ADD_VALUE),
                    group);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs.build());

        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = compositionOf(stack);
        ToolType tt = toolType();
        if (comp == null || !comp.isValid() || tt == null) {
            return Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId));
        }
        Identifier primary = primaryAdditiveMaterial(tt, comp);
        Component matName = primary != null
                ? Component.translatable(PartItem.materialTranslationKey(primary))
                : Component.literal("");
        return Component.translatable("item." + Smithery.MODID + ".part_combo",
                matName, Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId)));
    }

    private static Identifier primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == com.soul.smithery.api.tool.DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        ToolComposition comp = compositionOf(stack);
        ToolType tt = toolType();
        if (comp == null || tt == null || !comp.isValid()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.uncomposed")
                    .withStyle(ChatFormatting.RED));
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(), java.util.List.of());
        ToolStats stats = ToolStats.compute(comp, applied);
        SmitheryTooltips.Tier tier = SmitheryTooltips.currentTier();

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (tier == SmitheryTooltips.Tier.BASIC) {
            SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        tooltip.accept(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".armor.defense",
                String.format("%.1f", stats.armorDefense))));
        if (stats.armorToughness > 0.001f) {
            tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                    "tooltip." + Smithery.MODID + ".armor.toughness",
                    String.format("%.1f", stats.armorToughness))));
        }

        tooltip.accept(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.parts")));
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot slot = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
            if (m == null) continue;
            PartType pt = slot.partType();
            tooltip.accept(SmitheryTooltips.bullet(Component.empty()
                    .append(Component.translatable(PartItem.materialTranslationKey(m.id())))
                    .append(Component.literal(" "))
                    .append(Component.translatable(PartItem.partTranslationKey(pt.id())))));
        }

        if (!stats.allEffects.isEmpty()) {
            tooltip.accept(SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".tool.modifiers")));
            for (ToolStats.ResolvedEffect r : stats.allEffects) {
                int level = r.effect().paramInt("level", 1);
                MutableComponent line = Component.empty()
                        .append(Component.translatable(PartItem.modifierTranslationKey(r.effect().modifierId()))
                                .withStyle(ChatFormatting.AQUA));
                if (level > 1) {
                    line.append(Component.literal(" " + SmitheryToolItem.toRoman(level))
                            .withStyle(ChatFormatting.AQUA));
                }
                tooltip.accept(SmitheryTooltips.bullet(line));
                String descKey = PartItem.modifierDescriptionKey(r.effect().modifierId());
                if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
                    tooltip.accept(SmitheryTooltips.subLine(
                            SmitheryTooltips.description(Component.translatable(descKey))));
                }
            }
        }

        if (!stats.activeSynergies.isEmpty()) {
            tooltip.accept(SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".tool.synergies")));
            for (SynergyDefinition s : stats.activeSynergies) {
                tooltip.accept(SmitheryTooltips.synergyBullet(
                        Component.translatable(SmitheryToolItem.synergyTranslationKey(s.id()))
                                .withStyle(ChatFormatting.LIGHT_PURPLE)));
            }
        }

        SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
