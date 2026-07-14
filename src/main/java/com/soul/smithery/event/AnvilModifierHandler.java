package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolCompositions;
import com.soul.smithery.item.tool.SmitheryToolData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AnvilUpdateEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla anvil hook that applies post-craft modifiers to Smithery tools with multi-item
 * partial contribution.
 *
 * <p>Each interaction consumes only as many source items as are needed to fill the current
 * level's threshold. Progress is stored on the tool in the {@code MODIFIER_PROGRESS} component
 * as accumulated unit count keyed by modifier id; when the count reaches the level cost the
 * level commits, the progress entry clears, and any over-consumption is avoided by ceiling-
 * dividing required units by per-item unit value.
 *
 * <p>Vanilla enchanted books are rejected so smithery's modifier system remains the only
 * source of behaviour on these tools.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class AnvilModifierHandler {
    private AnvilModifierHandler() {}

    private static final int APPLY_XP_COST = 1;

    /**
     * Computes the anvil's resulting tool stack and material cost for a smithery tool + source
     * item pair. No-ops for non-smithery tools, unrecognised sources, or tools that are out of
     * modifier slots.
     *
     * @param event NeoForge's anvil-update event whose left/right slots and output are read/written
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack tool = event.getLeft();
        ItemStack source = event.getRight();
        if (tool.isEmpty() || source.isEmpty()) return;
        if (!ToolCompositions.isComposable(tool)) return;

        if (source.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            event.setOutput(ItemStack.EMPTY);
            return;
        }

        ModifierSources.Entry sourceEntry = ModifierSources.resolve(source);
        if (sourceEntry == null) return;

        ToolComposition comp = SmitheryToolData.getComposition(tool);
        if (comp == null || !comp.isValid()) return;

        if (SmitheryToolItem.freeModifierSlots(tool) <= 0) return;

        List<ModifierEffect> existing = SmitheryToolData.getAppliedModifiers(tool);
        ResourceLocation modifierId = sourceEntry.effect().modifierId();
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
        if (mod != null) {
            boolean isArmor = tool.getItem() instanceof com.soul.smithery.item.tool.SmitheryArmorItem;
            if (mod.appliesTo() == Modifier.AppliesTo.ARMOR && !isArmor) return;
            if (mod.appliesTo() == Modifier.AppliesTo.TOOLS && isArmor) return;
        }
        int maxLevel = mod != null ? mod.maxLevel() : 1;
        if (currentLevel >= maxLevel) return;

        int levelCost = mod != null ? mod.levelCostFor(currentLevel) : 1;
        int unitValue = Math.max(1, sourceEntry.unitValue());

        Map<ResourceLocation, Integer> progressMap = SmitheryToolData.getModifierProgress(tool);
        int currentUnits = Math.max(0, progressMap.getOrDefault(modifierId, 0));

        int neededUnits = Math.max(1, levelCost - currentUnits);
        int neededItems = (neededUnits + unitValue - 1) / unitValue;
        int toConsume = Math.min(source.getCount(), neededItems);
        if (toConsume <= 0) return;

        int addedUnits = toConsume * unitValue;
        int newUnits = Math.min(levelCost, currentUnits + addedUnits);

        ItemStack output = tool.copy();
        Map<ResourceLocation, Integer> newProgressMap = new HashMap<>(progressMap);

        if (newUnits >= levelCost) {
            int newLevel = currentLevel + 1;
            Map<String, Object> params = new HashMap<>(sourceEntry.effect().params());
            params.put("level", (float) newLevel);
            ModifierEffect newEffect = ModifierEffect.of(modifierId, params);
            List<ModifierEffect> updated = new ArrayList<>(existing);
            if (existingIndex >= 0) updated.set(existingIndex, newEffect);
            else updated.add(newEffect);
            SmitheryToolData.setAppliedModifiers(output, List.copyOf(updated));

            newProgressMap.remove(modifierId);
            if (newProgressMap.isEmpty()) {
                SmitheryToolData.setModifierProgress(output, Map.of());
            } else {
                SmitheryToolData.setModifierProgress(output, newProgressMap);
            }

            net.minecraft.core.HolderLookup.Provider lookup = event.getPlayer() != null
                    ? event.getPlayer().level().registryAccess() : null;
            ToolCompositions.apply(output, comp, lookup);
        } else {
            newProgressMap.put(modifierId, newUnits);
            SmitheryToolData.setModifierProgress(output, newProgressMap);
        }

        event.setOutput(output);
        event.setXpCost(APPLY_XP_COST);
        event.setMaterialCost(toConsume);
    }
}
