package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.ToolAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Single item class for every smithery tool (sword, pickaxe, axe, shovel, hoe, spear).
 * The ToolType is fixed per item instance; per-stack composition lives in the
 * TOOL_COMPOSITION data component and drives all stats. {@link #applyComposition}
 * stamps the derived stats onto vanilla components so vanilla systems handle damage,
 * mining, and attack speed without per-frame work.
 */
public class SmitheryToolItem extends Item {
    private final ResourceLocation toolTypeId;

    /**
     * Constructs the tool item bound to the given smithery ToolType id.
     */
    public SmitheryToolItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:sword}). */
    public ResourceLocation toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this tool item, or null if unregistered. */
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    /** Reads the {@link ToolComposition} from the given stack's data component (may be null). */
    public ToolComposition compositionOf(ItemStack stack) {
        return stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
    }

    /**
     * Computes stats from {@code comp} plus any post-craft modifiers already on the
     * stack, then writes them onto the vanilla data components. Clears any prior
     * ENCHANTMENTS so smithery modifiers fully own enchantment state, then fires every
     * {@link Modifier.OnCompose} hook on the effective effect list.
     *
     * <p>Prefer {@link ToolCompositions#apply} unless the stack is known to be a
     * non-armor tool — it dispatches by item family and can resolve {@code lookup}.
     *
     * @param lookup registry access for compose actions; pass null only when no live
     *               registry is available, in which case affected actions skip silently
     */
    public static ItemStack applyComposition(ItemStack stack, ToolComposition comp,
                                              net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup) {
        stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);

        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(), java.util.List.of());
        int wearDamage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        int wearMax = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        float missing = wearMax > 0 ? (float) wearDamage / wearMax : 0f;
        ToolStats stats = ToolStats.compute(comp, applied, missing);
        stack.set(SmitheryDataComponents.TOOL_COMPOSITION.get(), comp);

        String composePath = comp.toolType() != null ? comp.toolType().id().getPath() : "";
        boolean stackable = "arrow".equals(composePath) || "shuriken".equals(composePath);
        if (stackable) {
            ToolCompositions.fireComposeHooks(stack, stats, lookup);
            return stack;
        }

        int priorDamage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.MAX_DAMAGE, stats.maxDurability);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        // Preserve wear across recomposition (anvil modifiers, stat recomputes) — resetting to
        // 0 here made every modifier application a free full repair.
        stack.set(DataComponents.DAMAGE, Math.min(priorDamage, stats.maxDurability - 1));

        float scaledDamage = comp.toolType() != null
                ? stats.attackDamage * damageScaleFor(comp.toolType())
                : stats.attackDamage;
        var attrs = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                Math.max(0f, scaledDamage - 1f),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                attackSpeedFor(comp), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);

        ToolType tt = comp.toolType();
        if (tt != null) {
            Tool tool = buildToolComponent(tt, stats);
            if (tool != null) stack.set(DataComponents.TOOL, tool);

            String ttPath = tt.id().getPath();
            if ("sword".equals(ttPath) || "spear".equals(ttPath)
                    || "broadsword".equals(ttPath) || "rapier".equals(ttPath)
                    || "cleaver".equals(ttPath) || "battlesign".equals(ttPath)) {
                stack.set(DataComponents.WEAPON, new Weapon(1, 0.0f));
            }
            if ("battlesign".equals(ttPath)) {
                applyBlockingComponents(stack, lookup);
            }
            if ("spear".equals(ttPath)) {
                applySpearComponents(stack, comp, lookup);
            }
        }

        ToolCompositions.fireComposeHooks(stack, stats, lookup);
        return stack;
    }

    /**
     * Writes the BLOCKS_ATTACKS component that makes the battlesign block while raised —
     * the vanilla component drives the animation, use duration, damage reduction, and
     * axe-disable with no custom item code. Battlesigns are weapons first: they block a
     * 60° arc at 60% reduction and take disable cooldowns 20% longer than a vanilla
     * shield. (The vanilla shield remains the dedicated full blocker by design.)
     *
     * <p>The BYPASSES_SHIELD exemption needs registry access; composed without a lookup
     * (creative-tab previews) the block simply has no bypass list until a real recompose.
     */
    private static void applyBlockingComponents(ItemStack stack,
                                                net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup) {
        java.util.Optional<HolderSet<net.minecraft.world.damagesource.DamageType>> bypassedBy =
                java.util.Optional.empty();
        if (lookup != null) {
            bypassedBy = lookup.lookup(Registries.DAMAGE_TYPE)
                    .flatMap(reg -> reg.get(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD))
                    .map(holders -> (HolderSet<net.minecraft.world.damagesource.DamageType>) holders);
        }
        var reduction = new net.minecraft.world.item.component.BlocksAttacks.DamageReduction(
                60.0f, java.util.Optional.empty(), 0.0f, 0.6f);
        stack.set(DataComponents.BLOCKS_ATTACKS, new net.minecraft.world.item.component.BlocksAttacks(
                0.25f,
                1.2f,
                List.of(reduction),
                new net.minecraft.world.item.component.BlocksAttacks.ItemDamageFunction(3.0f, 1.0f, 1.0f),
                bypassedBy,
                java.util.Optional.of(SoundEvents.SHIELD_BLOCK),
                java.util.Optional.of(SoundEvents.SHIELD_BREAK)));
    }

    /**
     * Re-stamps stats whenever durability changes IF a durability-scaled modifier
     * (Stonebound-style) is present — their mining/damage contributions depend on wear.
     * Safe against recursion: applyComposition writes the DAMAGE component directly and
     * never routes back through here.
     */
    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, damage);
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null || !comp.isValid()) return;
        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(), java.util.List.of());
        if (!ToolStats.compute(comp, applied).hasDurabilityScaled) return;
        ToolCompositions.apply(stack, comp);
    }

    /**
     * Returns the total modifier-slot count granted by every slot of {@code comp}; the
     * tool's capacity for post-craft modifier application.
     */
    public static int totalModifierSlots(ToolComposition comp) {
        ToolType tt = comp.toolType();
        if (tt == null) return 0;
        int sum = 0;
        List<ToolType.Slot> slots = tt.slots();
        java.util.List<ResourceLocation> matIds = comp.slotMaterials();
        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(matIds.get(i));
            if (m == null) continue;
            sum += m.stats().modifierSlotsFor(slots.get(i).partType());
        }
        return sum;
    }

    /**
     * Returns the modifier slots consumed by the stack's APPLIED_MODIFIERS list, where
     * each entry counts as max(1, its level parameter).
     */
    public static int appliedModifierCount(ItemStack stack) {
        int total = 0;
        for (com.soul.smithery.api.modifier.ModifierEffect e :
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(),
                        java.util.List.<com.soul.smithery.api.modifier.ModifierEffect>of())) {
            total += Math.max(1, e.paramInt("level", 1));
        }
        return total;
    }

    /**
     * Indented gray bullet used by the tool and part tooltip stat lines. Shared with
     * {@link PartItem}'s per-tool tooltip sections.
     */
    public static net.minecraft.network.chat.MutableComponent statLine(Component body) {
        return Component.literal(" ▸ ").append(body).withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Formats {@code n} as Roman numerals for tooltip level rendering; falls back to a
     * decimal string for non-positive or absurdly large values.
     */
    public static String toRoman(int n) {
        if (n <= 0) return String.valueOf(n);
        if (n >= 4000) return String.valueOf(n);
        StringBuilder sb = new StringBuilder();
        int[] vals = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < vals.length; i++) {
            while (n >= vals[i]) { sb.append(syms[i]); n -= vals[i]; }
        }
        return sb.toString();
    }

    /** Returns the modifier slots remaining on {@code stack}, clamped to non-negative. */
    public static int freeModifierSlots(ItemStack stack) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null) return 0;
        return Math.max(0, totalModifierSlots(comp) - appliedModifierCount(stack));
    }

    private static float attackSpeedFor(ToolComposition comp) {
        ToolType tt = comp.toolType();
        if (tt == null) return -2.4f;
        String path = tt.id().getPath();
        return switch (path) {
            case "sword"         -> -2.4f;
            case "broadsword"    -> -3.0f;
            case "rapier"        -> -1.6f;
            case "pickaxe"       -> -2.8f;
            case "paxel"         -> -2.9f;
            case "mining_hammer" -> -3.4f;
            case "axe"           -> -3.2f;
            case "kama"          -> -2.2f;
            case "battlesign"    -> -2.4f;
            case "cleaver"       -> -3.4f;
            case "lumberaxe"     -> -3.3f;
            case "excavator"     -> -3.2f;
            case "trident"       -> -2.9f;
            case "shovel"        -> -3.0f;
            case "hoe"           -> -3.0f;
            case "spear"         -> 1.0f / spearAttackDuration(headHarvestLevel(comp)) - 4.0f;
            default              -> -2.6f;
        };
    }

    /**
     * Per-tool-type multiplier on the primary material's attack damage — the weapon-identity
     * lever: broadswords trade speed for weight, rapiers the reverse (their edge is the
     * armor-piercing thrust, see ToolModifierEventRouter).
     */
    private static float damageScaleFor(ToolType tt) {
        return switch (tt.id().getPath()) {
            case "broadsword"    -> 1.35f;
            case "rapier"        -> 0.7f;
            case "mining_hammer" -> 1.2f;
            case "cleaver"       -> 1.5f;
            case "battlesign"    -> 0.9f;
            case "lumberaxe"     -> 1.15f;
            case "trident"       -> 1.1f;
            default              -> 1.0f;
        };
    }

    private static int headHarvestLevel(ToolComposition comp) {
        ToolType tt = comp.toolType();
        if (tt == null) return 2;
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == com.soul.smithery.api.tool.DurabilityRole.ADDITIVE) {
                Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
                if (m != null) return m.stats().harvestLevel();
                break;
            }
        }
        return 2;
    }

    private static float spearAttackDuration(int harvestLevel) {
        float t = Mth.clamp(harvestLevel, 0, 4) / 4.0f;
        return 0.65f + t * 0.5f;
    }

    private static void applySpearComponents(ItemStack stack, ToolComposition comp,
                                              net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup) {
        int hl = headHarvestLevel(comp);
        float t = Mth.clamp(hl, 0, 4) / 4.0f;

        float attackDuration     = spearAttackDuration(hl);
        float damageMultiplier   = 0.7f  + t * 0.5f;
        float delay              = 0.75f - t * 0.35f;
        float dismountTime       = 5.0f  - t * 2.5f;
        float dismountThreshold  = 14.0f - t * 5.0f;
        float knockbackTime      = 10.0f - t * 4.5f;
        float knockbackThreshold = 5.1f;
        float damageTime         = 15.0f - t * 6.25f;
        float damageThreshold    = 4.6f;

        boolean wooden = (hl <= 0);
        Holder<SoundEvent> useSound    = wooden ? SoundEvents.SPEAR_WOOD_USE    : SoundEvents.SPEAR_USE;
        Holder<SoundEvent> attackSound = wooden ? SoundEvents.SPEAR_WOOD_ATTACK : SoundEvents.SPEAR_ATTACK;
        Holder<SoundEvent> hitSound    = wooden ? SoundEvents.SPEAR_WOOD_HIT    : SoundEvents.SPEAR_HIT;

        stack.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(
                10,
                (int)(delay * 20.0f),
                KineticWeapon.Condition.ofAttackerSpeed((int)(dismountTime * 20.0f), dismountThreshold),
                KineticWeapon.Condition.ofAttackerSpeed((int)(knockbackTime * 20.0f), knockbackThreshold),
                KineticWeapon.Condition.ofRelativeSpeed((int)(damageTime * 20.0f), damageThreshold),
                0.38f,
                damageMultiplier,
                Optional.of(useSound),
                Optional.of(hitSound)));

        stack.set(DataComponents.PIERCING_WEAPON, new PiercingWeapon(
                true, false, Optional.of(attackSound), Optional.of(hitSound)));

        stack.set(DataComponents.ATTACK_RANGE,
                new AttackRange(2.0f, 4.5f, 2.0f, 6.5f, 0.125f, 0.5f));
        stack.set(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0f);
        stack.set(DataComponents.SWING_ANIMATION,
                new SwingAnimation(SwingAnimationType.STAB, (int)(attackDuration * 20.0f)));
        stack.set(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0f));

        if (lookup != null) {
            try {
                Holder<DamageType> spearDamageType =
                        lookup.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SPEAR);
                stack.set(DataComponents.DAMAGE_TYPE, spearDamageType);
            } catch (Throwable t2) {
                Smithery.LOGGER.debug("Spear DAMAGE_TYPE unset (registry unavailable): {}", t2.toString());
            }
        }
    }

    private static @org.jspecify.annotations.Nullable Tool buildToolComponent(ToolType tt, ToolStats stats) {
        List<Tool.Rule> rules = new ArrayList<>();
        String path = tt.id().getPath();
        switch (path) {
            case "pickaxe" -> {
                HolderSet<Block> minable = blockTag(BlockTags.MINEABLE_WITH_PICKAXE);
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                rules.add(Tool.Rule.minesAndDrops(minable, stats.miningSpeed));
            }
            case "axe" -> {
                HolderSet<Block> minable = blockTag(BlockTags.MINEABLE_WITH_AXE);
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                rules.add(Tool.Rule.minesAndDrops(minable, stats.miningSpeed));
            }
            case "shovel" -> {
                HolderSet<Block> minable = blockTag(BlockTags.MINEABLE_WITH_SHOVEL);
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                rules.add(Tool.Rule.minesAndDrops(minable, stats.miningSpeed));
            }
            case "hoe" -> {
                HolderSet<Block> minable = blockTag(BlockTags.MINEABLE_WITH_HOE);
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                rules.add(Tool.Rule.minesAndDrops(minable, stats.miningSpeed));
            }
            case "sword", "broadsword", "rapier" -> {
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.SWORD_EFFICIENT), stats.miningSpeed));
            }
            case "cleaver", "battlesign" -> {
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.SWORD_EFFICIENT), stats.miningSpeed));
            }
            case "lumberaxe" -> {
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                // Slower per-block: the identity is the whole tree, not the single log.
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_AXE),
                        stats.miningSpeed * 0.7f));
            }
            case "excavator" -> {
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_SHOVEL),
                        stats.miningSpeed * 0.6f));
            }
            case "kama" -> {
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.SWORD_EFFICIENT), stats.miningSpeed));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.LEAVES), stats.miningSpeed));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.WOOL), stats.miningSpeed));
            }
            case "paxel" -> {
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_PICKAXE), stats.miningSpeed));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_AXE), stats.miningSpeed));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_SHOVEL), stats.miningSpeed));
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_HOE), stats.miningSpeed));
            }
            case "mining_hammer" -> {
                HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
                if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
                // The hammer trades single-block speed for the 3x3 spread (AoeMiningHandler).
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.MINEABLE_WITH_PICKAXE),
                        stats.miningSpeed * 0.6f));
            }
            default -> { return null; }
        }
        return new Tool(rules, 1.0f, 2, true);
    }

    private static HolderSet<Block> incorrectForTier(int harvestLevel) {
        TagKey<Block> tag = switch (harvestLevel) {
            case 0 -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> null;
        };
        return tag == null ? null : blockTag(tag);
    }

    private static HolderSet<Block> blockTag(TagKey<Block> tag) {
        return BuiltInRegistries.BLOCK.getOrThrow(tag);
    }

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = compositionOf(stack);
        ToolType tt = toolType();
        if (comp == null || !comp.isValid() || tt == null) {
            return Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId));
        }
        ResourceLocation primaryMat = primaryAdditiveMaterial(comp);
        Component matName = primaryMat != null
                ? Component.translatable(PartItem.materialTranslationKey(primaryMat))
                : Component.literal("");
        return Component.translatable("item." + Smithery.MODID + ".part_combo",
                matName,
                Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId)));
    }

    private ResourceLocation primaryAdditiveMaterial(ToolComposition comp) {
        ToolType tt = toolType();
        if (tt == null) return null;
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
        com.soul.smithery.item.SmitheryTooltips.Tier tier = com.soul.smithery.item.SmitheryTooltips.currentTier();

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (tier == com.soul.smithery.item.SmitheryTooltips.Tier.BASIC) {
            com.soul.smithery.item.SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        // DETAIL and FULL ---------------------------------------------------
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")));
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("%.1f", stats.attackDamage))));
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.mining_speed",
                String.format("%.1f", stats.miningSpeed))));
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".part.harvest_level", stats.harvestLevel)));

        comp.embossedMaterial().ifPresent(donor -> tooltip.accept(
                com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".tool.embossed",
                        Component.translatable(PartItem.materialTranslationKey(donor))))));

        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.parts")));
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot slot = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
            if (m == null) continue;
            PartType pt = slot.partType();
            Component matName = Component.translatable(PartItem.materialTranslationKey(m.id()));
            Component partName = Component.translatable(PartItem.partTranslationKey(pt.id()));
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.bullet(
                    Component.empty().append(matName).append(Component.literal(" ")).append(partName)));
        }

        int totalSlots = totalModifierSlots(comp);
        int usedSlots = applied.size();
        int freeSlots = Math.max(0, totalSlots - usedSlots);

        if (!stats.allEffects.isEmpty() || totalSlots > 0) {
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".section.modifiers_count",
                            usedSlots, totalSlots)));
        }
        for (ToolStats.ResolvedEffect r : stats.allEffects) {
            int level = r.effect().paramInt("level", 1);
            MutableComponent line = Component.empty()
                    .append(Component.translatable(PartItem.modifierTranslationKey(r.effect().modifierId()))
                            .withStyle(ChatFormatting.AQUA));
            if (level > 1) {
                line.append(Component.literal(" " + toRoman(level)).withStyle(ChatFormatting.AQUA));
            }
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.bullet(line));

            String descKey = PartItem.modifierDescriptionKey(r.effect().modifierId());
            if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
                tooltip.accept(com.soul.smithery.item.SmitheryTooltips.subLine(
                        com.soul.smithery.item.SmitheryTooltips.description(Component.translatable(descKey))));
            }

            if (tier == com.soul.smithery.item.SmitheryTooltips.Tier.FULL) {
                appendModifierFullDetails(tooltip, r.effect(), r.modifier());
            }
        }

        if (!stats.activeSynergies.isEmpty()) {
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".tool.synergies")));
            for (SynergyDefinition s : stats.activeSynergies) {
                tooltip.accept(com.soul.smithery.item.SmitheryTooltips.synergyBullet(
                        Component.translatable(synergyTranslationKey(s.id()))
                                .withStyle(ChatFormatting.LIGHT_PURPLE)));
                if (tier == com.soul.smithery.item.SmitheryTooltips.Tier.FULL) {
                    tooltip.accept(com.soul.smithery.item.SmitheryTooltips.subLine(
                            com.soul.smithery.item.SmitheryTooltips.description(Component.literal(
                                    materialName(s.materialA()) + " + " + materialName(s.materialB())))));
                }
            }
        }

        java.util.Map<net.minecraft.resources.ResourceLocation, Integer> progress =
                stack.getOrDefault(SmitheryDataComponents.MODIFIER_PROGRESS.get(),
                        java.util.Map.<net.minecraft.resources.ResourceLocation, Integer>of());
        if (!progress.isEmpty()) {
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".section.progress")));
            for (var entry : progress.entrySet()) {
                tooltip.accept(com.soul.smithery.item.SmitheryTooltips.bullet(
                        Component.translatable("tooltip." + Smithery.MODID + ".progress.line",
                                Component.translatable(PartItem.modifierTranslationKey(entry.getKey()))
                                        .withStyle(ChatFormatting.YELLOW),
                                entry.getValue())));
            }
        }

        com.soul.smithery.item.SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    /**
     * Appends the FULL-tier extra details for a modifier: every parameter as a {@code key = value}
     * line, plus a level-info or single-use line, plus a durability multiplier line when the
     * modifier's mult is not 1.0.
     *
     * <p>Only invoked when the player holds both Shift and Ctrl, so this is "advanced players
     * who want the actual numbers" territory.
     */
    private static void appendModifierFullDetails(Consumer<Component> tooltip,
                                                  com.soul.smithery.api.modifier.ModifierEffect effect,
                                                  Modifier modifier) {
        if (!effect.params().isEmpty()) {
            for (var p : effect.params().entrySet()) {
                tooltip.accept(com.soul.smithery.item.SmitheryTooltips.subLine(
                        Component.translatable("tooltip." + Smithery.MODID + ".modifier.param_line",
                                p.getKey(), formatParamValue(p.getValue()))
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        if (modifier == null) return;
        if (modifier.maxLevel() > 1) {
            int currentLevel = effect.paramInt("level", 1);
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.subLine(
                    Component.translatable("tooltip." + Smithery.MODID + ".modifier.level_info",
                            currentLevel, modifier.maxLevel(),
                            String.format("%.2f", modifier.levelCostScaling()))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (Math.abs(modifier.durabilityMultiplier() - 1.0f) > 1e-3) {
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.subLine(
                    Component.translatable("tooltip." + Smithery.MODID + ".modifier.dur_mult",
                            String.format("%.2f", modifier.durabilityMultiplier()))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /** Format an effect-parameter value for FULL-tier display: floats to 2 decimals, others as-is. */
    private static String formatParamValue(Object value) {
        if (value instanceof Float f)  return String.format("%.2f", f);
        if (value instanceof Double d) return String.format("%.2f", d);
        return String.valueOf(value);
    }

    /** Local lookup of the material display name for the FULL-tier synergy expansion line. */
    private static String materialName(ResourceLocation materialId) {
        return net.minecraft.client.resources.language.I18n.get(PartItem.materialTranslationKey(materialId));
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ToolAction ability) {
        ToolType tt = toolType();
        if (tt == null) return super.canPerformAction(stack, ability);
        String path = tt.id().getPath();
        switch (path) {
            case "sword" -> {
                if (ability == ToolActions.SWORD_SWEEP) return true;
            }
            case "axe" -> {
                if (ToolActions.DEFAULT_AXE_ACTIONS.contains(ability)) return true;
            }
            case "shovel" -> {
                if (ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(ability)) return true;
            }
            case "hoe" -> {
                if (ToolActions.DEFAULT_HOE_ACTIONS.contains(ability)) return true;
            }
            case "paxel" -> {
                if (ToolActions.DEFAULT_AXE_ACTIONS.contains(ability)
                        || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(ability)
                        || ToolActions.DEFAULT_HOE_ACTIONS.contains(ability)) return true;
            }
            case "kama" -> {
                // Kamas act as shears everywhere vanilla asks (sheep, pumpkins, tripwire, ...).
                if (ToolActions.DEFAULT_SHEARS_ACTIONS.contains(ability)) return true;
            }
            case "lumberaxe" -> {
                if (ToolActions.DEFAULT_AXE_ACTIONS.contains(ability)) return true;
            }
            case "excavator" -> {
                if (ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(ability)) return true;
            }
            default -> {}
        }
        return super.canPerformAction(stack, ability);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ToolComposition comp = compositionOf(stack);
        if (comp == null) return;
        ToolStats stats = ToolStats.compute(comp);
        if (stats.activeEffects.isEmpty()) return;
        Modifier.AttackContext ctx = new Modifier.AttackContext(stack, attacker, target, 0f);
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            if (r.modifier().onAttack() != null) {
                r.modifier().onAttack().onAttack(r.effect(), ctx);
            }
        }
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, net.minecraft.core.BlockPos pos, LivingEntity owner) {
        boolean superResult = super.mineBlock(stack, level, state, pos, owner);
        ToolComposition comp = compositionOf(stack);
        if (comp == null) return superResult;
        ToolStats stats = ToolStats.compute(comp);
        if (stats.activeEffects.isEmpty()) return superResult;
        net.minecraft.world.entity.player.Player player =
                owner instanceof net.minecraft.world.entity.player.Player p ? p : null;
        Modifier.BlockBreakContext ctx = new Modifier.BlockBreakContext(stack, player, level, pos, state);
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            if (r.modifier().onBreak() != null) {
                r.modifier().onBreak().onBreak(r.effect(), ctx);
            }
        }
        return superResult;
    }

    /** Translation key shared by synergy display names. */
    public static String synergyTranslationKey(ResourceLocation synergyId) {
        return Smithery.MODID + ".synergy." + synergyId.getNamespace() + "." + synergyId.getPath();
    }

    /** Instance-bound convenience wrapper for {@link #primaryTintColorFor(ItemStack)}. */
    public int primaryTintColor(ItemStack stack) {
        return primaryTintColorFor(stack);
    }

    /**
     * Returns the ARGB tint colour for {@code stack}'s primary additive material; opaque
     * white when no composition is present or the material can't be resolved. Static so
     * the bow and arrow items (which don't extend SmitheryToolItem) can share it.
     */
    public static int primaryTintColorFor(ItemStack stack) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null) return 0xFFFFFFFF;
        ToolType tt = comp.toolType();
        if (tt == null) return 0xFFFFFFFF;
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == com.soul.smithery.api.tool.DurabilityRole.ADDITIVE) {
                ResourceLocation matId = comp.slotMaterials().get(i);
                Material m = matId != null ? SmitheryAPI.MATERIALS.get(matId) : null;
                return m != null ? (m.stats().partColor() | 0xFF000000) : 0xFFFFFFFF;
            }
        }
        return 0xFFFFFFFF;
    }
}
