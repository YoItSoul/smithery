package com.soul.smithery.api.alloy;

import net.minecraft.resources.Identifier;

/** One component of an alloy recipe: a material and its ratio. 1 ratio unit = 144 mB. */
public record AlloyComponent(Identifier materialId, int ratio) {
    public AlloyComponent {
        if (ratio <= 0) throw new IllegalArgumentException("ratio must be > 0");
    }
}
