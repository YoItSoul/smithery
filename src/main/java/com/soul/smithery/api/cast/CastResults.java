package com.soul.smithery.api.cast;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Modder-facing registry mapping (material, cast-target part-type) pairs to the item
 * produced when the cast completes. Sparse — only registered pairs yield items; everything
 * else falls back to the default smithery PartItem resolution (or returns nothing for
 * combos with no built-in part item, like "iron + pearl").
 *
 * Built-in mappings:
 *   - (smithery:iron,   smithery:ingot)  → minecraft:iron_ingot
 *   - (smithery:gold,   smithery:ingot)  → minecraft:gold_ingot
 *   - (smithery:copper, smithery:ingot)  → minecraft:copper_ingot
 *   - (smithery:iron,   smithery:nugget) → minecraft:iron_nugget
 *   - (smithery:gold,   smithery:nugget) → minecraft:gold_nugget
 *
 * Modder example — register an ender-pearl cast:
 * <pre>{@code
 *   // Material smithery:ender already registered via SmitheryAPI.registerMaterial(...).
 *   // PartType smithery:pearl registered via SmitheryAPI.registerPartType(...) with
 *   // textureTemplate = minecraft:item/ender_pearl.
 *   CastResults.register(enderMaterialId, pearlPartTypeId, () -> Items.ENDER_PEARL);
 * }</pre>
 * After that, pouring molten ender into a cast impressed with an ender pearl will yield a
 * vanilla ender pearl.
 *
 * Use {@link Supplier} (not a direct Item reference) so registration can run before the
 * item registry is populated — the supplier is only invoked at resolve time.
 */
public final class CastResults {
    private static final Map<Key, Supplier<Item>> ENTRIES = new HashMap<>();

    private CastResults() {}

    /** Registers the item produced when {@code materialId} is poured into a cast of {@code partTypeId}. */
    public static void register(Identifier materialId, Identifier partTypeId, Supplier<Item> resultSupplier) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(resultSupplier, "resultSupplier");
        ENTRIES.put(new Key(materialId, partTypeId), resultSupplier);
    }

    /**
     * Returns the result item for the given (material, part-type) pair, or {@code null} if
     * no mapping was registered. Caller decides what to do with null (typically falls back
     * to the smithery PartItem lookup or yields nothing).
     */
    public static @Nullable Item resolve(Identifier materialId, Identifier partTypeId) {
        Supplier<Item> supplier = ENTRIES.get(new Key(materialId, partTypeId));
        return supplier == null ? null : supplier.get();
    }

    /** True iff a result is registered for the pair. */
    public static boolean hasResult(Identifier materialId, Identifier partTypeId) {
        return ENTRIES.containsKey(new Key(materialId, partTypeId));
    }

    private record Key(Identifier materialId, Identifier partTypeId) {}
}
