package com.soul.smithery.api.alloy;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A registered alloy recipe. 2 to 4 components.
 *
 * The forge processes alloys via the conflict-resolution algorithm: recipes with more components
 * always reserve their materials before simpler recipes that share components. When two recipes
 * have equal component count, the forge's fluid insertion order is the tiebreaker.
 */
public final class AlloyDefinition {
    public static final int MIN_COMPONENTS = 2;
    public static final int MAX_COMPONENTS = 4;
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

    public Identifier resultMaterialId() { return resultMaterialId; }
    public List<AlloyComponent> components() { return components; }
    public float minTemp() { return minTemp; }

    /** Total output mB produced per minimum batch (sum of all component ratios × 144 mB). */
    public int batchOutputMb() {
        int sum = 0;
        for (AlloyComponent c : components) sum += c.ratio();
        return sum * MB_PER_RATIO_UNIT;
    }

    /** Required input mB for a given component for one minimum batch. */
    public int batchInputMb(AlloyComponent c) {
        return c.ratio() * MB_PER_RATIO_UNIT;
    }

    public static Builder builder(Identifier result) { return new Builder(result); }

    public static final class Builder {
        private final Identifier result;
        private final List<AlloyComponent> components = new ArrayList<>();
        private float minTemp = 0f;

        private Builder(Identifier result) { this.result = result; }

        public Builder addComponent(Identifier material, int ratio) {
            components.add(new AlloyComponent(material, ratio));
            return this;
        }

        public Builder addComponent(String material, int ratio) {
            return addComponent(Identifier.parse(material), ratio);
        }

        public Builder minTemp(float v) { this.minTemp = v; return this; }

        public AlloyDefinition build() { return new AlloyDefinition(this); }
    }
}
