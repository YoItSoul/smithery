package com.soul.smithery.api.cast;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a material to a concrete item through the common Forge item tags — {@code forge:ingots/…},
 * {@code forge:nuggets/…}, {@code forge:storage_blocks/…}.
 *
 * <p>This is the fallback behind {@link CastResults} and {@link CastBlocks}: hand-written mappings
 * always win, and this catches the long tail nobody wrote a line for. Any mod that tags its metals
 * the conventional way becomes castable without Smithery or an addon knowing it exists.
 *
 * <p>Only the {@link TagKey} objects are cached. Tag <em>contents</em> are read live on every call,
 * because they change with the data pack and a cached item would go stale on {@code /reload} — a
 * TagKey is just an id, so caching it can never be wrong.
 */
public final class CastTags {

    private CastTags() {}

    /** Conventional folder for one-ingot-sized items. */
    public static final String INGOTS = "ingots";
    /** Conventional folder for one-nugget-sized items. */
    public static final String NUGGETS = "nuggets";
    /** Conventional folder for nine-to-the-block storage blocks. */
    public static final String STORAGE_BLOCKS = "storage_blocks";

    private static final Map<String, TagKey<Item>> KEYS = new ConcurrentHashMap<>();

    /**
     * The item a material's tag points at, or null when the tag is absent, empty, or the game has
     * not loaded tags yet.
     *
     * <p>When a tag holds several items — a metal both Thermal and Mekanism ship — the one from the
     * material's own namespace wins, then the first entry in tag order. Tag order is stable for a
     * given set of data packs, so the same pour yields the same item every time.
     *
     * @param materialId material whose path names the tag, e.g. {@code soa_additions:manasteel}
     * @param folder     one of {@link #INGOTS}, {@link #NUGGETS}, {@link #STORAGE_BLOCKS}
     */
    public static @Nullable Item resolve(@Nullable ResourceLocation materialId, String folder) {
        if (materialId == null) return null;
        // Material ids may carry a dotted prefix (ma.superium) that no conventional tag uses.
        if (materialId.getPath().indexOf('.') >= 0) return null;

        TagKey<Item> key = KEYS.computeIfAbsent(folder + "/" + materialId.getPath(),
                path -> TagKey.create(BuiltInRegistries.ITEM.key(),
                        ResourceLocation.fromNamespaceAndPath("forge", path)));

        Item fallback = null;
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            Item item = holder.value();
            if (item == null || item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && id.getNamespace().equals(materialId.getNamespace())) return item;
            if (fallback == null) fallback = item;
        }
        return fallback;
    }
}
