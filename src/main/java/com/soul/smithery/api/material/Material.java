package com.soul.smithery.api.material;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Registered substance in Smithery (iron, gold, copper, blood, ender, etc.).
 *
 * <p>Pairs an id with a mutable-by-override {@link MaterialStats} block. Stats live in a separate
 * object so datapack overrides can replace them at resource reload without invalidating Material
 * identity — existing references to the Material remain valid; only the stat block changes.
 */
public final class Material {
    private final ResourceLocation id;
    private volatile MaterialStats stats;

    /** Constructs a material with the given id and initial stats. */
    public Material(ResourceLocation id, MaterialStats stats) {
        this.id = Objects.requireNonNull(id);
        this.stats = Objects.requireNonNull(stats);
    }

    /** ResourceLocation for this material. */
    public ResourceLocation id() { return id; }

    /** Current stat block for this material. */
    public MaterialStats stats() { return stats; }

    /** Replace this material's stat block (used by datapack overrides). */
    public void overrideStats(MaterialStats newStats) {
        this.stats = Objects.requireNonNull(newStats);
    }

    @Override public boolean equals(Object o) { return o instanceof Material m && m.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "Material[" + id + "]"; }
}
