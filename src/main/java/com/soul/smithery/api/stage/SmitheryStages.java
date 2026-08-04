package com.soul.smithery.api.stage;

import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game-stage gates for materials, tool types and modifiers — Smithery's answer to
 * TinkerStages.
 *
 * <p>Progression packs lock the material ladder rather than the tools: a modpack
 * decides that cobalt is Nether-tier and bedrock is endgame, and the tool system
 * has to honour that. Tinkers' Construct had TinkerStages for exactly this
 * ({@code addMaterialStage}, {@code addToolTypeStage}, {@code addModifierStage}),
 * and a pack replacing Tinkers with Smithery has nowhere to express it — so a
 * player can compose an endgame-material tool the moment they can obtain one part.</p>
 *
 * <p>Gates are declared per data pack, and GameStages is an optional dependency —
 * looked up reflectively, so Smithery neither requires nor ships it. With
 * GameStages absent nothing is ever locked.</p>
 *
 * <p>Enforcement is at <em>use</em> time rather than crafting time: tool assembly is
 * an ordinary shapeless recipe, and {@code CraftingRecipe#matches} gets no player to
 * test stages against. A locked tool is therefore craftable but inert — it will not
 * mine, attack or activate — which is also how the surrounding ItemStages ecosystem
 * behaves for gated items.</p>
 */
public final class SmitheryStages {

    private static final Map<ResourceLocation, String> MATERIAL_STAGES = new ConcurrentHashMap<>();
    /**
     * Gates declared without a namespace, keyed by path.
     *
     * <p>Materials are contributed by whichever mod registers them — a pack's own
     * addon, or Smithery itself — so a pack author writing a ladder should not have
     * to know which namespace each material happens to live in. A bare
     * {@code "cobalt"} therefore gates any material whose path is {@code cobalt},
     * checked only after an exact id match fails.</p>
     */
    private static final Map<String, String> MATERIAL_STAGES_BY_PATH = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> TOOL_TYPE_STAGES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> MODIFIER_STAGES = new ConcurrentHashMap<>();

    /** Resolved once; null when GameStages is not installed. */
    private static Method hasStageMethod;
    private static boolean stageLookupResolved;

    private SmitheryStages() {}

    // ---------------- registration (called by the reload listener) ----------------

    public static void clearData() {
        MATERIAL_STAGES.clear();
        MATERIAL_STAGES_BY_PATH.clear();
        TOOL_TYPE_STAGES.clear();
        MODIFIER_STAGES.clear();
    }

    public static void putMaterialStage(ResourceLocation material, String stage) {
        MATERIAL_STAGES.put(material, stage);
    }

    /** Gates every material with this path, whatever namespace registered it. */
    public static void putMaterialStageByPath(String materialPath, String stage) {
        MATERIAL_STAGES_BY_PATH.put(materialPath, stage);
    }

    private static String materialStage(ResourceLocation material) {
        String stage = MATERIAL_STAGES.get(material);
        return stage != null ? stage : MATERIAL_STAGES_BY_PATH.get(material.getPath());
    }

    public static void putToolTypeStage(ResourceLocation toolType, String stage) {
        TOOL_TYPE_STAGES.put(toolType, stage);
    }

    public static void putModifierStage(ResourceLocation modifier, String stage) {
        MODIFIER_STAGES.put(modifier, stage);
    }

    public static int gateCount() {
        return MATERIAL_STAGES.size() + MATERIAL_STAGES_BY_PATH.size() + TOOL_TYPE_STAGES.size() + MODIFIER_STAGES.size();
    }

    /** True when no gates are declared at all — lets callers skip work entirely. */
    public static boolean isEmpty() {
        return MATERIAL_STAGES.isEmpty() && MATERIAL_STAGES_BY_PATH.isEmpty()
                && TOOL_TYPE_STAGES.isEmpty() && MODIFIER_STAGES.isEmpty();
    }

    // ---------------- queries ----------------

    /**
     * Every stage this stack demands: one per gated material in its composition, plus
     * its tool type and any applied modifiers. Parts report their own material.
     *
     * @return the required stages, empty when the stack is ungated
     */
    public static Set<String> requiredStages(ItemStack stack) {
        if (isEmpty() || stack.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> stages = new LinkedHashSet<>();

        if (stack.getItem() instanceof PartItem part) {
            addIfPresent(stages, materialStage(part.materialId()));
            return stages;
        }

        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null) {
            return stages;
        }
        addIfPresent(stages, TOOL_TYPE_STAGES.get(comp.toolTypeId()));
        for (ResourceLocation material : comp.slotMaterials()) {
            addIfPresent(stages, materialStage(material));
        }
        for (ModifierEffect effect : SmitheryToolData.getAppliedModifiers(stack)) {
            addIfPresent(stages, MODIFIER_STAGES.get(effect.modifierId()));
        }
        return stages;
    }

    /** The subset of {@link #requiredStages} this player has not unlocked. */
    public static Set<String> missingStages(Player player, ItemStack stack) {
        Set<String> required = requiredStages(stack);
        if (required.isEmpty() || player == null) {
            return Collections.emptySet();
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String stage : required) {
            if (!hasStage(player, stage)) {
                missing.add(stage);
            }
        }
        return missing;
    }

    /** True when this player may not use this stack yet. */
    public static boolean isLocked(Player player, ItemStack stack) {
        if (isEmpty() || player == null || player.isCreative()) {
            return false;
        }
        return !missingStages(player, stack).isEmpty();
    }

    // ---------------- GameStages bridge (optional dependency) ----------------

    private static boolean hasStage(Player player, String stage) {
        Method method = resolveHasStage();
        if (method == null) {
            // GameStages absent: nothing can be locked, so every stage counts as held.
            return true;
        }
        try {
            return (boolean) method.invoke(null, player, stage);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return true;
        }
    }

    private static synchronized Method resolveHasStage() {
        if (!stageLookupResolved) {
            stageLookupResolved = true;
            try {
                Class<?> helper = Class.forName("net.darkhax.gamestages.GameStageHelper");
                hasStageMethod = helper.getMethod("hasStage", Player.class, String.class);
            } catch (ReflectiveOperationException e) {
                hasStageMethod = null;
            }
        }
        return hasStageMethod;
    }

    private static void addIfPresent(Set<String> stages, String stage) {
        if (stage != null && !stage.isEmpty()) {
            stages.add(stage);
        }
    }
}
