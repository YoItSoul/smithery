package com.soul.smithery.item.tool;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.SmitheryTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Single item class for every smithery tool (sword, pickaxe, axe, shovel, hoe, spear).
 * The ToolType is fixed per item instance; per-stack composition lives in the stack's
 * {@code tool_composition} NBT (see {@link SmitheryToolData}) and drives all stats.
 * {@link #applyComposition} persists the derived durability; damage, mining and attack
 * speed are served live through the stack-sensitive item overrides
 * ({@link #getAttributeModifiers}, {@link #getDestroySpeed}, {@link #isCorrectToolForDrops},
 * {@link #getMaxDamage}) so datapack material edits reach existing tools.
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

    /** Reads the {@link ToolComposition} from the given stack's NBT (may be null). */
    public ToolComposition compositionOf(ItemStack stack) {
        return SmitheryToolData.getComposition(stack);
    }

    /**
     * Computes stats from {@code comp} plus any post-craft modifiers already on the
     * stack, persists the composition and derived durability to NBT, then fires every
     * {@link Modifier.OnCompose} hook on the effective effect list. Clears any prior
     * enchantments so smithery modifiers fully own enchantment state.
     *
     * <p>Prefer {@link ToolCompositions#apply} unless the stack is known to be a
     * non-armor tool — it dispatches by item family and can resolve {@code lookup}.
     *
     * @param lookup registry access for compose actions; pass null only when no live
     *               registry is available, in which case affected actions skip silently
     */
    public static ItemStack applyComposition(ItemStack stack, ToolComposition comp,
                                             HolderLookup.@Nullable Provider lookup) {
        stack.removeTagKey("Enchantments");

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        int wearDamage = stack.getDamageValue();
        int wearMax = stack.getMaxDamage();
        float missing = wearMax > 0 ? (float) wearDamage / wearMax : 0f;
        ToolStats stats = ToolStats.compute(comp, applied, missing);
        SmitheryToolData.setComposition(stack, comp);

        String composePath = comp.toolType() != null ? comp.toolType().id().getPath() : "";
        boolean stackable = "arrow".equals(composePath) || "shuriken".equals(composePath);
        if (stackable) {
            ToolCompositions.fireComposeHooks(stack, stats, lookup);
            return stack;
        }

        // Preserve wear across recomposition (anvil modifiers, stat recomputes) — resetting to
        // 0 here made every modifier application a free full repair. Written straight to NBT so
        // the durability-scaled recompose in setDamage cannot recurse.
        int priorDamage = stack.getDamageValue();
        SmitheryToolData.setMaxDurability(stack, stats.maxDurability);
        stack.getOrCreateTag().putInt("Damage", Math.min(priorDamage, stats.maxDurability - 1));

        ToolCompositions.fireComposeHooks(stack, stats, lookup);
        return stack;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serves the composed durability persisted by {@link #applyComposition}; uncomposed
     * stacks fall back to the item default.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return SmitheryToolData.getMaxDurability(stack, super.getMaxDamage(stack));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serves attack damage and attack speed computed live from the stack's composition,
     * so datapack material edits reach existing tools without recomposing.
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        Multimap<Attribute, AttributeModifier> composed = composedMeleeAttributes(stack);
        return composed != null ? composed : super.getAttributeModifiers(slot, stack);
    }

    /**
     * Builds the mainhand attribute multimap (attack damage, attack speed, spear reach) from
     * the stack's composition, or null when the stack carries no valid composition. Shared
     * with {@link SmitheryTridentItem}, which extends the vanilla trident instead of this class.
     */
    static @Nullable Multimap<Attribute, AttributeModifier> composedMeleeAttributes(ItemStack stack) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null || !comp.isValid()) return null;

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        int wearMax = stack.getMaxDamage();
        float missing = wearMax > 0 ? (float) stack.getDamageValue() / wearMax : 0f;
        ToolStats stats = ToolStats.compute(comp, applied, missing);

        float scaledDamage = comp.toolType() != null
                ? stats.attackDamage * damageScaleFor(comp.toolType())
                : stats.attackDamage;

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Tool modifier",
                        Math.max(0f, scaledDamage - 1f), AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED,
                new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Tool modifier",
                        attackSpeedFor(comp), AttributeModifier.Operation.ADDITION));
        if ("spear".equals(comp.toolType() != null ? comp.toolType().id().getPath() : "")) {
            // 1.20.1 has no vanilla spear mechanics; the extended thrust is expressed as
            // bonus entity reach instead of the newer kinetic/piercing weapon data.
            builder.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(SPEAR_REACH_UUID, "Spear reach",
                            1.5d, AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    /** Stable UUID for the spear's bonus-reach attribute modifier. */
    private static final java.util.UUID SPEAR_REACH_UUID =
            java.util.UUID.fromString("d3f7c1d4-0f2f-4c1a-8f5e-6a2b9c4e7d10");

    /**
     * Re-stamps stats whenever durability changes IF a durability-scaled modifier
     * (Stonebound-style) is present — their mining/damage contributions depend on wear.
     * Safe against recursion: applyComposition writes the damage NBT directly and
     * never routes back through here.
     */
    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, damage);
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null || !comp.isValid()) return;
        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
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
        List<ResourceLocation> matIds = comp.slotMaterials();
        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(matIds.get(i));
            if (m == null) continue;
            sum += m.stats().modifierSlotsFor(slots.get(i).partType());
        }
        return sum;
    }

    /**
     * Returns the modifier slots consumed by the stack's applied-modifier list, where
     * each entry counts as max(1, its level parameter).
     */
    public static int appliedModifierCount(ItemStack stack) {
        int total = 0;
        for (ModifierEffect e : SmitheryToolData.getAppliedModifiers(stack)) {
            total += Math.max(1, e.paramInt("level", 1));
        }
        return total;
    }

    /**
     * Indented gray bullet used by the tool and part tooltip stat lines. Shared with
     * {@link PartItem}'s per-tool tooltip sections.
     */
    public static MutableComponent statLine(Component body) {
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
        ToolComposition comp = SmitheryToolData.getComposition(stack);
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
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
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

    /**
     * One mining rule for {@link #getDestroySpeed}/{@link #isCorrectToolForDrops}: blocks in
     * {@code tag} mine at {@code speedFactor} × the composed mining speed.
     */
    private record MiningRule(TagKey<net.minecraft.world.level.block.Block> tag, float speedFactor) {}

    /**
     * The per-tool-type mining rules — the 1.20.1 stand-in for the newer TOOL data component.
     * Speed factors mirror the tool identities: hammers/excavators/lumberaxes trade per-block
     * speed for their area or tree-felling spread.
     */
    private static List<MiningRule> miningRulesFor(String toolTypePath) {
        return switch (toolTypePath) {
            case "pickaxe"       -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_PICKAXE, 1.0f));
            case "axe"           -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_AXE, 1.0f));
            case "shovel"        -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_SHOVEL, 1.0f));
            case "hoe"           -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_HOE, 1.0f));
            case "sword", "broadsword", "rapier", "cleaver", "battlesign"
                                 -> List.of(new MiningRule(BlockTags.SWORD_EFFICIENT, 1.0f));
            case "lumberaxe"     -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_AXE, 0.7f));
            case "excavator"     -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_SHOVEL, 0.6f));
            case "kama"          -> List.of(
                    new MiningRule(BlockTags.SWORD_EFFICIENT, 1.0f),
                    new MiningRule(BlockTags.LEAVES, 1.0f),
                    new MiningRule(BlockTags.WOOL, 1.0f));
            case "paxel"         -> List.of(
                    new MiningRule(BlockTags.MINEABLE_WITH_PICKAXE, 1.0f),
                    new MiningRule(BlockTags.MINEABLE_WITH_AXE, 1.0f),
                    new MiningRule(BlockTags.MINEABLE_WITH_SHOVEL, 1.0f),
                    new MiningRule(BlockTags.MINEABLE_WITH_HOE, 1.0f));
            case "mining_hammer" -> List.of(new MiningRule(BlockTags.MINEABLE_WITH_PICKAXE, 0.6f));
            default              -> List.of();
        };
    }

    /** True when this tool family respects harvest tiers (sword-family blocks always drop). */
    private static boolean usesHarvestTier(String toolTypePath) {
        return switch (toolTypePath) {
            case "sword", "broadsword", "rapier", "cleaver", "battlesign", "kama" -> false;
            default -> true;
        };
    }

    /** Maps the composed harvest level onto the vanilla tier ladder for tier-gating checks. */
    private static Tier tierFor(int harvestLevel) {
        return switch (Mth.clamp(harvestLevel, 0, 4)) {
            case 0 -> Tiers.WOOD;
            case 1 -> Tiers.STONE;
            case 2 -> Tiers.IRON;
            case 3 -> Tiers.DIAMOND;
            default -> Tiers.NETHERITE;
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serves the composed mining speed against this tool type's mineable tags.
     */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        ToolType tt = comp != null ? comp.toolType() : null;
        if (comp == null || tt == null) return super.getDestroySpeed(stack, state);
        for (MiningRule rule : miningRulesFor(tt.id().getPath())) {
            if (state.is(rule.tag())) {
                ToolStats stats = ToolStats.compute(comp, SmitheryToolData.getAppliedModifiers(stack));
                return Math.max(1.0f, stats.miningSpeed * rule.speedFactor());
            }
        }
        return super.getDestroySpeed(stack, state);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A block drops when it matches one of this tool type's mineable tags and, for
     * tier-gated families, the composed harvest level clears the block's required tier.
     */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        ToolType tt = comp != null ? comp.toolType() : null;
        if (comp == null || tt == null) return super.isCorrectToolForDrops(stack, state);
        String path = tt.id().getPath();
        for (MiningRule rule : miningRulesFor(path)) {
            if (!state.is(rule.tag())) continue;
            if (!usesHarvestTier(path)) return true;
            ToolStats stats = ToolStats.compute(comp, SmitheryToolData.getAppliedModifiers(stack));
            return TierSortingRegistry.isCorrectTierForDrops(tierFor(stats.harvestLevel), state);
        }
        return super.isCorrectToolForDrops(stack, state);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The battlesign partially blocks while raised, shield-style: {@code use} starts the
     * blocking pose and {@code ToolModifierEventRouter} scales the blocked damage down to the
     * battlesign's 60% reduction (a vanilla shield remains the dedicated full blocker).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (isBattlesign()) {
            ItemStack stack = player.getItemInHand(hand);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return super.use(level, player, hand);
    }

    /** {@inheritDoc} The battlesign holds the shield blocking pose. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isBattlesign() ? UseAnim.BLOCK : super.getUseAnimation(stack);
    }

    /** {@inheritDoc} The battlesign can be held raised indefinitely, like a shield. */
    @Override
    public int getUseDuration(ItemStack stack) {
        return isBattlesign() ? 72000 : super.getUseDuration(stack);
    }

    private boolean isBattlesign() {
        ToolType tt = toolType();
        return tt != null && "battlesign".equals(tt.id().getPath());
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

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (tier == SmitheryTooltips.Tier.BASIC) {
            SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, level, lines, flag);
            return;
        }

        // DETAIL and FULL ---------------------------------------------------
        tooltip.accept(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("%.1f", stats.attackDamage))));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.mining_speed",
                String.format("%.1f", stats.miningSpeed))));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".part.harvest_level", stats.harvestLevel)));

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
            Component matName = Component.translatable(PartItem.materialTranslationKey(m.id()));
            Component partName = Component.translatable(PartItem.partTranslationKey(pt.id()));
            tooltip.accept(SmitheryTooltips.bullet(
                    Component.empty().append(matName).append(Component.literal(" ")).append(partName)));
        }

        int totalSlots = totalModifierSlots(comp);
        int usedSlots = applied.size();

        if (!stats.allEffects.isEmpty() || totalSlots > 0) {
            tooltip.accept(SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".section.modifiers_count",
                            usedSlots, totalSlots)));
        }
        for (ToolStats.ResolvedEffect r : stats.allEffects) {
            int effectLevel = r.effect().paramInt("level", 1);
            MutableComponent line = Component.empty()
                    .append(Component.translatable(PartItem.modifierTranslationKey(r.effect().modifierId()))
                            .withStyle(ChatFormatting.AQUA));
            if (effectLevel > 1) {
                line.append(Component.literal(" " + toRoman(effectLevel)).withStyle(ChatFormatting.AQUA));
            }
            tooltip.accept(SmitheryTooltips.bullet(line));

            String descKey = PartItem.modifierDescriptionKey(r.effect().modifierId());
            if (I18n.exists(descKey)) {
                tooltip.accept(SmitheryTooltips.subLine(
                        SmitheryTooltips.description(Component.translatable(descKey))));
            }

            if (tier == SmitheryTooltips.Tier.FULL) {
                appendModifierFullDetails(tooltip, r.effect(), r.modifier());
            }
        }

        if (!stats.activeSynergies.isEmpty()) {
            tooltip.accept(SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".tool.synergies")));
            for (SynergyDefinition s : stats.activeSynergies) {
                tooltip.accept(SmitheryTooltips.synergyBullet(
                        Component.translatable(synergyTranslationKey(s.id()))
                                .withStyle(ChatFormatting.LIGHT_PURPLE)));
                if (tier == SmitheryTooltips.Tier.FULL) {
                    tooltip.accept(SmitheryTooltips.subLine(
                            SmitheryTooltips.description(Component.literal(
                                    materialName(s.materialA()) + " + " + materialName(s.materialB())))));
                }
            }
        }

        java.util.Map<ResourceLocation, Integer> progress = SmitheryToolData.getModifierProgress(stack);
        if (!progress.isEmpty()) {
            tooltip.accept(SmitheryTooltips.sectionHeader(
                    Component.translatable("tooltip." + Smithery.MODID + ".section.progress")));
            for (var entry : progress.entrySet()) {
                tooltip.accept(SmitheryTooltips.bullet(
                        Component.translatable("tooltip." + Smithery.MODID + ".progress.line",
                                Component.translatable(PartItem.modifierTranslationKey(entry.getKey()))
                                        .withStyle(ChatFormatting.YELLOW),
                                entry.getValue())));
            }
        }

        SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, level, lines, flag);
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
                                                  ModifierEffect effect,
                                                  Modifier modifier) {
        if (!effect.params().isEmpty()) {
            for (var p : effect.params().entrySet()) {
                tooltip.accept(SmitheryTooltips.subLine(
                        Component.translatable("tooltip." + Smithery.MODID + ".modifier.param_line",
                                p.getKey(), formatParamValue(p.getValue()))
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        if (modifier == null) return;
        if (modifier.maxLevel() > 1) {
            int currentLevel = effect.paramInt("level", 1);
            tooltip.accept(SmitheryTooltips.subLine(
                    Component.translatable("tooltip." + Smithery.MODID + ".modifier.level_info",
                            currentLevel, modifier.maxLevel(),
                            String.format("%.2f", modifier.levelCostScaling()))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (Math.abs(modifier.durabilityMultiplier() - 1.0f) > 1e-3) {
            tooltip.accept(SmitheryTooltips.subLine(
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
        return I18n.get(PartItem.materialTranslationKey(materialId));
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        ToolType tt = toolType();
        if (tt == null) return super.canPerformAction(stack, action);
        String path = tt.id().getPath();
        switch (path) {
            case "sword" -> {
                if (action == ToolActions.SWORD_SWEEP) return true;
            }
            case "axe" -> {
                if (ToolActions.DEFAULT_AXE_ACTIONS.contains(action)) return true;
            }
            case "shovel" -> {
                if (ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action)) return true;
            }
            case "hoe" -> {
                if (ToolActions.DEFAULT_HOE_ACTIONS.contains(action)) return true;
            }
            case "paxel" -> {
                if (ToolActions.DEFAULT_AXE_ACTIONS.contains(action)
                        || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action)
                        || ToolActions.DEFAULT_HOE_ACTIONS.contains(action)) return true;
            }
            case "kama" -> {
                // Kamas act as shears everywhere vanilla asks (sheep, pumpkins, tripwire, ...).
                if (ToolActions.DEFAULT_SHEARS_ACTIONS.contains(action)) return true;
            }
            case "lumberaxe" -> {
                if (ToolActions.DEFAULT_AXE_ACTIONS.contains(action)) return true;
            }
            case "excavator" -> {
                if (ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action)) return true;
            }
            case "battlesign" -> {
                // Shield-style blocking pose; damage reduction is scaled in the event router.
                if (action == ToolActions.SHIELD_BLOCK) return true;
            }
            default -> {}
        }
        return super.canPerformAction(stack, action);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        ToolComposition comp = compositionOf(stack);
        if (comp == null) return true;
        ToolStats stats = ToolStats.compute(comp);
        if (stats.activeEffects.isEmpty()) return true;
        Modifier.AttackContext ctx = new Modifier.AttackContext(stack, attacker, target, 0f);
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            if (r.modifier().onAttack() != null) {
                r.modifier().onAttack().onAttack(r.effect(), ctx);
            }
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0f) {
            // Mirrors the pre-port TOOL data of two durability per mined block.
            stack.hurtAndBreak(2, owner, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        ToolComposition comp = compositionOf(stack);
        if (comp == null) return true;
        ToolStats stats = ToolStats.compute(comp);
        if (stats.activeEffects.isEmpty()) return true;
        Player player = owner instanceof Player p ? p : null;
        Modifier.BlockBreakContext ctx = new Modifier.BlockBreakContext(stack, player, level, pos, state);
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            if (r.modifier().onBreak() != null) {
                r.modifier().onBreak().onBreak(r.effect(), ctx);
            }
        }
        return true;
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
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null) return 0xFFFFFFFF;
        ToolType tt = comp.toolType();
        if (tt == null) return 0xFFFFFFFF;
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                ResourceLocation matId = comp.slotMaterials().get(i);
                Material m = matId != null ? SmitheryAPI.MATERIALS.get(matId) : null;
                return m != null ? (m.stats().partColor() | 0xFF000000) : 0xFFFFFFFF;
            }
        }
        return 0xFFFFFFFF;
    }
}
