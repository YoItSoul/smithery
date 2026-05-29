package com.soul.smithery.api.synergy;

import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registered two-material synergy that grants bonus modifier effects when both materials appear
 * in the same tool.
 *
 * <p>The two materials are stored in a canonical (lexicographic) order so equality and lookup are
 * independent of registration order. The per-{@link ToolType} effects are applied as bonus
 * modifiers without consuming modifier slots; tool types that aren't keyed in the effect map
 * simply do nothing for this synergy.
 */
public final class SynergyDefinition {
    private final Identifier id;
    private final Identifier materialA;
    private final Identifier materialB;
    private final Map<Identifier, ModifierEffect> effectsPerToolType;

    private SynergyDefinition(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        if (Objects.equals(b.materialA, b.materialB)) {
            throw new IllegalArgumentException("Synergy " + b.id + " requires two distinct materials");
        }
        if (b.materialA.toString().compareTo(b.materialB.toString()) <= 0) {
            this.materialA = b.materialA;
            this.materialB = b.materialB;
        } else {
            this.materialA = b.materialB;
            this.materialB = b.materialA;
        }
        this.effectsPerToolType = Collections.unmodifiableMap(new HashMap<>(b.effects));
    }

    /** Identifier for this synergy. */
    public Identifier id() { return id; }

    /** First material id (lexicographically smaller of the two). */
    public Identifier materialA() { return materialA; }

    /** Second material id (lexicographically larger of the two). */
    public Identifier materialB() { return materialB; }

    /** Unmodifiable per-tool-type effect map. Tool types absent from this map get nothing. */
    public Map<Identifier, ModifierEffect> effectsPerToolType() { return effectsPerToolType; }

    /** Effect for the given tool type, or {@code null} if none is registered. */
    public ModifierEffect effectFor(ToolType toolType) {
        return effectsPerToolType.get(toolType.id());
    }

    /** True if this synergy applies to the (a, b) pair regardless of argument order. */
    public boolean matches(Identifier a, Identifier b) {
        return (materialA.equals(a) && materialB.equals(b)) || (materialA.equals(b) && materialB.equals(a));
    }

    /** Begins building a {@link SynergyDefinition} with the given id. */
    public static Builder builder(Identifier id) { return new Builder(id); }

    /** Fluent builder for {@link SynergyDefinition}. */
    public static final class Builder {
        private final Identifier id;
        private Identifier materialA;
        private Identifier materialB;
        private final Map<Identifier, ModifierEffect> effects = new HashMap<>();

        private Builder(Identifier id) { this.id = id; }

        /** Sets the two material ids this synergy fires for (order-independent at lookup time). */
        public Builder materials(Identifier a, Identifier b) {
            this.materialA = a;
            this.materialB = b;
            return this;
        }

        /** String-id overload of {@link #materials(Identifier, Identifier)}. */
        public Builder materials(String a, String b) {
            return materials(Identifier.parse(a), Identifier.parse(b));
        }

        /** Attaches a {@link ModifierEffect} to fire for the given tool type. */
        public Builder addEffect(ToolType tt, ModifierEffect effect) {
            effects.put(tt.id(), effect);
            return this;
        }

        /** Convenience overload wrapping a bare modifier id in a parameterless effect. */
        public Builder addEffect(ToolType tt, Identifier modifierId) {
            return addEffect(tt, ModifierEffect.of(modifierId));
        }

        /** Convenience overload wrapping a modifier id and parameter map in an effect. */
        public Builder addEffect(ToolType tt, Identifier modifierId, Map<String, Object> params) {
            return addEffect(tt, ModifierEffect.of(modifierId, params));
        }

        /** Finalizes and returns the built {@link SynergyDefinition}. */
        public SynergyDefinition build() {
            Objects.requireNonNull(materialA, "synergy materialA");
            Objects.requireNonNull(materialB, "synergy materialB");
            return new SynergyDefinition(this);
        }
    }
}
