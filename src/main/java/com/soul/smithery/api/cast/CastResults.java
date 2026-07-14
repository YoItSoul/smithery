package com.soul.smithery.api.cast;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Modder-facing registry mapping (material, cast-target part-type) pairs to the produced item.
 *
 * <p>Sparse — only registered pairs yield items; everything else falls back to the default
 * Smithery PartItem resolution (or returns nothing for combos with no built-in PartItem, like
 * "iron + pearl"). Built-in mappings cover the vanilla ingot/nugget casts for iron, gold, and
 * copper.
 *
 * <p>Result items are stored as {@link Supplier} so registration may run before the item registry
 * is populated; the supplier is only invoked at resolve time.
 */
public final class CastResults {
    private static final Map<Key, Supplier<Item>> ENTRIES = new HashMap<>();

    private CastResults() {}

    /** Registers the item produced when {@code materialId} is poured into a cast of {@code partTypeId}. */
    public static void register(ResourceLocation materialId, ResourceLocation partTypeId, Supplier<Item> resultSupplier) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(resultSupplier, "resultSupplier");
        ENTRIES.put(new Key(materialId, partTypeId), resultSupplier);
    }

    /**
     * Returns the result item for the given (material, part-type) pair, or {@code null} if no
     * mapping was registered. Callers typically fall back to a Smithery PartItem lookup or yield
     * nothing.
     */
    public static @Nullable Item resolve(ResourceLocation materialId, ResourceLocation partTypeId) {
        Supplier<Item> supplier = ENTRIES.get(new Key(materialId, partTypeId));
        return supplier == null ? null : supplier.get();
    }

    /** True iff a result is registered for the pair. */
    public static boolean hasResult(ResourceLocation materialId, ResourceLocation partTypeId) {
        return ENTRIES.containsKey(new Key(materialId, partTypeId));
    }

    private record Key(ResourceLocation materialId, ResourceLocation partTypeId) {}
}
