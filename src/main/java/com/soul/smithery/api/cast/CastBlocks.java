package com.soul.smithery.api.cast;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registry mapping a material to the storage block a Casting Basin pours it into.
 *
 * <p>Where {@link CastResults} is keyed by (material, part type) because a Casting Table can be
 * impressed with any shape, a basin only ever casts one thing — the material's block — so a single
 * material key is enough. Each entry carries its own portion because "a block" is not a fixed
 * amount across materials: metals are nine 144 mB ingots, slime is nine 125 mB balls, lapis is nine
 * 16 mB gems, and amethyst is only four shards. Nothing here derives a portion from a formula.
 *
 * <p>The registered portion is expected to mirror the block's melting recipe so the cast round-trips
 * exactly. A portion below what the block melts for is a duplication cycle, not a discount.
 *
 * <p>Two coexisting layers mirror {@link com.soul.smithery.api.alloy.AlloyRecipes}: a code layer
 * populated at mod init, and a data layer repopulated from
 * {@code data/<namespace>/smithery/basin_cast/*.json} on every {@code /reload}. Data entries
 * override code entries for the same material, and a data file may also remove a material's cast
 * outright so packs can take a basin recipe away rather than only retune it.
 *
 * <p>Sparse — a material with no entry cannot be basin-cast at all, and the basin refuses to accept
 * its fluid rather than swallowing metal it could never give back. Built-in mappings cover the
 * vanilla storage blocks; {@link com.soul.smithery.registry.SmitheryMaterialForms} adds one for
 * every generated storage form.
 *
 * <p>Result items are stored as {@link Supplier} so code registration may run before the item
 * registry is populated; the supplier is only invoked at resolve time.
 */
public final class CastBlocks {

    /**
     * One material's basin cast.
     *
     * @param mb     molten milliBuckets needed to fill the basin for this material
     * @param result supplier of the item the finished cast yields
     */
    public record Cast(int mb, Supplier<Item> result) {}

    private static final Map<ResourceLocation, Cast> CODE_REGISTRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Cast> DATA_REGISTRY = new LinkedHashMap<>();
    private static final Set<ResourceLocation> DATA_REMOVALS = new LinkedHashSet<>();

    private CastBlocks() {}

    /**
     * Registers (or replaces) a code-layer cast: the block a basin produces when {@code materialId}
     * is poured into it. Stable across reloads.
     *
     * @param materialId     id of a registered Material
     * @param mb             molten milliBuckets the basin must receive to complete the cast
     * @param resultSupplier supplier of the resulting item, resolved lazily
     */
    public static void register(ResourceLocation materialId, int mb, Supplier<Item> resultSupplier) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(resultSupplier, "resultSupplier");
        if (mb <= 0) throw new IllegalArgumentException("Basin cast for " + materialId + " needs a positive mB");
        CODE_REGISTRY.put(materialId, new Cast(mb, resultSupplier));
    }

    /** Wipes the data layer, removals included; called at the start of each reload pass. */
    public static void clearDataEntries() {
        DATA_REGISTRY.clear();
        DATA_REMOVALS.clear();
    }

    /**
     * Registers one data-layer cast, shadowing any code-layer cast for the same material. Called by
     * the reload listener per JSON file.
     */
    public static void registerDataEntry(ResourceLocation materialId, int mb, Supplier<Item> resultSupplier) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(resultSupplier, "resultSupplier");
        if (mb <= 0) throw new IllegalArgumentException("Basin cast for " + materialId + " needs a positive mB");
        DATA_REMOVALS.remove(materialId);
        DATA_REGISTRY.put(materialId, new Cast(mb, resultSupplier));
    }

    /**
     * Suppresses a material's cast entirely for this reload, code layer included, so a pack can take
     * a basin recipe away rather than only retune it.
     */
    public static void removeDataEntry(ResourceLocation materialId) {
        Objects.requireNonNull(materialId, "materialId");
        DATA_REGISTRY.remove(materialId);
        DATA_REMOVALS.add(materialId);
    }

    /**
     * The data layer alone. Used to ship pack-loaded casts to clients, which run their own copy of
     * this registry and would otherwise only ever see the code layer.
     */
    public static Map<ResourceLocation, Cast> dataEntries() {
        return Collections.unmodifiableMap(DATA_REGISTRY);
    }

    /** The materials the data layer suppressed. Shipped to clients alongside {@link #dataEntries()}. */
    public static Set<ResourceLocation> dataRemovals() {
        return Collections.unmodifiableSet(DATA_REMOVALS);
    }

    /**
     * Replaces the data layer wholesale with entries received from the server. Distinct from
     * {@link #registerDataEntry} so the client applies one atomic snapshot rather than clearing and
     * refilling in steps.
     */
    public static void replaceDataEntries(Map<ResourceLocation, Cast> entries,
                                          Set<ResourceLocation> removals) {
        DATA_REGISTRY.clear();
        DATA_REGISTRY.putAll(entries);
        DATA_REMOVALS.clear();
        DATA_REMOVALS.addAll(removals);
    }

    /**
     * Looks up the basin cast for a material, data layer first.
     *
     * @param materialId id of a registered Material; may be null
     * @return the effective {@link Cast}, or null when this material has no block form
     */
    public static @Nullable Cast resolve(@Nullable ResourceLocation materialId) {
        if (materialId == null || DATA_REMOVALS.contains(materialId)) return null;
        Cast data = DATA_REGISTRY.get(materialId);
        return data != null ? data : CODE_REGISTRY.get(materialId);
    }

    /**
     * Every effective basin cast — code entries in registration order, with data entries overriding
     * and removals excluded.
     */
    public static Map<ResourceLocation, Cast> all() {
        Map<ResourceLocation, Cast> merged = new LinkedHashMap<>(CODE_REGISTRY);
        merged.keySet().removeAll(DATA_REMOVALS);
        merged.putAll(DATA_REGISTRY);
        return Collections.unmodifiableMap(merged);
    }
}
