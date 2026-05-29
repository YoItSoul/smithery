package com.soul.smithery.api.alloy;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Legacy ratio-based alloy recipe registered through {@link com.soul.smithery.api.SmitheryAPI#ALLOYS}.
 *
 * <p>Holds 2 to 4 {@link AlloyComponent} entries. The forge processes alloys via a
 * conflict-resolution algorithm: recipes with more components always reserve their materials before
 * simpler recipes that share components. When two recipes have equal component count, the forge's
 * fluid insertion order is the tiebreaker.
 */
public final class AlloyDefinition {
    /** Minimum number of input components an alloy must declare. */
    public static final int MIN_COMPONENTS = 2;

    /** Maximum number of input components an alloy may declare. */
    public static final int MAX_COMPONENTS = 4;

    /** mB produced per ratio unit (1 unit = 144 mB, one ingot's worth). */
    public static final int MB_PER_RATIO_UNIT = 144;

    private final Identifier resultMaterialId;
    private final List<AlloyComponent> components;
    private final float minTemp;

    private AlloyDefinition(Builder b) {
        this.resultMaterialId = Objects.requireNonNull(b.result, "alloy result");
        if (b.components.size() < MIN_COMPONENTS || b.components.size() > MAX_COMPONENTS) {
            throw new IllegalArgumentException(
                    "Alloy " + b.result + " has " + b.components.size()
                    + " components; must be " + MIN_COMPONENTS + "–" + MAX_COMPONENTS);
        }
        this.components = Collections.unmodifiableList(new ArrayList<>(b.components));
        this.minTemp = b.minTemp;
    }

    /** Id of the material produced by this alloy. */
    public Identifier resultMaterialId() { return resultMaterialId; }

    /** Unmodifiable view of the alloy's input components. */
    public List<AlloyComponent> components() { return components; }

    /** Minimum forge temperature required for the alloy to fire. */
    public float minTemp() { return minTemp; }

    /** Total output mB produced per minimum batch (sum of all component ratios x 144 mB). */
    public int batchOutputMb() {
        int sum = 0;
        for (AlloyComponent c : components) sum += c.ratio();
        return sum * MB_PER_RATIO_UNIT;
    }

    /** Required input mB for the given component to satisfy one minimum batch. */
    public int batchInputMb(AlloyComponent c) {
        return c.ratio() * MB_PER_RATIO_UNIT;
    }

    /** Begins building an alloy producing the given result material. */
    public static Builder builder(Identifier result) { return new Builder(result); }

    /** Fluent builder for {@link AlloyDefinition}. */
    public static final class Builder {
        private final Identifier result;
        private final List<AlloyComponent> components = new ArrayList<>();
        private float minTemp = 0f;

        private Builder(Identifier result) { this.result = result; }

        /** Appends an {@link AlloyComponent} with the given material id and ratio. */
        public Builder addComponent(Identifier material, int ratio) {
            components.add(new AlloyComponent(material, ratio));
            return this;
        }

        /** String-id overload of {@link #addComponent(Identifier, int)}. */
        public Builder addComponent(String material, int ratio) {
            return addComponent(Identifier.parse(material), ratio);
        }

        /** Sets the minimum forge temperature required for this alloy to fire. */
        public Builder minTemp(float v) { this.minTemp = v; return this; }

        /** Finalizes and returns the built {@link AlloyDefinition}. */
        public AlloyDefinition build() { return new AlloyDefinition(this); }
    }
}
