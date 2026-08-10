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
    /** Shape id -> conventional Forge tag folder used when no mapping covers a material. */
    private static final Map<ResourceLocation, String> TAG_FALLBACKS = new HashMap<>();

    private CastResults() {}

    /** Registers the item produced when {@code materialId} is poured into a cast of {@code partTypeId}. */
    public static void register(ResourceLocation materialId, ResourceLocation partTypeId, Supplier<Item> resultSupplier) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(resultSupplier, "resultSupplier");
        ENTRIES.put(new Key(materialId, partTypeId), resultSupplier);
    }

    /**
     * Declares that a shape corresponds to a conventional Forge item tag, so any material with that
     * tag can be cast into it without anyone writing a mapping.
     *
     * @param partTypeId shape being described, e.g. {@code smithery:ingot}
     * @param tagFolder  folder under {@code forge:}, e.g. {@link CastTags#INGOTS}
     */
    public static void registerTagFallback(ResourceLocation partTypeId, String tagFolder) {
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(tagFolder, "tagFolder");
        TAG_FALLBACKS.put(partTypeId, tagFolder);
    }

    /**
     * Returns the result item for the given (material, part-type) pair, or {@code null} when the
     * pairing produces nothing. Callers typically fall back to a Smithery PartItem lookup or yield
     * nothing.
     *
     * <p>Resolution order is registered mapping, then Forge tag. A registered supplier that returns
     * null falls through to the tag rather than ending the search — cross-mod mappings are written
     * as lazy lookups that yield null when the other mod is absent, and a pack that has the metal
     * from somewhere else should still be able to cast it.
     */
    public static @Nullable Item resolve(ResourceLocation materialId, ResourceLocation partTypeId) {
        Supplier<Item> supplier = ENTRIES.get(new Key(materialId, partTypeId));
        if (supplier != null) {
            Item explicit = supplier.get();
            if (explicit != null) return explicit;
        }
        String folder = TAG_FALLBACKS.get(partTypeId);
        return folder == null ? null : CastTags.resolve(materialId, folder);
    }

    /** True iff a result is registered for the pair. */
    public static boolean hasResult(ResourceLocation materialId, ResourceLocation partTypeId) {
        return ENTRIES.containsKey(new Key(materialId, partTypeId));
    }

    private record Key(ResourceLocation materialId, ResourceLocation partTypeId) {}
}
