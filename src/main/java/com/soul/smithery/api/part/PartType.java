package com.soul.smithery.api.part;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Category of tool part (sword_blade, pick_head, handle, binder, etc.).
 *
 * <p>Each registered Material gets one PartItem auto-generated per registered PartType (subject to
 * any {@link PartEligibility} restrictions). The {@link #partColorTint} flag controls whether the
 * material's part color is applied at render time — some parts (e.g. wooden handles) prefer a
 * fixed tint and opt out by setting it to false.
 */
public final class PartType {
    /** Default mB needed to cast one part if a PartType doesn't override it (half an ingot). */
    public static final int DEFAULT_CAST_MB = 72;

    private final ResourceLocation id;
    private final float durabilityScalar;
    private final boolean partColorTint;
    private final ResourceLocation textureTemplate;
    private final int castMb;
    private final boolean syntheticCast;

    private PartType(Builder b) {
        this.id = Objects.requireNonNull(b.id, "PartType id");
        this.durabilityScalar = b.durabilityScalar;
        this.partColorTint = b.partColorTint;
        this.textureTemplate = b.textureTemplate != null ? b.textureTemplate
                : ResourceLocation.fromNamespaceAndPath(b.id.getNamespace(), "item/part/" + b.id.getPath());
        this.castMb = b.castMb;
        this.syntheticCast = b.syntheticCast;
    }

    /** ResourceLocation for this part type. */
    public ResourceLocation id() { return id; }

    /** Scalar applied to this slot's durability contribution when computing tool durability. */
    public float durabilityScalar() { return durabilityScalar; }

    /** Whether the per-material part color should be applied at render time. */
    public boolean partColorTint() { return partColorTint; }

    /** Texture template path used by the auto-generated PartItem model. */
    public ResourceLocation textureTemplate() { return textureTemplate; }

    /** mB of molten material needed to cast one of this part. */
    public int castMb() { return castMb; }

    /**
     * If true, this PartType does NOT auto-generate Smithery PartItems.
     *
     * <p>The cast resolves via {@link com.soul.smithery.api.cast.CastResults} to whatever item the
     * modder registered (typically a vanilla item or another mod's item). Used for "shape-only"
     * casts that produce existing items (ingot, nugget, ender pearl).
     */
    public boolean syntheticCast() { return syntheticCast; }

    @Override public boolean equals(Object o) { return o instanceof PartType p && p.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "PartType[" + id + "]"; }

    /** Begins building a {@link PartType} with the given id. */
    public static Builder builder(ResourceLocation id) { return new Builder(id); }

    /** Fluent builder for {@link PartType}. */
    public static final class Builder {
        private final ResourceLocation id;
        private float durabilityScalar = 1.0f;
        private boolean partColorTint = true;
        private ResourceLocation textureTemplate;
        private int castMb = DEFAULT_CAST_MB;
        private boolean syntheticCast = false;

        private Builder(ResourceLocation id) { this.id = id; }

        /** Sets the durability scalar applied to this slot's contribution. */
        public Builder durabilityScalar(float v) { this.durabilityScalar = v; return this; }

        /** Sets whether the material's part color is applied at render time. */
        public Builder partColorTint(boolean v) { this.partColorTint = v; return this; }

        /** Overrides the texture template path used for auto-generated PartItem models. */
        public Builder textureTemplate(ResourceLocation t) { this.textureTemplate = t; return this; }

        /** Override how much molten material is needed to cast one part of this type. */
        public Builder castMb(int v) { this.castMb = v; return this; }

        /**
         * Mark as a shape-only cast that doesn't produce a Smithery PartItem (e.g. ingot,
         * nugget, ender pearl). The cast resolves via {@link com.soul.smithery.api.cast.CastResults}.
         */
        public Builder syntheticCast(boolean v) { this.syntheticCast = v; return this; }

        /** Finalizes and returns the built {@link PartType}. */
        public PartType build() { return new PartType(this); }
    }
}
