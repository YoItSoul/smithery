package com.soul.smithery.api.melting;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * "When this Item is consumed in the forge interior, produce {@code outputMb} of molten
 * {@code outputMaterialId}."
 *
 * Smelt time scales with temperature using the formula in {@link com.soul.smithery.api.material.MaterialStats}
 * (see SMITHERY_DESIGN.md §4.4). Items only melt once the forge meets the material's melt point.
 *
 * v1 conventions (matching the design doc):
 *   - nugget = 16 mB
 *   - ingot  = 144 mB
 *   - raw / ore form = 288 mB (2× ingot)
 *   - block (9 ingots) = 1296 mB
 */
public record MeltingRecipe(Identifier inputItemId, Identifier outputMaterialId, int outputMb) {

    public MeltingRecipe {
        Objects.requireNonNull(inputItemId, "inputItemId");
        Objects.requireNonNull(outputMaterialId, "outputMaterialId");
        if (outputMb <= 0) throw new IllegalArgumentException("outputMb must be > 0");
    }
}
