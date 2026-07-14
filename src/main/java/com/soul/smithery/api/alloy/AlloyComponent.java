package com.soul.smithery.api.alloy;

import net.minecraft.resources.ResourceLocation;

/**
 * One component of a legacy {@link AlloyDefinition} recipe.
 *
 * @param materialId the contributing material's id
 * @param ratio      ratio units of this component required per minimum batch; one ratio unit equals 144 mB
 */
public record AlloyComponent(ResourceLocation materialId, int ratio) {
    /** Creates a component, rejecting non-positive ratios. */
    public AlloyComponent {
        if (ratio <= 0) throw new IllegalArgumentException("ratio must be > 0");
    }
}
