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
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The single Item class for all Smithery tools (Sword, Pickaxe, future Spear, ...). The ToolType
 * is fixed per item instance; the per-stack composition lives in the TOOL_COMPOSITION data
 * component and drives all stats.
 *
 * At craft time, {@link #applyComposition} computes the derived stats and writes the vanilla
 * components (max_damage, attribute_modifiers, tool) so vanilla systems handle damage, mining,
 * and attack speed without any per-frame work on our part.
 */
public class SmitheryToolItem extends Item {
    private final Identifier toolTypeId;

    public SmitheryToolItem(Properties properties, Identifier toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    public Identifier toolTypeId() { return toolTypeId; }
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    public ToolComposition compositionOf(ItemStack stack) {
        return stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
    }

    /**
     * Backwards-compat overload — no explicit registry access. Tries to resolve the current
     * server's HolderLookup.Provider from {@link net.neoforged.neoforge.server.ServerLifecycleHooks};
     * if no server is running (client-only init, creative tab preview, etc.) the lookup is null
     * and any compose actions requiring it (notably {@code apply_enchantment}) silently no-op.
     */
    public static ItemStack applyComposition(ItemStack stack, ToolComposition comp) {
        net.minecraft.core.HolderLookup.Provider lookup = null;
        try {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) lookup = server.registryAccess();
        } catch (Throwable ignored) { /* client / pre-server contexts */ }
        return applyComposition(stack, comp, lookup);
    }

    /**
     * Compute stats from {@code comp} (and any post-craft modifiers already on the stack)
     * and write them onto {@code stack} as vanilla data components. Call at craft time AND
     * any time post-craft modifiers change (e.g. after the anvil applies a new modifier) so
     * the cached vanilla attribute modifiers / durability / tool rules stay in sync.
     *
     * <h3>Enchantment handling</h3>
     * This method <em>clears</em> the stack's {@code ENCHANTMENTS} component at entry, then
     * invokes every {@link com.soul.smithery.api.modifier.Modifier.OnCompose} hook on the
     * effective modifier list (composition material grants + applied modifiers + synergies).
     * Compose actions can write fresh enchantments via the provided {@code HolderLookup.Provider}.
     * Net effect: enchantments on smithery tools are <em>always</em> driven by the modifier
     * system and recomputed on every (re)composition. Vanilla enchanting is blocked separately
     * via {@link #getEnchantmentValue()} and the anvil handler.
     *
     * @param lookup registry access for compose actions that need it (enchantment registry,
     *               attribute registry, etc.). Pass {@code null} only when no live registry
     *               access is available — affected actions will skip with no error.
     */
    public static ItemStack applyComposition(ItemStack stack, ToolComposition comp,
                                              net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup) {
        // Clear any prior enchantments — built-in smithery modifiers EMULATE enchantment
        // effects (e.g. golden_touch's bonus_drops mimics Fortune ore drops via BlockDropsEvent)
        // rather than writing the vanilla ENCHANTMENTS component. Modders who explicitly want
        // their modifier to ALSO write a vanilla enchantment (for compat with other mods that
        // inspect ENCHANTMENTS directly) can use the smithery:apply_enchantment compose action,
        // which writes here. Cleared at composition entry so any prior enchantment from a
        // removed modifier doesn't linger.
        stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);

        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(), java.util.List.of());
        ToolStats stats = ToolStats.compute(comp, applied);
        stack.set(SmitheryDataComponents.TOOL_COMPOSITION.get(), comp);

        // Arrows are stackable consumables — vanilla rejects items that are both stackable AND
        // damage-bearing, so the durability + attribute + mining/weapon-component block below
        // is skipped for arrows. Each arrow's damage / shot-count meaning lives in the per-stack
        // ToolComposition + ToolStats lookup at fire time (see SmitheryBowItem).
        boolean stackable = comp.toolType() != null && "arrow".equals(comp.toolType().id().getPath());
        if (stackable) {
            // Still fire compose hooks so any onCompose modifier on an arrow part runs.
            fireComposeHooks(stack, stats, lookup);
            return stack;
        }

        stack.set(DataComponents.MAX_DAMAGE, stats.maxDurability);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.DAMAGE, 0);

        // Attribute modifiers: attack damage + attack speed in the mainhand slot.
        // (The −1 vanilla offset on attack damage matches Sword/Pickaxe behavior.)
        var attrs = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                Math.max(0f, stats.attackDamage - 1f),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                attackSpeedFor(comp), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);

        // Tool component (mining rules) — only set when the tool type actually mines anything.
        // Pickaxe / axe / shovel / hoe → minable-with-X tag at computed speed, tier-gated by
        // incorrect_for_<tier>. Sword → SWORD_EFFICIENT (cobweb / leaves / bamboo).
        // Spear → no TOOL component (matches vanilla; spears don't mine).
        ToolType tt = comp.toolType();
        if (tt != null) {
            Tool tool = buildToolComponent(tt, stats);
            if (tool != null) stack.set(DataComponents.TOOL, tool);

            // Swords & spears are weapons: 1 durability per attack and don't disable shields.
            // Mining tools don't get this component (their attack damage comes from the tool component).
            String ttPath = tt.id().getPath();
            if ("sword".equals(ttPath) || "spear".equals(ttPath)) {
                stack.set(DataComponents.WEAPON, new Weapon(1, 0.0f));
            }
            // Spear: write the vanilla spear data-component bundle (kinetic/piercing/range/etc.)
            // so charging, run-and-stab momentum damage, piercing, stab animation, and the spear
            // damage type all route through the stock Item.use / Item.releaseUsing pipeline.
            if ("spear".equals(ttPath)) {
                applySpearComponents(stack, comp, lookup);
            }
        }

        fireComposeHooks(stack, stats, lookup);
        return stack;
    }

    /**
     * Fires every onCompose hook on the given stats list against {@code stack}. Extracted so
     * the arrow path (which skips the durability / attribute / tool component writes) still
     * triggers compose modifiers — e.g. an arrow_head material with an onCompose action would
     * otherwise silently no-op on arrows.
     */
    private static void fireComposeHooks(ItemStack stack, ToolStats stats,
                                          net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup) {
        com.soul.smithery.api.modifier.Modifier.ComposeContext composeCtx =
                new com.soul.smithery.api.modifier.Modifier.ComposeContext(stack, lookup);
        for (ToolStats.ResolvedEffect r : stats.composeEffects) {
            com.soul.smithery.api.modifier.Modifier mod =
                    com.soul.smithery.api.SmitheryAPI.MODIFIERS.get(r.effect().modifierId());
            if (mod == null || mod.onCompose() == null) continue;
            try {
                mod.onCompose().apply(r.effect(), composeCtx);
            } catch (Throwable t) {
                com.soul.smithery.Smithery.LOGGER.error(
                        "Modifier {} onCompose failed: {}", r.effect().modifierId(), t.toString());
            }
        }
    }

    // ---- Vanilla enchanting blocked at two layers ----
    //
    // In 1.21+ enchantability is the {@code DataComponents.ENCHANTABLE} component (set via
    // {@code Properties.enchantable(int)}). Smithery tools NEVER set it, so vanilla enchanting
    // tables already refuse to enchant them. The anvil-book path is blocked separately by
    // {@link com.soul.smithery.event.AnvilModifierHandler}, which sets the output to empty
    // when an enchanted book sits in the right slot opposite a smithery tool. Together these
    // ensure enchantments only land via the smithery modifier system's
    // {@code smithery:apply_enchantment} on_compose action.

    // ---- Modifier slot accounting ----
    //
    // Each part's material grants N slots for that part type (set in MaterialStats.modifierSlots).
    // The tool's total slot count is the sum across all slots in the composition. Post-craft
    // modifier application consumes one slot per modifier applied (stored in APPLIED_MODIFIERS).

    /** Total modifier slots a fully-composed tool offers. Derives entirely from the composition. */
    public static int totalModifierSlots(ToolComposition comp) {
        ToolType tt = comp.toolType();
        if (tt == null) return 0;
        int sum = 0;
        List<ToolType.Slot> slots = tt.slots();
        java.util.List<Identifier> matIds = comp.slotMaterials();
        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(matIds.get(i));
            if (m == null) continue;
            sum += m.stats().modifierSlotsFor(slots.get(i).partType());
        }
        return sum;
    }

    /**
     * Modifier slots consumed by the post-craft modifier list on this stack. Each modifier
     * entry counts as {@code level} slots — so a Haste II application uses 2 slots, Haste III
     * uses 3, etc. Single-level modifiers (most) default to level 1 = 1 slot.
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

    /** Standard Roman numeral formatter for tooltip level rendering. */
    /** Indented gray bullet for the Stats block — shared with PartItem's per-tool sections. */
    public static net.minecraft.network.chat.MutableComponent statLine(Component body) {
        return Component.literal(" ▸ ").append(body).withStyle(ChatFormatting.DARK_GRAY);
    }

    public static String toRoman(int n) {
        if (n <= 0) return String.valueOf(n);
        if (n >= 4000) return String.valueOf(n);    // not worth wrestling Romans for absurd numbers
        StringBuilder sb = new StringBuilder();
        int[] vals = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < vals.length; i++) {
            while (n >= vals[i]) { sb.append(syms[i]); n -= vals[i]; }
        }
        return sb.toString();
    }

    /** Free modifier slots remaining on this stack (clamped to ≥ 0). */
    public static int freeModifierSlots(ItemStack stack) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null) return 0;
        return Math.max(0, totalModifierSlots(comp) - appliedModifierCount(stack));
    }

    private static float attackSpeedFor(ToolComposition comp) {
        // Simple model: matches vanilla pacing — sword fastest, hoe medium-fast, pickaxe/shovel
        // slower, axe slowest. Spear is computed from its kinetic attackDuration so it lines up
        // with the SwingAnimation duration written by applySpearComponents.
        ToolType tt = comp.toolType();
        if (tt == null) return -2.4f;
        String path = tt.id().getPath();
        return switch (path) {
            case "sword"   -> -2.4f;
            case "pickaxe" -> -2.8f;
            case "axe"     -> -3.2f;
            case "shovel"  -> -3.0f;
            case "hoe"     -> -3.0f;
            case "spear"   -> 1.0f / spearAttackDuration(headHarvestLevel(comp)) - 4.0f;
            default        -> -2.6f;
        };
    }

    /** Primary-additive-slot harvest level (the spear head). Falls back to iron-tier (2). */
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

    /**
     * Vanilla spear curve, parameterized by tier 0..4 (wooden..netherite):
     *   t=0 → 0.65 (wooden), t=4 → 1.15 (netherite). Linear interpolation.
     * Higher tiers swing faster (lower duration = higher attacks/sec) and pack more momentum
     * damage. Smithery uses the head material's harvest level as the tier proxy.
     */
    private static float spearAttackDuration(int harvestLevel) {
        float t = Mth.clamp(harvestLevel, 0, 4) / 4.0f;
        return 0.65f + t * 0.5f;
    }

    /**
     * Writes the vanilla spear data-component bundle onto {@code stack}. Constants are interpolated
     * across the wooden→netherite range using the head material's harvest level as the tier proxy,
     * matching {@code Item.Properties.spear(...)} from {@code Items.java}.
     *
     * Components written:
     *   - KINETIC_WEAPON     — charge / dismount / knockback / damage thresholds
     *   - PIERCING_WEAPON    — single thrust pierces multiple entities
     *   - ATTACK_RANGE       — extended reach (2.0..4.5 player, 2.0..6.5 creative)
     *   - MINIMUM_ATTACK_CHARGE — 1.0 (must fully charge)
     *   - SWING_ANIMATION    — STAB anim, duration matched to attackDuration
     *   - USE_EFFECTS        — sprint locked, slow-walk while charging
     *   - DAMAGE_TYPE        — minecraft:spear (only if {@code lookup} is available)
     *
     * Sound profile is wood-flavored for harvest-level 0 (wooden tier), generic spear sounds
     * otherwise — same split vanilla uses.
     */
    private static void applySpearComponents(ItemStack stack, ToolComposition comp,
                                              net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup) {
        int hl = headHarvestLevel(comp);
        float t = Mth.clamp(hl, 0, 4) / 4.0f;

        float attackDuration     = spearAttackDuration(hl);
        float damageMultiplier   = 0.7f  + t * 0.5f;        // wooden 0.70 → netherite 1.20
        float delay              = 0.75f - t * 0.35f;       // wooden 0.75 → netherite 0.40
        float dismountTime       = 5.0f  - t * 2.5f;        // wooden 5.0  → netherite 2.5
        float dismountThreshold  = 14.0f - t * 5.0f;        // wooden 14   → netherite 9
        float knockbackTime      = 10.0f - t * 4.5f;        // wooden 10   → netherite 5.5
        float knockbackThreshold = 5.1f;                    // vanilla constant across tiers
        float damageTime         = 15.0f - t * 6.25f;       // wooden 15   → netherite 8.75
        float damageThreshold    = 4.6f;                    // vanilla constant across tiers

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

        // DAMAGE_TYPE = minecraft:spear. Requires a HolderLookup.Provider — when called from a
        // client-only preview path (creative tab icon before server start) the lookup is null,
        // so we skip; the spear still functions, just falls back to generic player_attack on hit.
        if (lookup != null) {
            try {
                Holder<DamageType> spearDamageType =
                        lookup.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SPEAR);
                stack.set(DataComponents.DAMAGE_TYPE, spearDamageType);
            } catch (Throwable t2) {
                // Damage type registry not yet populated — non-fatal, log once and continue.
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
            case "sword" -> {
                // Cobweb / leaves / bamboo, at the tool's mining speed.
                rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.SWORD_EFFICIENT), stats.miningSpeed));
            }
            // "spear" and any other tool type with no mining rules: no TOOL component.
            // Vanilla spears intentionally have no TOOL component — they don't mine blocks at all.
            default -> { return null; }
        }
        return new Tool(rules, 1.0f, 2, true);
    }

    /**
     * Blocks that are *too tough* for our harvest level: their incorrect_for_<tier> tag.
     * Returns null if the level meets all vanilla requirements.
     */
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

    // ---- Tooltip ----

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = compositionOf(stack);
        ToolType tt = toolType();
        if (comp == null || !comp.isValid() || tt == null) {
            return Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId));
        }
        // "<primary-additive-material> <tool>" — same shape as the part naming, e.g. "Iron Sword"
        Identifier primaryMat = primaryAdditiveMaterial(comp);
        Component matName = primaryMat != null
                ? Component.translatable(PartItem.materialTranslationKey(primaryMat))
                : Component.literal("");
        return Component.translatable("item." + Smithery.MODID + ".part_combo",
                matName,
                Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId)));
    }

    private Identifier primaryAdditiveMaterial(ToolComposition comp) {
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

        // ---- Parts breakdown ----
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.parts")
                .withStyle(ChatFormatting.GRAY));
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot slot = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
            if (m == null) continue;
            PartType pt = slot.partType();
            Component matName = Component.translatable(PartItem.materialTranslationKey(m.id()));
            Component partName = Component.translatable(PartItem.partTranslationKey(pt.id()));
            tooltip.accept(Component.literal(" • ")
                    .append(matName).append(Component.literal(" "))
                    .append(partName)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // ---- Stats block ----
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("%.1f", stats.attackDamage))));
        tooltip.accept(statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.mining_speed",
                String.format("%.1f", stats.miningSpeed))));
        tooltip.accept(statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".part.harvest_level", stats.harvestLevel)));

        // ---- Modifier slots (post-craft application capacity) ----
        int totalSlots = totalModifierSlots(comp);
        int usedSlots = applied.size();
        int freeSlots = Math.max(0, totalSlots - usedSlots);
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.modifier_slots",
                freeSlots, totalSlots).withStyle(ChatFormatting.GRAY));

        // ---- Modifiers (all of them — passive, active, and compose-only alike) ----
        if (!stats.allEffects.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.modifiers")
                    .withStyle(ChatFormatting.GRAY));
            // Shift detection: static Screen.hasShiftDown() removed in 1.21+. Query the window
            // directly via InputConstants. Safe client-side only — appendHoverText never runs
            // server-side even though the Item class loads on both.
            com.mojang.blaze3d.platform.Window win =
                    net.minecraft.client.Minecraft.getInstance().getWindow();
            boolean shiftDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                            win, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                    || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                            win, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
            for (ToolStats.ResolvedEffect r : stats.allEffects) {
                int level = r.effect().paramInt("level", 1);
                MutableComponent line = Component.literal(" • ")
                        .append(Component.translatable(PartItem.modifierTranslationKey(r.effect().modifierId()))
                                .withStyle(ChatFormatting.AQUA));
                // Render level as Roman numeral when > 1 (matches vanilla enchantment style).
                if (level > 1) {
                    line.append(Component.literal(" " + toRoman(level)).withStyle(ChatFormatting.AQUA));
                }
                tooltip.accept(line.withStyle(ChatFormatting.DARK_GRAY));
                // Shift held → indented italic description line. Description lang key is
                // "<modifier_key>.description". Missing keys are gracefully skipped via I18n.exists.
                if (shiftDown) {
                    String descKey = PartItem.modifierDescriptionKey(r.effect().modifierId());
                    if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
                        tooltip.accept(Component.literal("     ")
                                .append(Component.translatable(descKey))
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    }
                }
            }
            if (!shiftDown) {
                tooltip.accept(Component.translatable(
                        "tooltip." + Smithery.MODID + ".tool.shift_for_descriptions")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }

        // ---- In-progress modifier accumulation (from partial anvil applications) ----
        // Value is the raw count of source items contributed so far. We don't know the target
        // threshold here without picking a "primary" source; show just the contributed count,
        // which matches the anvil's actual consumption.
        java.util.Map<net.minecraft.resources.Identifier, Integer> progress =
                stack.getOrDefault(SmitheryDataComponents.MODIFIER_PROGRESS.get(),
                        java.util.Map.<net.minecraft.resources.Identifier, Integer>of());
        if (!progress.isEmpty()) {
            for (var entry : progress.entrySet()) {
                tooltip.accept(Component.literal(" ⌛ ")
                        .append(Component.translatable(PartItem.modifierTranslationKey(entry.getKey()))
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(": " + entry.getValue() + " contributed")
                                .withStyle(ChatFormatting.GRAY))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // ---- Synergies ----
        if (!stats.activeSynergies.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.synergies")
                    .withStyle(ChatFormatting.GRAY));
            for (SynergyDefinition s : stats.activeSynergies) {
                MutableComponent line = Component.literal(" ✦ ")
                        .append(Component.translatable(synergyTranslationKey(s.id()))
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.accept(line.withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    // ---- Vanilla tool-type parity ----

    /**
     * Smithery tools declare the same ItemAbilities as their vanilla counterparts so
     * vanilla mechanics (sword sweep, etc.) route through them correctly. Pickaxe mining
     * doesn't go through this path — it's driven by the minecraft:tool data component set
     * in applyComposition().
     */
    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        ToolType tt = toolType();
        if (tt == null) return super.canPerformAction(stack, ability);
        String path = tt.id().getPath();
        switch (path) {
            case "sword" -> {
                if (ability == ItemAbilities.SWORD_SWEEP) return true;
            }
            case "axe" -> {
                if (ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability)) return true;
            }
            case "shovel" -> {
                if (ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability)) return true;
            }
            case "hoe" -> {
                if (ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability)) return true;
            }
            default -> {}
        }
        return super.canPerformAction(stack, ability);
    }

    // ---- Active modifier hooks ----

    /**
     * Fires onAttack modifier callbacks. Routed through {@code Item.hurtEnemy} (called per
     * damaged entity, server-side) instead of {@code postHurtEnemy} so spears' charged stab
     * attack — which goes through {@code LivingEntity.stabAttack} → {@code Item.hurtEnemy}
     * and never touches {@code postHurtEnemy} — also fires its on-hit modifiers (corrosive,
     * verdant, lunge, …).
     *
     * Path coverage:
     *   LMB melee → {@code Player.attack} → {@code Player.itemAttackInteraction} → here.
     *   Charged stab (right-click hold + STAB packet) → {@code PiercingWeapon.attack} →
     *     {@code LivingEntity.stabAttack} → here, ONCE PER PIERCED ENTITY.
     *
     * Per-pierce semantics are correct for hit-driven effects (corrosive, verdant — each pierced
     * entity rolls its own chance). Modifiers that want once-per-attack semantics (lunge in
     * particular) gate themselves via a per-player cooldown — see {@code SmitheryModifiers.LUNGE}.
     */
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ToolComposition comp = compositionOf(stack);
        if (comp == null) return;
        ToolStats stats = ToolStats.compute(comp);
        if (stats.activeEffects.isEmpty()) return;
        // We don't know the exact damage dealt here cheaply; pass through 0f if unavailable.
        Modifier.AttackContext ctx = new Modifier.AttackContext(stack, attacker, target, 0f);
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            if (r.modifier().onAttack() != null) {
                r.modifier().onAttack().onAttack(r.effect(), ctx);
            }
        }
    }

    /** Vanilla calls this after a block is broken with this tool. Fires all active onBreak modifier callbacks. */
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

    public static String synergyTranslationKey(Identifier synergyId) {
        return Smithery.MODID + ".synergy." + synergyId.getNamespace() + "." + synergyId.getPath();
    }

    /** Get the material color used for primary tinting (drives ToolPrimaryMaterialTintSource). */
    public int primaryTintColor(ItemStack stack) {
        return primaryTintColorFor(stack);
    }

    /**
     * Static variant — derives the tool type from the {@link ToolComposition} itself rather
     * than the calling Item instance, so it works for {@code SmitheryBowItem} and
     * {@code SmitheryArrowItem} too (neither extends {@code SmitheryToolItem}).
     *
     * Returns opaque white when no composition is present, the composition is invalid, or
     * the primary additive material has been removed from the registry.
     */
    public static int primaryTintColorFor(ItemStack stack) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null) return 0xFFFFFFFF;
        ToolType tt = comp.toolType();
        if (tt == null) return 0xFFFFFFFF;
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == com.soul.smithery.api.tool.DurabilityRole.ADDITIVE) {
                Identifier matId = comp.slotMaterials().get(i);
                Material m = matId != null ? SmitheryAPI.MATERIALS.get(matId) : null;
                return m != null ? (m.stats().partColor() | 0xFF000000) : 0xFFFFFFFF;
            }
        }
        return 0xFFFFFFFF;
    }
}
