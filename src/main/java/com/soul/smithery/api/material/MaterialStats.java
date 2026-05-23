package com.soul.smithery.api.material;

import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable per-material stat block.
 *
 * - harvestLevel: 0 = by hand, 1 = stone, 2 = iron-tier, 3 = diamond, 4 = netherite-tier
 * - durabilityPerIngot: base durability that an additive part contributes per ingot's worth of material
 * - meltingTemp: °C the forge must meet or exceed
 * - moltenColor: ARGB for auto-generated fluid texture
 * - partColor: ARGB used by the ItemColor handler to tint grayscale part textures
 * - binderMultiplier: when this material is used as a binder, this is its durability multiplier
 * - modifierSlots: how many post-craft modifier slots this material contributes when used as a given PartType
 * - modifiers: at-craft modifiers applied when this material is used in a tool of a given ToolType
 */
public final class MaterialStats {
    private final int harvestLevel;
    private final float miningSpeed;
    private final float attackDamage;
    private final int durabilityPerIngot;
    private final float meltingTemp;
    private final int moltenColor;
    private final int partColor;
    private final float binderMultiplier;
    private final boolean castOnly;
    private final Map<Identifier, Integer> modifierSlots;
    private final Map<Identifier, List<ModifierEffect>> modifiers;

    private MaterialStats(Builder b) {
        this.harvestLevel = b.harvestLevel;
        this.miningSpeed = b.miningSpeed;
        this.attackDamage = b.attackDamage;
        this.durabilityPerIngot = b.durabilityPerIngot;
        this.meltingTemp = b.meltingTemp;
        this.moltenColor = b.moltenColor;
        this.partColor = b.partColor != 0 ? b.partColor : darken(b.moltenColor);
        this.binderMultiplier = b.binderMultiplier;
        this.castOnly = b.castOnly;
        this.modifierSlots = Collections.unmodifiableMap(new HashMap<>(b.modifierSlots));
        this.modifiers = Collections.unmodifiableMap(new HashMap<>(b.modifiers));
    }

    public int harvestLevel() { return harvestLevel; }
    public float miningSpeed() { return miningSpeed; }
    public float attackDamage() { return attackDamage; }
    public int durabilityPerIngot() { return durabilityPerIngot; }
    public float meltingTemp() { return meltingTemp; }
    public int moltenColor() { return moltenColor; }
    public int partColor() { return partColor; }
    public float binderMultiplier() { return binderMultiplier; }
    /**
     * If true, this material exists in fluid form only — it doesn't auto-generate smithery
     * PartItems for the standard part types (blade/guard/handle/etc). Useful for materials
     * like ender that only make sense as a special-cast product (vanilla ender pearl) rather
     * than as a tool material. Set via {@link Builder#castOnly}.
     */
    public boolean castOnly() { return castOnly; }

    public int modifierSlotsFor(PartType partType) {
        return modifierSlots.getOrDefault(partType.id(), 0);
    }

    public List<ModifierEffect> modifiersFor(ToolType toolType) {
        return modifiers.getOrDefault(toolType.id(), List.of());
    }

    public static Builder builder() { return new Builder(); }

    /** Compute a darker shade of an ARGB color (solid part vs molten fluid). */
    private static int darken(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int)(((argb >>> 16) & 0xFF) * 0.7f);
        int g = (int)(((argb >>>  8) & 0xFF) * 0.7f);
        int b = (int)(( argb         & 0xFF) * 0.7f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static final class Builder {
        private int harvestLevel = 0;
        private float miningSpeed = 1.0f;
        private float attackDamage = 0.0f;
        private int durabilityPerIngot = 60;
        private float meltingTemp = 1000f;
        private int moltenColor = 0xFFAAAAAA;
        private int partColor = 0;
        private float binderMultiplier = 1.0f;
        private boolean castOnly = false;
        private final Map<Identifier, Integer> modifierSlots = new HashMap<>();
        private final Map<Identifier, List<ModifierEffect>> modifiers = new HashMap<>();

        public Builder harvestLevel(int v) { this.harvestLevel = v; return this; }
        public Builder miningSpeed(float v) { this.miningSpeed = v; return this; }
        public Builder attackDamage(float v) { this.attackDamage = v; return this; }
        public Builder durabilityPerIngot(int v) { this.durabilityPerIngot = v; return this; }
        public Builder meltingTemp(float v) { this.meltingTemp = v; return this; }
        public Builder moltenColor(int argb) { this.moltenColor = argb; return this; }
        public Builder partColor(int argb) { this.partColor = argb; return this; }
        public Builder binderMultiplier(float v) { this.binderMultiplier = v; return this; }
        /**
         * Mark this material as fluid-only — no auto-generated PartItems for the standard
         * smithery part types. Used for materials that only make sense in cast form
         * (e.g. ender → ender pearl) and shouldn't appear as blade/guard/handle items.
         */
        public Builder castOnly(boolean v) { this.castOnly = v; return this; }

        public Builder modifierSlots(PartType pt, int slots) {
            this.modifierSlots.put(Objects.requireNonNull(pt).id(), slots);
            return this;
        }

        public Builder addModifier(ToolType tt, ModifierEffect effect) {
            this.modifiers.computeIfAbsent(tt.id(), k -> new java.util.ArrayList<>()).add(effect);
            return this;
        }

        public Builder addModifier(ToolType tt, Identifier modifierId) {
            return addModifier(tt, ModifierEffect.of(modifierId));
        }

        public Builder addModifier(ToolType tt, Identifier modifierId, Map<String, Object> params) {
            return addModifier(tt, ModifierEffect.of(modifierId, params));
        }

        public MaterialStats build() { return new MaterialStats(this); }
    }
}
