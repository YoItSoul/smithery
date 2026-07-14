package com.soul.smithery.item.tool;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.SmitheryTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Smithery armor item: helmet, chestplate, leggings, or boots, with composition-driven defense,
 * toughness, and durability.
 *
 * <p>Extends {@link ArmorItem} with a zeroed placeholder {@link ArmorMaterial} — the vanilla
 * equip/dispense/mob-pickup paths key off the ArmorItem type, while every real stat is served
 * live from the per-stack {@link ToolStats} through the stack-sensitive overrides
 * ({@link #getAttributeModifiers}, {@link #getMaxDamage}). The worn model renders the shared
 * grayscale layers dyed with the core material's color via {@link DyeableLeatherItem}.
 */
public class SmitheryArmorItem extends ArmorItem implements DyeableLeatherItem {

    /** Stable armor/toughness modifier UUIDs, one per armor slot (vanilla convention). */
    private static final UUID[] ARMOR_MODIFIER_UUIDS = {
            UUID.fromString("6b305ae4-9d17-4c99-9aef-4f3c7f2eab10"), // FEET
            UUID.fromString("7f10b7b4-64de-4d3e-93d5-7f31b2f5cd21"), // LEGS
            UUID.fromString("8a9e5f0c-32af-4b41-8f3a-9c78d1a3ef32"), // CHEST
            UUID.fromString("9d4bc2f1-51fd-4a6e-b64d-2e11c0d8ba43"), // HEAD
    };

    /**
     * Placeholder material: 1-point durability (the composed value is served by
     * {@link #getMaxDamage}), zero built-in defense (served by {@link #getAttributeModifiers}),
     * iron-like feel for sound and enchantability.
     */
    private static final ArmorMaterial COMPOSED_MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(ArmorItem.Type type) { return 1; }
        @Override public int getDefenseForType(ArmorItem.Type type) { return 0; }
        @Override public int getEnchantmentValue() { return 9; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return Smithery.MODID + ":composed"; }
        @Override public float getToughness() { return 0f; }
        @Override public float getKnockbackResistance() { return 0f; }
    };

    private final ResourceLocation toolTypeId;

    /**
     * Constructs the armor item bound to the given Smithery ToolType id (helmet/chestplate/leggings/boots).
     */
    public SmitheryArmorItem(ArmorItem.Type type, Properties properties, ResourceLocation toolTypeId) {
        super(COMPOSED_MATERIAL, type, properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:helmet}). */
    public ResourceLocation toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this armor item, or null if unregistered. */
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    /** Reads the {@link ToolComposition} from the given stack's NBT (may be null). */
    public ToolComposition compositionOf(ItemStack stack) {
        return SmitheryToolData.getComposition(stack);
    }

    /**
     * Smithery armor never shatters. Damage is clamped one point short of the vanilla break
     * threshold, so the piece survives at "broken" (see {@link #isBrokenArmor}) where it grants
     * no attributes until repaired — losing the armor investment to one creeper would erase
     * the whole part-crafting loop.
     */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity,
                                                   Consumer<T> onBroken) {
        return Math.min(amount, Math.max(0, stack.getMaxDamage() - 1 - stack.getDamageValue()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serves the composed durability persisted by {@link #applyComposition}; uncomposed
     * stacks fall back to the placeholder material's 1.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return SmitheryToolData.getMaxDurability(stack, super.getMaxDamage(stack));
    }

    /**
     * True when {@code stack} is smithery armor sitting at its damage cap (max - 1, the
     * clamp from {@link #damageItem}). Broken armor stays equipped and repairable but grants
     * no attribute modifiers — {@link #getAttributeModifiers} returns none.
     */
    public static boolean isBrokenArmor(ItemStack stack) {
        return stack.getItem() instanceof SmitheryArmorItem
                && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /**
     * Maps a Smithery armor tool-type path to its vanilla {@link EquipmentSlot}. Unknown paths
     * fall back to the chest slot so a malformed binding still equips somewhere sensible.
     */
    public static EquipmentSlot slotForToolTypeId(ResourceLocation toolTypeId) {
        return switch (toolTypeId.getPath()) {
            case "helmet"     -> EquipmentSlot.HEAD;
            case "chestplate" -> EquipmentSlot.CHEST;
            case "leggings"   -> EquipmentSlot.LEGS;
            case "boots"      -> EquipmentSlot.FEET;
            default           -> EquipmentSlot.CHEST;
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serves defense and toughness computed live from the stack's composition; broken
     * armor (see {@link #isBrokenArmor}) grants nothing until repaired.
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != getType().getSlot()) return ImmutableMultimap.of();
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null || !comp.isValid() || isBrokenArmor(stack)) return ImmutableMultimap.of();

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        ToolStats stats = ToolStats.compute(comp, applied);

        UUID uuid = ARMOR_MODIFIER_UUIDS[getType().getSlot().getIndex()];
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ARMOR,
                new AttributeModifier(uuid, "Armor modifier",
                        stats.armorDefense, AttributeModifier.Operation.ADDITION));
        if (stats.armorToughness > 0.001f) {
            builder.put(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(uuid, "Armor toughness",
                            stats.armorToughness, AttributeModifier.Operation.ADDITION));
        }

        // Modifier-granted bonuses (speedy, high stride, ...) ride their own NBT channel and
        // stack on top of the composed base attributes.
        for (SmitheryToolData.ExtraAttribute extra : SmitheryToolData.getExtraAttributes(stack)) {
            if (extra.slot() != slot) continue;
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(extra.attributeId());
            if (attribute == null) continue;
            UUID extraUuid = UUID.nameUUIDFromBytes(
                    (Smithery.MODID + ":extra:" + extra.name()).getBytes(StandardCharsets.UTF_8));
            builder.put(attribute, new AttributeModifier(
                    extraUuid, extra.name(), extra.amount(), extra.operation()));
        }
        return builder.build();
    }

    /**
     * Writes the composition-derived durability onto the stack, dyes the worn layers with the
     * core material's color, then fires compose hooks so armor modifiers behave exactly like
     * tool modifiers. Mirrors {@link SmitheryToolItem#applyComposition} for tools but with
     * armor attributes served by {@link #getAttributeModifiers}.
     *
     * <p>Prefer {@link ToolCompositions#apply} unless the stack is known to be armor —
     * it dispatches by item family and can resolve {@code lookup}.
     *
     * @param lookup registry access for compose actions; pass null only when no live
     *               registry is available, in which case affected actions skip silently
     */
    public static ItemStack applyComposition(ItemStack stack, ToolComposition comp,
                                             HolderLookup.@Nullable Provider lookup) {
        stack.removeTagKey("Enchantments");
        SmitheryToolData.clearExtraAttributes(stack);

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        ToolStats stats = ToolStats.compute(comp, applied);

        SmitheryToolData.setComposition(stack, comp);
        // Preserve wear across recomposition — see SmitheryToolItem.applyComposition.
        int priorDamage = stack.getDamageValue();
        SmitheryToolData.setMaxDurability(stack, stats.maxDurability);
        stack.getOrCreateTag().putInt("Damage", Math.min(priorDamage, stats.maxDurability - 1));

        applyWornTint(stack, comp.toolType(), comp);

        ToolCompositions.fireComposeHooks(stack, stats, lookup);
        return stack;
    }

    /**
     * Writes the core material's part color into the dye NBT so the shared grayscale armor
     * layers render in the material's color on the player. The dye tooltip line is hidden —
     * the color is derived state, not a player-applied dye.
     */
    private static void applyWornTint(ItemStack stack, ToolType tt, ToolComposition comp) {
        ResourceLocation coreMaterial = tt != null ? primaryAdditiveMaterial(tt, comp) : null;
        Material material = coreMaterial != null ? SmitheryAPI.MATERIALS.get(coreMaterial) : null;
        if (material == null) return;
        stack.getOrCreateTagElement("display").putInt("color", material.stats().partColor() & 0xFFFFFF);
        stack.hideTooltipPart(ItemStack.TooltipPart.DYE);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Both worn layers point at the shared grayscale armor textures;
     * {@link DyeableLeatherItem} tints them with the composed color at render time.
     */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return Smithery.MODID + ":textures/models/armor/armor_layer_" + layer + ".png";
    }

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = compositionOf(stack);
        ToolType tt = toolType();
        if (comp == null || !comp.isValid() || tt == null) {
            return Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId));
        }
        ResourceLocation primary = primaryAdditiveMaterial(tt, comp);
        Component matName = primary != null
                ? Component.translatable(PartItem.materialTranslationKey(primary))
                : Component.literal("");
        return Component.translatable("item." + Smithery.MODID + ".part_combo",
                matName, Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId)));
    }

    private static ResourceLocation primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Consumer<Component> tooltip = lines::add;
        ToolComposition comp = compositionOf(stack);
        ToolType tt = toolType();
        if (comp == null || tt == null || !comp.isValid()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.uncomposed")
                    .withStyle(ChatFormatting.RED));
            super.appendHoverText(stack, level, lines, flag);
            return;
        }

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        ToolStats stats = ToolStats.compute(comp, applied);
        SmitheryTooltips.Tier tier = SmitheryTooltips.currentTier();

        if (isBrokenArmor(stack)) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".armor.broken")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (tier == SmitheryTooltips.Tier.BASIC) {
            SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, level, lines, flag);
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

        comp.embossedMaterial().ifPresent(donor -> tooltip.accept(
                SmitheryTooltips.statLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".tool.embossed",
                        Component.translatable(PartItem.materialTranslationKey(donor))))));

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
                int effectLevel = r.effect().paramInt("level", 1);
                MutableComponent line = Component.empty()
                        .append(Component.translatable(PartItem.modifierTranslationKey(r.effect().modifierId()))
                                .withStyle(ChatFormatting.AQUA));
                if (effectLevel > 1) {
                    line.append(Component.literal(" " + SmitheryToolItem.toRoman(effectLevel))
                            .withStyle(ChatFormatting.AQUA));
                }
                tooltip.accept(SmitheryTooltips.bullet(line));
                String descKey = PartItem.modifierDescriptionKey(r.effect().modifierId());
                if (I18n.exists(descKey)) {
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
        super.appendHoverText(stack, level, lines, flag);
    }
}
