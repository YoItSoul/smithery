package com.soul.smithery.api.alloy;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Modder-facing registry of {@link AlloyRecipe} entries.
 *
 * <p>Two coexisting layers mirror {@link com.soul.smithery.api.modifier.ModifierSources}: a code
 * layer populated by mod init, and a data layer repopulated from JSON on every {@code /reload}.
 * Data entries take precedence on id collision; the merged {@link #all()} view sorts by
 * descending input count so more specific recipes win the conflict-resolution race in the forge.
 */
public final class AlloyRecipes {
    private AlloyRecipes() {}

    private static final Map<ResourceLocation, AlloyRecipe> CODE_REGISTRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, AlloyRecipe> DATA_REGISTRY = new LinkedHashMap<>();

    /** Registers (or replaces) a code-layer alloy. Stable across reloads. */
    public static void register(ResourceLocation id, AlloyRecipe recipe) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipe, "recipe");
        CODE_REGISTRY.put(id, recipe);
    }

    /** Wipes the data layer; called at the start of each reload pass. */
    public static void clearDataEntries() {
        DATA_REGISTRY.clear();
    }

    /** Registers one data-layer alloy. Called by the reload listener per JSON file. */
    public static void registerDataEntry(ResourceLocation id, AlloyRecipe recipe) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipe, "recipe");
        DATA_REGISTRY.put(id, recipe);
    }

    /**
     * All registered alloys sorted by descending input count (more-specific first).
     *
     * <p>Data entries override code entries on id collision. Tie-breaker within equal input counts
     * is data-before-code, then insertion order — deterministic across reloads.
     */
    public static List<AlloyRecipe> all() {
        List<AlloyRecipe> out = new ArrayList<>(CODE_REGISTRY.size() + DATA_REGISTRY.size());
        for (Map.Entry<ResourceLocation, AlloyRecipe> e : CODE_REGISTRY.entrySet()) {
            if (!DATA_REGISTRY.containsKey(e.getKey())) out.add(e.getValue());
        }
        out.addAll(DATA_REGISTRY.values());
        out.sort((a, b) -> Integer.compare(b.inputs().size(), a.inputs().size()));
        return Collections.unmodifiableList(out);
    }
}
