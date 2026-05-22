package com.soul.smithery.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * A PartType describes a category of tool part (e.g. sword_blade, pick_head, handle, binder).
 *
 * Each registered Material gets one PartItem auto-generated per registered PartType.
 *
 * partColorTint determines whether the material's partColor is applied at render time.
 * Some parts (e.g. wooden handles) may want a fixed brown rather than the material's tint;
 * in those cases the PartType can opt out by setting partColorTint=false.
 */
public final class PartType {
    /** Default mB to cast one part if a PartType doesn't specify — half an ingot. */
    public static final int DEFAULT_CAST_MB = 72;

    private final Identifier id;
    private final float durabilityScalar;
    private final boolean partColorTint;
    private final Identifier textureTemplate;
    private final int castMb;

    private PartType(Builder b) {
        this.id = Objects.requireNonNull(b.id, "PartType id");
        this.durabilityScalar = b.durabilityScalar;
        this.partColorTint = b.partColorTint;
        this.textureTemplate = b.textureTemplate != null ? b.textureTemplate
                : Identifier.fromNamespaceAndPath(b.id.getNamespace(), "item/part/" + b.id.getPath());
        this.castMb = b.castMb;
    }

    public Identifier id() { return id; }
    public float durabilityScalar() { return durabilityScalar; }
    public boolean partColorTint() { return partColorTint; }
    public Identifier textureTemplate() { return textureTemplate; }
    /** mB of molten material needed to cast one of this part. */
    public int castMb() { return castMb; }

    @Override public boolean equals(Object o) { return o instanceof PartType p && p.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "PartType[" + id + "]"; }

    public static Builder builder(Identifier id) { return new Builder(id); }

    public static final class Builder {
        private final Identifier id;
        private float durabilityScalar = 1.0f;
        private boolean partColorTint = true;
        private Identifier textureTemplate;
        private int castMb = DEFAULT_CAST_MB;

        private Builder(Identifier id) { this.id = id; }

        public Builder durabilityScalar(float v) { this.durabilityScalar = v; return this; }
        public Builder partColorTint(boolean v) { this.partColorTint = v; return this; }
        public Builder textureTemplate(Identifier t) { this.textureTemplate = t; return this; }
        /** Override how much molten material is needed to cast one part of this type. */
        public Builder castMb(int v) { this.castMb = v; return this; }

        public PartType build() { return new PartType(this); }
    }
}
