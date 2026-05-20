package com.soul.smithery.api.synergy;

import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A registered material synergy.
 *
 * Two materials, order-independent. When a tool has parts of both materials, the per-ToolType
 * effects in this synergy are applied as bonus modifiers — without consuming modifier slots.
 *
 * effectsPerToolType may omit ToolTypes that have no synergy effect; missing entries simply do
 * nothing for that tool type.
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
        // Canonicalize order: lexicographic on Identifier toString. Makes equality and lookup
        // independent of registration order.
        if (b.materialA.toString().compareTo(b.materialB.toString()) <= 0) {
            this.materialA = b.materialA;
            this.materialB = b.materialB;
        } else {
            this.materialA = b.materialB;
            this.materialB = b.materialA;
        }
        this.effectsPerToolType = Collections.unmodifiableMap(new HashMap<>(b.effects));
    }

    public Identifier id() { return id; }
    public Identifier materialA() { return materialA; }
    public Identifier materialB() { return materialB; }
    public Map<Identifier, ModifierEffect> effectsPerToolType() { return effectsPerToolType; }

    public ModifierEffect effectFor(ToolType toolType) {
        return effectsPerToolType.get(toolType.id());
    }

    /** True if this synergy applies to the (a, b) pair regardless of argument order. */
    public boolean matches(Identifier a, Identifier b) {
        return (materialA.equals(a) && materialB.equals(b)) || (materialA.equals(b) && materialB.equals(a));
    }

    public static Builder builder(Identifier id) { return new Builder(id); }

    public static final class Builder {
        private final Identifier id;
        private Identifier materialA;
        private Identifier materialB;
        private final Map<Identifier, ModifierEffect> effects = new HashMap<>();

        private Builder(Identifier id) { this.id = id; }

        public Builder materials(Identifier a, Identifier b) {
            this.materialA = a;
            this.materialB = b;
            return this;
        }

        public Builder materials(String a, String b) {
            return materials(Identifier.parse(a), Identifier.parse(b));
        }

        public Builder addEffect(ToolType tt, ModifierEffect effect) {
            effects.put(tt.id(), effect);
            return this;
        }

        public Builder addEffect(ToolType tt, Identifier modifierId) {
            return addEffect(tt, ModifierEffect.of(modifierId));
        }

        public Builder addEffect(ToolType tt, Identifier modifierId, Map<String, Object> params) {
            return addEffect(tt, ModifierEffect.of(modifierId, params));
        }

        public SynergyDefinition build() {
            Objects.requireNonNull(materialA, "synergy materialA");
            Objects.requireNonNull(materialB, "synergy materialB");
            return new SynergyDefinition(this);
        }
    }
}
