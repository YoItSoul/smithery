package com.soul.smithery.api.cast;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * One data-pack file's worth of Casting Basin recipe, as written on disk.
 *
 * <p>JSON files live at {@code data/<namespace>/smithery/basin_cast/<name>.json}. The file name is
 * only an id for logging — the recipe keys off the {@code material} field, because a basin has one
 * shape and so one recipe per material.
 *
 * <p>Two shapes are accepted. To add or retune a cast:
 * <pre>{@code
 * { "material": "smithery:amethyst", "mb": 64, "result": "minecraft:amethyst_block" }
 * }</pre>
 * To take a built-in cast away, leaving that material un-castable:
 * <pre>{@code
 * { "material": "smithery:amethyst", "remove": true }
 * }</pre>
 *
 * <p>Keep {@code mb} at or above what the same block melts for in the forge. A basin portion below
 * the melting yield lets a player cast a block for less than melting it back returns, which is a
 * duplication cycle rather than a discount.
 *
 * @param material id of the material poured in
 * @param mb       molten milliBuckets the basin must receive; ignored when {@code remove} is set
 * @param result   item id the finished cast yields; absent when {@code remove} is set
 * @param remove   true to suppress this material's cast, built-in entries included
 */
public record BasinCastEntry(ResourceLocation material,
                             int mb,
                             Optional<ResourceLocation> result,
                             boolean remove) {

    /** Compact constructor enforcing a non-null material and result slot. */
    public BasinCastEntry {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(result, "result");
    }

    /** Codec for {@link BasinCastEntry}. */
    public static final Codec<BasinCastEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("material").forGetter(BasinCastEntry::material),
            Codec.INT.optionalFieldOf("mb", 0).forGetter(BasinCastEntry::mb),
            ResourceLocation.CODEC.optionalFieldOf("result").forGetter(BasinCastEntry::result),
            Codec.BOOL.optionalFieldOf("remove", false).forGetter(BasinCastEntry::remove)
    ).apply(i, BasinCastEntry::new));

    /**
     * True when the file is well-formed for what it claims to do: a removal needs nothing else, and
     * anything else needs both a positive portion and a result item.
     */
    public boolean isWellFormed() {
        return remove || (mb > 0 && result.isPresent());
    }
}
