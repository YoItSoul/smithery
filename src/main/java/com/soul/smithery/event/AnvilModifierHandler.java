package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anvil hook for applying post-craft modifiers to Smithery tools, with Tinkers'-Construct-style
 * partial contribution.
 *
 * <h3>How application works</h3>
 * Each anvil interaction consumes up to the source items needed to fill the current level
 * threshold. Progress is stored on the tool as the raw item count in the {@code MODIFIER_PROGRESS}
 * data component, so it's directly readable ("64/90 lapis contributed"). When the count reaches
 * the source's level cost, the level commits and the progress entry clears.
 *
 * <h4>Example: Lapis Blessing level 1 (cost 90 lapis)</h4>
 * <ol>
 *   <li>Player puts tool + 64 lapis (one stack) → anvil consumes 64, progress = 64/90, tool
 *       tooltip shows {@code ⌛ Lapis Blessing: 64/90}.</li>
 *   <li>Player puts tool (with progress) + 30 lapis → consumes 26 (just enough to hit 90),
 *       level commits at 1, progress entry cleared, 4 lapis stay in the right slot.</li>
 *   <li>For level 2 (cost 135): Repeat the same flow against the higher threshold.</li>
 * </ol>
 *
 * <h4>Mixed-source caveat</h4>
 * Progress is stored as a raw item count; the threshold is whatever the CURRENT source defines.
 * Mixing sources within one level (e.g. start with lapis dust, then drop in lapis blocks) is
 * "buyer beware" math — the count carries but the threshold flips. Stick to one source per
 * level for predictable progress; the math just works.
 *
 * <h4>Single-shot modifiers (max_level=1, cost=1)</h4>
 * Materials like Nether Star → Nether Sharpened still work the same: cost 1 means a single
 * star = 10000 BP = immediate level up. No partial-progress storage involved.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class AnvilModifierHandler {
    private AnvilModifierHandler() {}

    /** XP-level cost per partial OR full application (i.e. paid each interaction, not per level). */
    private static final int APPLY_XP_COST = 1;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack tool = event.getLeft();
        ItemStack source = event.getRight();
        if (tool.isEmpty() || source.isEmpty()) return;
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        // Refuse vanilla enchanted-book combos — smithery handles enchantments via its own
        // modifier system. Empty output = anvil shows no result.
        if (source.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            event.setOutput(ItemStack.EMPTY);
            return;
        }

        ModifierSources.Entry sourceEntry = ModifierSources.resolve(source);
        if (sourceEntry == null) return;  // not a registered source

        ToolComposition comp = tool.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null || !comp.isValid()) return;

        if (SmitheryToolItem.freeModifierSlots(tool) <= 0) return;

        // Find existing level of this modifier on the tool (if any).
        List<ModifierEffect> existing = tool.getOrDefault(
                SmitheryDataComponents.APPLIED_MODIFIERS.get(), List.of());
        Identifier modifierId = sourceEntry.effect().modifierId();
        int existingIndex = -1;
        int currentLevel = 0;
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).modifierId().equals(modifierId)) {
                existingIndex = i;
                currentLevel = Math.max(1, existing.get(i).paramInt("level", 1));
                break;
            }
        }

        Modifier mod = SmitheryAPI.MODIFIERS.get(modifierId);
        int maxLevel = mod != null ? mod.maxLevel() : 1;
        if (currentLevel >= maxLevel) return;

        // Unit math: modifier declares total units needed per level (with scaling); source
        // declares unit value per item. Progress accumulates in units. So lapis dust (1 unit
        // each) and lapis blocks (9 units each) both contribute to the same total — 45 dust
        // + 5 blocks = 45 + 45 = 90 units = level 1 commit.
        int levelCost = mod != null ? mod.levelCostFor(currentLevel) : 1;
        int unitValue = Math.max(1, sourceEntry.unitValue());

        // Read current progress for this modifier (units accumulated so far).
        Map<Identifier, Integer> progressMap = tool.getOrDefault(
                SmitheryDataComponents.MODIFIER_PROGRESS.get(), Map.of());
        int currentUnits = Math.max(0, progressMap.getOrDefault(modifierId, 0));

        // Cap consumption at exactly what's needed to hit the threshold — no overspend.
        int neededUnits = Math.max(1, levelCost - currentUnits);
        int neededItems = (neededUnits + unitValue - 1) / unitValue;  // ceil div
        int toConsume = Math.min(source.getCount(), neededItems);
        if (toConsume <= 0) return;

        int addedUnits = toConsume * unitValue;
        int newUnits = Math.min(levelCost, currentUnits + addedUnits);

        ItemStack output = tool.copy();
        Map<Identifier, Integer> newProgressMap = new HashMap<>(progressMap);

        if (newUnits >= levelCost) {
            // Level UP. Commit a new ModifierEffect entry (or replace existing one).
            int newLevel = currentLevel + 1;
            Map<String, Object> params = new HashMap<>(sourceEntry.effect().params());
            params.put("level", (float) newLevel);
            ModifierEffect newEffect = ModifierEffect.of(modifierId, params);
            List<ModifierEffect> updated = new ArrayList<>(existing);
            if (existingIndex >= 0) updated.set(existingIndex, newEffect);
            else updated.add(newEffect);
            output.set(SmitheryDataComponents.APPLIED_MODIFIERS.get(), List.copyOf(updated));

            // Reset progress for this modifier (no overflow — neededItems clamp ensures we
            // don't over-consume).
            newProgressMap.remove(modifierId);
            if (newProgressMap.isEmpty()) {
                output.remove(SmitheryDataComponents.MODIFIER_PROGRESS.get());
            } else {
                output.set(SmitheryDataComponents.MODIFIER_PROGRESS.get(), newProgressMap);
            }

            net.minecraft.core.HolderLookup.Provider lookup = event.getPlayer() != null
                    ? event.getPlayer().level().registryAccess() : null;
            SmitheryToolItem.applyComposition(output, comp, lookup);
        } else {
            // Partial progress — record on the tool, no level change, no recompose.
            newProgressMap.put(modifierId, newUnits);
            output.set(SmitheryDataComponents.MODIFIER_PROGRESS.get(), newProgressMap);
        }

        event.setOutput(output);
        event.setXpCost(APPLY_XP_COST);
        event.setMaterialCost(toConsume);
    }
}
