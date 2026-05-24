package com.soul.smithery.api.alloy;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Modder-facing registry of alloy recipes. Code path and data path coexist — same shape as
 * {@link com.soul.smithery.api.modifier.ModifierSources}:
 *
 * <h3>Code path</h3>
 * <pre>{@code
 *   AlloyRecipes.register(Identifier.fromNamespaceAndPath("yourmod", "bronze"),
 *           new AlloyRecipe(
 *                   List.of(new AlloyRecipe.Input(yourmodCopperId, 144),
 *                           new AlloyRecipe.Input(yourmodTinId, 144)),
 *                   new AlloyRecipe.Output(yourmodBronzeId, 288),
 *                   1085f));
 * }</pre>
 *
 * <h3>Data path</h3>
 * Drop a JSON file at {@code data/<ns>/smithery/alloy/<name>.json} — see
 * {@link AlloyRecipe} for the schema. The reload listener reads at server start and on
 * {@code /reload}, overwriting any same-id code-registered entry.
 *
 * <h3>Resolution order</h3>
 * Data entries take precedence over code entries on id collision. {@link #all()} returns
 * the merged view in insertion order (code first, then data, with data overriding code).
 */
public final class AlloyRecipes {
    private AlloyRecipes() {}

    private static final Map<Identifier, AlloyRecipe> CODE_REGISTRY = new LinkedHashMap<>();
    private static final Map<Identifier, AlloyRecipe> DATA_REGISTRY = new LinkedHashMap<>();

    // ---- Code path ----

    public static void register(Identifier id, AlloyRecipe recipe) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipe, "recipe");
        CODE_REGISTRY.put(id, recipe);
    }

    // ---- Data path ----

    public static void clearDataEntries() {
        DATA_REGISTRY.clear();
    }

    public static void registerDataEntry(Identifier id, AlloyRecipe recipe) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipe, "recipe");
        DATA_REGISTRY.put(id, recipe);
    }

    // ---- Lookup ----

    /**
     * All registered alloys (data overrides code on id collision), sorted by priority:
     * <ol>
     *   <li>More inputs first — the "more specific" recipe wins. If a 2-input recipe
     *       (silver+gold → electrum) and a 3-input recipe (silver+gold+iron → constantan)
     *       both have their preconditions met, constantan fires first because it requires
     *       more inputs. After it consumes the shared fluids, the electrum recipe no
     *       longer has its inputs and naturally skips. Players who want electrum keep
     *       iron OUT of the forge until the electrum is drained.</li>
     *   <li>Tie-breaker: data entries before code entries, then insertion order within
     *       each. Deterministic across reloads.</li>
     * </ol>
     *
     * <p>Future enhancement: per-forge "auto-alloy paused" toggle (UI button on the
     * controller GUI) for cases where the player needs to prevent a more-specific recipe
     * from beating out a less-specific one without draining and re-pouring. Documented
     * here so it's obvious the priority rule alone doesn't cover every conflict.
     */
    public static List<AlloyRecipe> all() {
        List<AlloyRecipe> out = new ArrayList<>(CODE_REGISTRY.size() + DATA_REGISTRY.size());
        for (Map.Entry<Identifier, AlloyRecipe> e : CODE_REGISTRY.entrySet()) {
            if (!DATA_REGISTRY.containsKey(e.getKey())) out.add(e.getValue());
        }
        out.addAll(DATA_REGISTRY.values());
        out.sort((a, b) -> Integer.compare(b.inputs().size(), a.inputs().size()));
        return Collections.unmodifiableList(out);
    }
}
