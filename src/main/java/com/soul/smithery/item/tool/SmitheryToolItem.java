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
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
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

        // Tool component: mining rules. For Pickaxe-type tools, mineable_with_pickaxe blocks get
        // the computed mining speed and become drop-correct. For Sword-type tools, just sets up
        // cobweb / leaves mining like vanilla swords.
        ToolType tt = comp.toolType();
        if (tt != null) {
            stack.set(DataComponents.TOOL, buildToolComponent(tt, stats));
            // Swords are weapons: they take 1 durability per attack and don't disable shields.
            // Pickaxes don't get this component (their attack damage is taken from the tool component).
            if ("sword".equals(tt.id().getPath())) {
                stack.set(DataComponents.WEAPON, new Weapon(1, 0.0f));
            }
        }

        // Compose hooks — fire after vanilla components are written so actions can read /
        // mutate the finalized stack (e.g. apply_enchantment writes to ENCHANTMENTS).
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
        return stack;
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
    private static String toRoman(int n) {
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
        // Simple model: sword swings at -2.4 (vanilla sword pace); pickaxe at -2.8.
        ToolType tt = comp.toolType();
        if (tt == null) return -2.4f;
        String path = tt.id().getPath();
        return switch (path) {
            case "sword"   -> -2.4f;
            case "pickaxe" -> -2.8f;
            default        -> -2.6f;
        };
    }

    private static Tool buildToolComponent(ToolType tt, ToolStats stats) {
        List<Tool.Rule> rules = new ArrayList<>();
        String path = tt.id().getPath();
        if ("pickaxe".equals(path)) {
            HolderSet<Block> minable = blockTag(BlockTags.MINEABLE_WITH_PICKAXE);
            HolderSet<Block> incorrect = incorrectForTier(stats.harvestLevel);
            if (incorrect != null) rules.add(new Tool.Rule(incorrect, Optional.empty(), Optional.of(false)));
            rules.add(Tool.Rule.minesAndDrops(minable, stats.miningSpeed));
        } else if ("sword".equals(path)) {
            // Swords mine cobweb at sword speed; leaves at 1.5; bamboo plant at 1.0.
            rules.add(Tool.Rule.minesAndDrops(blockTag(BlockTags.SWORD_EFFICIENT), stats.miningSpeed));
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

        // ---- Harvest level ----
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".part.harvest_level",
                stats.harvestLevel).withStyle(ChatFormatting.GRAY));

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
        if ("sword".equals(path) && ability == ItemAbilities.SWORD_SWEEP) return true;
        return super.canPerformAction(stack, ability);
    }

    // ---- Active modifier hooks ----

    /** Vanilla calls this after damage is applied through this item. Fires all active onAttack modifier callbacks. */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
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
        ToolComposition comp = compositionOf(stack);
        if (comp == null) return 0xFFFFFFFF;
        Identifier primary = primaryAdditiveMaterial(comp);
        Material m = primary != null ? SmitheryAPI.MATERIALS.get(primary) : null;
        return m != null ? m.stats().partColor() : 0xFFFFFFFF;
    }
}
