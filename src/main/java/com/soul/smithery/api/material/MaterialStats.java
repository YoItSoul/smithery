package com.soul.smithery.api.material;

import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable per-material stat block held by a {@link Material}.
 *
 * <p>Holds harvest level, mining speed, attack damage, durability per ingot, melt point, colors,
 * binder behavior, fluid base, per-{@link PartType} modifier slot counts, and per-{@link ToolType}
 * craft-time modifier effects. Constructed through {@link Builder}.
 */
public final class MaterialStats {

    /**
     * Animated base texture this material's molten fluid is rendered on top of.
     *
     * <p>Both bases are tinted by {@code moltenColor}; the choice controls the underlying frame
     * animation (lava-style glow vs water-style ripple) so cool fluids like blood look right.
     */
    public enum FluidBase {
        /** Lava-style glowing base texture; the default for hot/molten metals. */
        MOLTEN,
        /** Water-style rippling base texture; appropriate for cool fluids like blood. */
        WATER
    }

    private final int harvestLevel;
    private final float miningSpeed;
    private final float attackDamage;
    private final int durabilityPerIngot;
    private final float meltingTemp;
    private final int moltenColor;
    private final int partColor;
    private final float binderMultiplier;
    private final boolean castOnly;
    private final FluidBase fluidBase;
    private final Map<ResourceLocation, Integer> modifierSlots;
    private final Map<ResourceLocation, List<ModifierEffect>> modifiers;
    private final ArmorStats armorStats;

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
        this.fluidBase = b.fluidBase;
        this.modifierSlots = Collections.unmodifiableMap(new HashMap<>(b.modifierSlots));
        this.modifiers = Collections.unmodifiableMap(new HashMap<>(b.modifiers));
        this.armorStats = b.armorStats;
    }

    /**
     * Armor-specific stat block, optional per material.
     *
     * <p>Mirrors Constructs Armory 1.12's split into Core / Plates / Trim. The composed armor
     * piece's durability is {@code slotMult × ((coreDur + trimDur) × platesModifier + platesDur)};
     * defense and toughness are summed across the three parts and then slot-multiplied per
     * Minecraft's armor attribute system. Materials that don't make sense as armor (cast-only
     * fluids, bowstring fibers) leave this null.
     *
     * @param coreDurability   base durability contributed by the core slot
     * @param coreDefense      base armor points contributed by the core slot (pre slot-multiplier)
     * @param platesDurability flat durability bonus added by the plates slot (post-modifier)
     * @param platesModifier   multiplicative scalar applied to (core + trim) durability by the plates slot
     * @param platesToughness  armor toughness contributed by the plates slot (pre slot-multiplier)
     * @param trimDurability   flat durability bonus added by the trim slot (pre-modifier)
     */
    public record ArmorStats(
            float coreDurability,
            float coreDefense,
            float platesDurability,
            float platesModifier,
            float platesToughness,
            float trimDurability
    ) {}

    /** Vanilla-style harvest level: 0=hand, 1=stone, 2=iron, 3=diamond, 4=netherite. */
    public int harvestLevel() { return harvestLevel; }

    /** Base mining speed multiplier this material contributes to a mining head. */
    public float miningSpeed() { return miningSpeed; }

    /** Base attack damage this material contributes to a weapon head. */
    public float attackDamage() { return attackDamage; }

    /** Base durability added per ingot's worth of material when used in an additive part. */
    public int durabilityPerIngot() { return durabilityPerIngot; }

    /** Minimum forge temperature (in degrees Celsius) required to melt this material. */
    public float meltingTemp() { return meltingTemp; }

    /** ARGB color used to tint the auto-generated molten fluid texture. */
    public int moltenColor() { return moltenColor; }

    /** ARGB color used by the item tint handler to color grayscale part textures. */
    public int partColor() { return partColor; }

    /** Durability multiplier applied when this material is used as the binder slot. */
    public float binderMultiplier() { return binderMultiplier; }

    /**
     * If true, this material exists in fluid form only — it doesn't auto-generate Smithery
     * PartItems for the standard part types (blade/guard/handle/etc).
     */
    public boolean castOnly() { return castOnly; }

    /** Animated base texture used by this material's fluid. */
    public FluidBase fluidBase() { return fluidBase; }

    /** Number of post-craft modifier slots this material contributes when used as the given part. */
    public int modifierSlotsFor(PartType partType) {
        return modifierSlots.getOrDefault(partType.id(), 0);
    }

    /** Craft-time modifier effects this material grants when used in the given tool type. */
    public List<ModifierEffect> modifiersFor(ToolType toolType) {
        return modifiers.getOrDefault(toolType.id(), List.of());
    }

    /** Returns the armor-stat block for this material, or {@code null} if armor isn't supported. */
    public ArmorStats armorStats() { return armorStats; }

    /** True iff this material has an attached {@link ArmorStats} block. */
    public boolean supportsArmor() { return armorStats != null; }

    /** Begins building a new {@link MaterialStats}. */
    public static Builder builder() { return new Builder(); }

    private static int darken(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int)(((argb >>> 16) & 0xFF) * 0.7f);
        int g = (int)(((argb >>>  8) & 0xFF) * 0.7f);
        int b = (int)(( argb         & 0xFF) * 0.7f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Fluent builder for {@link MaterialStats}. */
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
        private FluidBase fluidBase = FluidBase.MOLTEN;
        private final Map<ResourceLocation, Integer> modifierSlots = new HashMap<>();
        private final Map<ResourceLocation, List<ModifierEffect>> modifiers = new HashMap<>();
        private ArmorStats armorStats = null;

        /** Sets the vanilla-style harvest level. */
        public Builder harvestLevel(int v) { this.harvestLevel = v; return this; }

        /** Sets the base mining-speed contribution. */
        public Builder miningSpeed(float v) { this.miningSpeed = v; return this; }

        /** Sets the base attack-damage contribution. */
        public Builder attackDamage(float v) { this.attackDamage = v; return this; }

        /** Sets the base durability per ingot for additive parts. */
        public Builder durabilityPerIngot(int v) { this.durabilityPerIngot = v; return this; }

        /** Sets the forge temperature required to melt this material. */
        public Builder meltingTemp(float v) { this.meltingTemp = v; return this; }

        /** Sets the ARGB tint applied to the molten fluid texture. */
        public Builder moltenColor(int argb) { this.moltenColor = argb; return this; }

        /** Sets the ARGB tint applied to part textures (defaults to a darkened molten color). */
        public Builder partColor(int argb) { this.partColor = argb; return this; }

        /** Sets the durability multiplier applied when this material occupies a binder slot. */
        public Builder binderMultiplier(float v) { this.binderMultiplier = v; return this; }

        /** Mark this material as fluid-only (no auto-generated PartItems for standard parts). */
        public Builder castOnly(boolean v) { this.castOnly = v; return this; }

        /** Choose the animated base texture used by this material's fluid. */
        public Builder fluidBase(FluidBase base) { this.fluidBase = Objects.requireNonNull(base); return this; }

        /** Sets the number of post-craft modifier slots this material grants as the given part. */
        public Builder modifierSlots(PartType pt, int slots) {
            this.modifierSlots.put(Objects.requireNonNull(pt).id(), slots);
            return this;
        }

        /** Attaches a craft-time {@link ModifierEffect} for the given tool type. */
        public Builder addModifier(ToolType tt, ModifierEffect effect) {
            this.modifiers.computeIfAbsent(tt.id(), k -> new java.util.ArrayList<>()).add(effect);
            return this;
        }

        /** Convenience overload that wraps a bare modifier id in a parameterless effect. */
        public Builder addModifier(ToolType tt, ResourceLocation modifierId) {
            return addModifier(tt, ModifierEffect.of(modifierId));
        }

        /** Convenience overload that wraps a modifier id and parameter map in an effect. */
        public Builder addModifier(ToolType tt, ResourceLocation modifierId, Map<String, Object> params) {
            return addModifier(tt, ModifierEffect.of(modifierId, params));
        }

        /** Attaches the same craft-time effect to several tool types at once (e.g. every armor piece). */
        public Builder addModifier(ModifierEffect effect, ToolType... toolTypes) {
            for (ToolType tt : toolTypes) addModifier(tt, effect);
            return this;
        }

        /**
         * Attaches an {@link ArmorStats} block, making this material eligible for armor parts.
         *
         * @param coreDurability   core slot's contributed durability
         * @param coreDefense      core slot's contributed defense points (pre slot-multiplier)
         * @param platesDurability plates slot's flat durability bonus
         * @param platesModifier   plates slot's multiplicative scalar on (core + trim) durability
         * @param platesToughness  plates slot's armor-toughness contribution
         * @param trimDurability   trim slot's flat durability bonus
         */
        public Builder armor(float coreDurability, float coreDefense,
                             float platesDurability, float platesModifier,
                             float platesToughness, float trimDurability) {
            this.armorStats = new ArmorStats(coreDurability, coreDefense, platesDurability,
                    platesModifier, platesToughness, trimDurability);
            return this;
        }

        /** Finalizes and returns the built {@link MaterialStats}. */
        public MaterialStats build() { return new MaterialStats(this); }
    }
}
