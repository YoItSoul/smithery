package com.soul.smithery.api.melting;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Recipe matching one input item to an output material and mB amount produced in the forge.
 *
 * <p>When this item is consumed in the forge interior, the forge produces {@code outputMb} of
 * molten {@code outputMaterialId}. The smelt time scales with temperature via the
 * {@link com.soul.smithery.api.material.MaterialStats} melt-rate formula and an item only melts
 * once the forge reaches the material's melt point.
 *
 * @param inputItemId      id of the item that melts
 * @param outputMaterialId id of the material the item melts into
 * @param outputMb         milliBuckets of output material produced (nugget=16, ingot=144, ore=288, block=1296)
 */
public record MeltingRecipe(ResourceLocation inputItemId, ResourceLocation outputMaterialId, int outputMb) {

    /** Compact constructor enforcing non-null ids and a positive output volume. */
    public MeltingRecipe {
        Objects.requireNonNull(inputItemId, "inputItemId");
        Objects.requireNonNull(outputMaterialId, "outputMaterialId");
        if (outputMb <= 0) throw new IllegalArgumentException("outputMb must be > 0");
    }
}
