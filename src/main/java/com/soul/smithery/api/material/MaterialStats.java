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
 * binder behavior, fluid base, per-{@link PartType} modifier slot counts, and the material's
 * craft-time modifier effects. Constructed through {@link Builder}.
 *
 * <p>Effects carry one of three scopes, mirroring Tinkers' Construct 1.12: universal traits,
 * granted from any part of any tool; head traits, granted only from the tool's head; and effects
 * keyed to a single {@link ToolType}, which is Smithery's own extension and exists for
 * armor-piece-specific traits.
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
    private final int[] colorCycle;
    private final int colorCyclePeriodTicks;
    private final boolean foil;
    private final boolean emissive;
    private final float binderMultiplier;
    private final boolean castOnly;
    private final boolean storageForms;
    private final ResourceLocation boundFluid;
    private final FluidBase fluidBase;
    private final Map<ResourceLocation, Integer> modifierSlots;
    private final Map<ResourceLocation, List<ModifierEffect>> modifiers;
    private final Map<ResourceLocation, List<ModifierEffect>> partModifiers;
    private final List<ModifierEffect> universalModifiers;
    private final List<ModifierEffect> headModifiers;
    private final ArmorStats armorStats;
    private final RangedStats rangedStats;

    private MaterialStats(Builder b) {
        this.harvestLevel = b.harvestLevel;
        this.miningSpeed = b.miningSpeed;
        this.attackDamage = b.attackDamage;
        this.durabilityPerIngot = b.durabilityPerIngot;
        this.meltingTemp = b.meltingTemp;
        // The default molten gray only stands when no partColor was given either — most
        // materials declare only partColor, and gray molten fluids for all of them was a
        // long-standing visual bug. Derived molten color is the part color pushed toward
        // white for a heat-glow read.
        this.moltenColor = (b.moltenColor == Builder.DEFAULT_MOLTEN_COLOR && b.partColor != 0)
                ? brighten(b.partColor) : b.moltenColor;
        this.partColor = b.partColor != 0 ? b.partColor : darken(b.moltenColor);
        this.colorCycle = b.colorCycle.clone();
        this.colorCyclePeriodTicks = b.colorCyclePeriodTicks;
        this.foil = b.foil;
        this.emissive = b.emissive;
        this.binderMultiplier = b.binderMultiplier;
        this.castOnly = b.castOnly;
        this.storageForms = b.storageForms;
        this.boundFluid = b.boundFluid;
        this.fluidBase = b.fluidBase;
        this.modifierSlots = Collections.unmodifiableMap(new HashMap<>(b.modifierSlots));
        this.modifiers = Collections.unmodifiableMap(new HashMap<>(b.modifiers));
        this.partModifiers = Collections.unmodifiableMap(new HashMap<>(b.partModifiers));
        this.universalModifiers = List.copyOf(b.universalModifiers);
        this.headModifiers = List.copyOf(b.headModifiers);
        this.armorStats = b.armorStats;
        this.rangedStats = b.rangedStats;
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

    /**
     * Ranged-specific stat block, optional per material. Ports Tinkers' Construct 1.12's four
     * ranged stat types, which the melee stats above have no room for.
     *
     * <p>{@code drawSpeed}/{@code range}/{@code bonusDamage} are TC's {@code BowMaterialStats} and
     * are read from bow limbs; a composed bow averages them across its limbs. {@code bowstring} is
     * {@code BowStringMaterialStats.modifier}, a durability scalar. {@code shaftModifier} and
     * {@code bonusAmmo} are {@code ArrowShaftMaterialStats} and decide how many arrows a craft
     * yields, and {@code accuracy}/{@code fletchingModifier} are {@code FletchingMaterialStats}.
     *
     * <p>A material that isn't usable as a limb leaves {@code drawSpeed} at 0, which reads as
     * "no bow stats" — {@link #supportsBow()}.
     *
     * @param drawSpeed         ticks-per-tick draw rate; 1.0 draws in the tool type's base time
     * @param range             velocity scalar applied to the fired projectile
     * @param bonusDamage       flat damage added to what the bow fires
     * @param bowstring         durability scalar contributed by this material as a bowstring
     * @param shaftModifier     ammo-count scalar contributed by this material as an arrow shaft
     * @param bonusAmmo         flat extra arrows contributed by this material as an arrow shaft
     * @param accuracy          0..1 accuracy contributed by this material as fletching (1 = perfect)
     * @param fletchingModifier ammo-count scalar contributed by this material as fletching
     */
    public record RangedStats(
            float drawSpeed,
            float range,
            float bonusDamage,
            float bowstring,
            float shaftModifier,
            int bonusAmmo,
            float accuracy,
            float fletchingModifier
    ) {
        /** Neutral block: no bow stats, and multipliers that leave a composition unchanged. */
        public static final RangedStats NONE =
                new RangedStats(0f, 0f, 0f, 1f, 1f, 0, 1f, 1f);
    }

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

    /**
     * ARGB keyframe colors this material's tint cycles through over time, in order.
     * Empty (the default) means the static {@link #partColor()} is always used.
     *
     * <p>The animated color is resolved client-side by
     * {@code MaterialColorAnimator.currentColor(stats)}; on the server (and for materials
     * without a cycle) that resolver falls back to {@link #partColor()}, so the cycle is a
     * pure render-time effect with no gameplay impact.
     *
     * @return a defensive copy of the keyframe array; never null
     */
    public int[] colorCycle() { return colorCycle.clone(); }

    /** Raw (uncopied) keyframe array — for hot render-path use only; callers must not mutate. */
    public int[] colorCycleRaw() { return colorCycle; }

    /** Full duration of one color cycle in client ticks (20 ticks = 1 second). */
    public int colorCyclePeriodTicks() { return colorCyclePeriodTicks; }

    /** True iff this material animates its tint (two or more cycle keyframes declared). */
    public boolean hasColorCycle() { return colorCycle.length >= 2; }

    /** True iff parts and gear made of this material render with the enchantment-glint shimmer. */
    public boolean foil() { return foil; }

    /**
     * True iff parts (and composed gear containing this material) render full-bright,
     * ignoring world light — used for materials whose source item glows.
     */
    public boolean emissive() { return emissive; }

    /** True iff this material declares any render effect (cycle, foil, or emissive). */
    public boolean hasRenderEffects() { return hasColorCycle() || foil || emissive; }

    /** Durability multiplier applied when this material is used as the binder slot. */
    public float binderMultiplier() { return binderMultiplier; }

    /**
     * If true, this material exists in fluid form only — it doesn't auto-generate Smithery
     * PartItems for the standard part types (blade/guard/handle/etc).
     */
    public boolean castOnly() { return castOnly; }

    /**
     * If true, Smithery generates a storage ingot and block for this material when the owning mod
     * calls {@code SmitheryItems.registerDeclaredForms}. Intended for materials no mod supplies an
     * item for — an alloy that only ever exists as a molten fluid otherwise has nowhere to live and
     * nothing to melt back into itself.
     */
    public boolean storageForms() { return storageForms; }

    /**
     * Id of an already-registered fluid this material pours as, or null when Smithery should mint
     * it a molten fluid of its own. See {@link Builder#boundFluid(ResourceLocation)}.
     */
    public ResourceLocation boundFluid() { return boundFluid; }

    /** Animated base texture used by this material's fluid. */
    public FluidBase fluidBase() { return fluidBase; }

    /** Number of post-craft modifier slots this material contributes when used as the given part. */
    public int modifierSlotsFor(PartType partType) {
        return modifierSlots.getOrDefault(partType.id(), 0);
    }

    /**
     * Every craft-time modifier effect this material can grant on the given tool type, from any
     * slot — the union of its universal traits, its head traits, the part traits for every part
     * this tool type uses, and its tool-type-specific traits.
     *
     * <p>This is the "what could this material give me" view, for tooltips, JEI and embossment.
     * Stat computation walks slots and wants {@link #modifiersFor(ToolType, PartType, boolean)}
     * instead, so a bow-limb trait is not granted by a material sitting in the bowstring.</p>
     */
    public List<ModifierEffect> modifiersFor(ToolType toolType) {
        List<ModifierEffect> out = new java.util.ArrayList<>(modifiersFor(toolType, null, true));
        if (toolType.usesMaterialTraits()) {
            for (ToolType.Slot slot : toolType.slots()) {
                for (ModifierEffect e : partModifiers.getOrDefault(slot.partType().id(), List.of())) {
                    if (!out.contains(e)) out.add(e);
                }
            }
        }
        return out;
    }

    /**
     * Craft-time modifier effects this material grants from one slot of the given tool type.
     *
     * <p>Trait scope follows Tinkers' Construct 1.12, which this ports. There a trait was
     * registered against a material stat type — {@code addTrait(trait)} applied from any part,
     * {@code addTrait(trait, "head")} only from a head part, {@code addTrait(trait, "bow")} only
     * from a bow limb, and so on — so a material could carry one trait as a blade and a different
     * one as a bowstring. {@code partType} is that stat type, and {@code headSlot} preserves the
     * narrower "this is the tool's head" distinction for traits that read the head's own stats
     * (Stonebound, Momentum). Tool-type-keyed effects (see {@link Builder#addModifier(ToolType,
     * ModifierEffect)}) are Smithery's own extension, used for armor-piece-specific traits, and
     * apply from any slot of that type.</p>
     *
     * @param partType the part this material occupies, or null to skip part-scoped traits
     * @param headSlot true when the material occupies the tool's head — its first additive slot
     */
    public List<ModifierEffect> modifiersFor(ToolType toolType, PartType partType, boolean headSlot) {
        List<ModifierEffect> keyed = modifiers.getOrDefault(toolType.id(), List.of());
        if (!toolType.usesMaterialTraits()) return keyed;
        List<ModifierEffect> head = headSlot ? headModifiers : List.<ModifierEffect>of();
        List<ModifierEffect> part = partType == null
                ? List.<ModifierEffect>of()
                : partModifiers.getOrDefault(partType.id(), List.of());
        if (universalModifiers.isEmpty() && head.isEmpty() && part.isEmpty()) return keyed;
        if (keyed.isEmpty() && head.isEmpty() && part.isEmpty()) return universalModifiers;
        List<ModifierEffect> merged = new java.util.ArrayList<>(
                universalModifiers.size() + head.size() + part.size() + keyed.size());
        merged.addAll(universalModifiers);
        merged.addAll(head);
        merged.addAll(part);
        merged.addAll(keyed);
        return merged;
    }

    /**
     * Effects keyed to this tool type alone, excluding universal, head and part traits — the view
     * a display needs when it lists universal traits separately instead of once per tool type.
     */
    public List<ModifierEffect> toolTypeModifiers(ToolType toolType) {
        return modifiers.getOrDefault(toolType.id(), List.of());
    }

    /** Traits this material grants from any part of any tool — TC's {@code addTrait(trait)}. */
    public List<ModifierEffect> universalModifiers() { return universalModifiers; }

    /** Traits this material grants only as a tool's head — TC's {@code addTrait(trait, "head")}. */
    public List<ModifierEffect> headModifiers() { return headModifiers; }

    /**
     * Traits this material grants only when used as the given part — TC's
     * {@code addTrait(trait, "bow")}, {@code addTrait(trait, "fletching")} and friends.
     */
    public List<ModifierEffect> partModifiers(PartType partType) {
        return partModifiers.getOrDefault(partType.id(), List.of());
    }

    /** Returns the armor-stat block for this material, or {@code null} if armor isn't supported. */
    public ArmorStats armorStats() { return armorStats; }

    /** True iff this material has an attached {@link ArmorStats} block. */
    public boolean supportsArmor() { return armorStats != null; }

    /** Ranged stat block; never null — materials without one get {@link RangedStats#NONE}. */
    public RangedStats rangedStats() { return rangedStats; }

    /** True iff this material carries bow-limb stats (TC 1.12's BowMaterialStats). */
    public boolean supportsBow() { return rangedStats.drawSpeed() > 0f; }

    /** Begins building a new {@link MaterialStats}. */
    public static Builder builder() { return new Builder(); }

    /**
     * A builder pre-loaded with this stat block, for deriving a variant of an existing material.
     *
     * <p>Exists so an integration mod can retune one of Smithery's own materials — a pack whose
     * progression puts bedrock far beyond vanilla tiers, say — without restating the fields it
     * does not care about, and without silently dropping the traits the base material already
     * carries. See {@code SmitheryAPI.retuneMaterial}.</p>
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.harvestLevel = harvestLevel;
        b.miningSpeed = miningSpeed;
        b.attackDamage = attackDamage;
        b.durabilityPerIngot = durabilityPerIngot;
        b.meltingTemp = meltingTemp;
        b.moltenColor = moltenColor;
        b.partColor = partColor;
        b.colorCycle = colorCycle.clone();
        b.colorCyclePeriodTicks = colorCyclePeriodTicks;
        b.foil = foil;
        b.emissive = emissive;
        b.binderMultiplier = binderMultiplier;
        b.castOnly = castOnly;
        b.storageForms = storageForms;
        b.boundFluid = boundFluid;
        b.fluidBase = fluidBase;
        b.armorStats = armorStats;
        b.rangedStats = rangedStats;
        b.modifierSlots.putAll(modifierSlots);
        for (Map.Entry<ResourceLocation, List<ModifierEffect>> e : modifiers.entrySet()) {
            b.modifiers.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        for (Map.Entry<ResourceLocation, List<ModifierEffect>> e : partModifiers.entrySet()) {
            b.partModifiers.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        b.universalModifiers.addAll(universalModifiers);
        b.headModifiers.addAll(headModifiers);
        return b;
    }

    private static int darken(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int)(((argb >>> 16) & 0xFF) * 0.7f);
        int g = (int)(((argb >>>  8) & 0xFF) * 0.7f);
        int b = (int)(( argb         & 0xFF) * 0.7f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Pushes a color 35% toward white — molten metal reads hotter than its solid form. */
    private static int brighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        r += (255 - r) * 35 / 100;
        g += (255 - g) * 35 / 100;
        b += (255 - b) * 35 / 100;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Fluent builder for {@link MaterialStats}. */
    public static final class Builder {
        /** Sentinel gray: an unchanged moltenColor means "derive from partColor" at build. */
        static final int DEFAULT_MOLTEN_COLOR = 0xFFAAAAAA;

        private int harvestLevel = 0;
        private float miningSpeed = 1.0f;
        private float attackDamage = 0.0f;
        private int durabilityPerIngot = 60;
        private float meltingTemp = 1000f;
        private int moltenColor = DEFAULT_MOLTEN_COLOR;
        private int partColor = 0;
        private int[] colorCycle = new int[0];
        private int colorCyclePeriodTicks = 60;
        private boolean foil = false;
        private boolean emissive = false;
        private float binderMultiplier = 1.0f;
        private boolean castOnly = false;
        private boolean storageForms = false;
        private ResourceLocation boundFluid = null;
        private FluidBase fluidBase = FluidBase.MOLTEN;
        private final Map<ResourceLocation, Integer> modifierSlots = new HashMap<>();
        private final Map<ResourceLocation, List<ModifierEffect>> modifiers = new HashMap<>();
        private final Map<ResourceLocation, List<ModifierEffect>> partModifiers = new HashMap<>();
        private final List<ModifierEffect> universalModifiers = new java.util.ArrayList<>();
        private final List<ModifierEffect> headModifiers = new java.util.ArrayList<>();
        private ArmorStats armorStats = null;
        private RangedStats rangedStats = RangedStats.NONE;

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

        /**
         * Declares an animated tint: the part color smoothly cycles through {@code argbColors}
         * over {@code periodTicks} client ticks, looping. Pass at least two colors; a single
         * color (or none) leaves the material on its static {@link #partColor(int)}.
         *
         * <p>Render-time only — server-side stat logic always sees the static part color.
         *
         * @param periodTicks full loop duration in ticks (clamped to at least 2)
         * @param argbColors  ordered ARGB keyframes to cycle through
         */
        public Builder colorCycle(int periodTicks, int... argbColors) {
            this.colorCyclePeriodTicks = Math.max(2, periodTicks);
            this.colorCycle = argbColors != null ? argbColors.clone() : new int[0];
            return this;
        }

        /** Renders parts and gear of this material with the enchantment-glint shimmer. */
        public Builder foil() { return foil(true); }

        /** Sets whether parts and gear of this material render with the enchantment glint. */
        public Builder foil(boolean v) { this.foil = v; return this; }

        /** Renders parts (and gear containing this material) full-bright, ignoring world light. */
        public Builder emissive() { return emissive(true); }

        /** Sets whether parts and gear of this material render full-bright. */
        public Builder emissive(boolean v) { this.emissive = v; return this; }

        /** Sets the durability multiplier applied when this material occupies a binder slot. */
        public Builder binderMultiplier(float v) { this.binderMultiplier = v; return this; }

        /** Mark this material as fluid-only (no auto-generated PartItems for standard parts). */
        public Builder castOnly(boolean v) { this.castOnly = v; return this; }

        /**
         * Pour this material as an existing fluid instead of minting it a molten one.
         *
         * <p>For materials whose fluid the game already has. Melting, alloying and the forge tank
         * stay material-keyed exactly as before — only the fluid the forge hands out changes, so
         * what the forge drains is the real thing and works wherever that fluid works. Molten lava
         * is the motivating case: bound to {@code minecraft:lava} it pours back into a fuel port,
         * which a separate Smithery-minted lava never could.
         *
         * <p>The flowing variant, block and bucket are read off the bound fluid, so it must be a
         * {@link net.minecraft.world.level.material.FlowingFluid} with a bucket — a source fluid,
         * not a flowing one. Smithery mints no fluid, block or bucket for the material, and the
         * bound fluid's own texture and behaviour apply, so {@code moltenColor}, {@code fluidBase}
         * and the colour-cycle sprites are all ignored. A melting temperature is still required:
         * it gates when the forge will melt into this material.
         *
         * @param fluidId registry id of the source fluid to pour, or null to mint one as usual
         */
        public Builder boundFluid(ResourceLocation fluidId) { this.boundFluid = fluidId; return this; }

        /**
         * Ask Smithery to generate a storage ingot and block for this material.
         *
         * <p>Use it when nothing in the pack supplies an item for the material — most often an alloy
         * that only exists as a fluid. The forms are tinted with {@link #partColor(int)}, craft 9:1
         * both ways, and melt back at ingot and block volume.</p>
         */
        public Builder storageForms() { return storageForms(true); }

        /** Sets whether Smithery generates a storage ingot and block for this material. */
        public Builder storageForms(boolean v) { this.storageForms = v; return this; }

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
         * Grants this effect from any part of any tool — the port of Tinkers' Construct 1.12
         * {@code Material.addTrait(trait)}, which is how most TC materials carried their traits.
         *
         * <p>Prefer this over enumerating tool types for a material trait: a flint guard gives
         * its trait on a rapier exactly as a flint blade does on a sword, which is the behavior
         * every 1.12 pack was built around.</p>
         */
        public Builder addUniversalModifier(ModifierEffect effect) {
            this.universalModifiers.add(effect);
            return this;
        }

        /** Convenience overload wrapping a bare modifier id. */
        public Builder addUniversalModifier(ResourceLocation modifierId) {
            return addUniversalModifier(ModifierEffect.of(modifierId));
        }

        /** Convenience overload wrapping a modifier id and parameter map. */
        public Builder addUniversalModifier(ResourceLocation modifierId, Map<String, Object> params) {
            return addUniversalModifier(ModifierEffect.of(modifierId, params));
        }

        /**
         * Grants this effect only when the material is the tool's head — its first additive slot —
         * on any tool type. Ports TC 1.12's {@code Material.addTrait(trait, "head")}, which it used
         * for traits that read off the head's own stats (Stonebound, Momentum).
         */
        public Builder addHeadModifier(ModifierEffect effect) {
            this.headModifiers.add(effect);
            return this;
        }

        /** Convenience overload wrapping a bare modifier id. */
        public Builder addHeadModifier(ResourceLocation modifierId) {
            return addHeadModifier(ModifierEffect.of(modifierId));
        }

        /** Convenience overload wrapping a modifier id and parameter map. */
        public Builder addHeadModifier(ResourceLocation modifierId, Map<String, Object> params) {
            return addHeadModifier(ModifierEffect.of(modifierId, params));
        }

        /**
         * Grants this effect only when the material occupies the given part, on any tool type that
         * uses that part. Ports TC 1.12's part-scoped {@code Material.addTrait(trait, statsId)} —
         * {@code "bow"}, {@code "bowstring"}, {@code "shaft"}, {@code "fletching"},
         * {@code "handle"}, {@code "extra"} and friends.
         *
         * <p>Use this when a material's trait genuinely differs by part: a wood that is Ecological
         * as a bow limb but carries nothing as fletching. When the same trait is registered against
         * every stat type the material has, {@link #addUniversalModifier(ModifierEffect)} says so
         * more cheaply and survives new part types being added later.</p>
         */
        public Builder addPartModifier(PartType partType, ModifierEffect effect) {
            this.partModifiers.computeIfAbsent(partType.id(), k -> new java.util.ArrayList<>()).add(effect);
            return this;
        }

        /** Convenience overload wrapping a bare modifier id. */
        public Builder addPartModifier(PartType partType, ResourceLocation modifierId) {
            return addPartModifier(partType, ModifierEffect.of(modifierId));
        }

        /** Convenience overload wrapping a modifier id and parameter map. */
        public Builder addPartModifier(PartType partType, ResourceLocation modifierId,
                                       Map<String, Object> params) {
            return addPartModifier(partType, ModifierEffect.of(modifierId, params));
        }

        /**
         * Attaches the same effect to several parts at once — the common shape when one 1.12 stat
         * type maps to a family of 1.20 parts (TC's single {@code "head"} stat covers blade, pick
         * head, axe head and the rest).
         */
        public Builder addPartModifier(ModifierEffect effect, PartType... partTypes) {
            for (PartType pt : partTypes) addPartModifier(pt, effect);
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

        /**
         * Declares this material usable as a bow limb, with TC 1.12's {@code BowMaterialStats}.
         *
         * @param drawSpeed   draw rate; a composed bow draws in {@code baseDrawTime / drawSpeed} ticks
         * @param range       velocity scalar on what the bow fires
         * @param bonusDamage flat damage the limb adds to the shot
         */
        public Builder bow(float drawSpeed, float range, float bonusDamage) {
            this.rangedStats = new RangedStats(drawSpeed, range, bonusDamage,
                    rangedStats.bowstring(), rangedStats.shaftModifier(), rangedStats.bonusAmmo(),
                    rangedStats.accuracy(), rangedStats.fletchingModifier());
            return this;
        }

        /** Durability scalar this material contributes as a bowstring — TC's BowStringMaterialStats. */
        public Builder bowstring(float modifier) {
            this.rangedStats = new RangedStats(rangedStats.drawSpeed(), rangedStats.range(),
                    rangedStats.bonusDamage(), modifier, rangedStats.shaftModifier(),
                    rangedStats.bonusAmmo(), rangedStats.accuracy(), rangedStats.fletchingModifier());
            return this;
        }

        /**
         * Ammo yield this material contributes as an arrow shaft — TC's ArrowShaftMaterialStats,
         * where an arrow's "durability" is the number of arrows the craft produces.
         */
        public Builder arrowShaft(float modifier, int bonusAmmo) {
            this.rangedStats = new RangedStats(rangedStats.drawSpeed(), rangedStats.range(),
                    rangedStats.bonusDamage(), rangedStats.bowstring(), modifier, bonusAmmo,
                    rangedStats.accuracy(), rangedStats.fletchingModifier());
            return this;
        }

        /** Accuracy and ammo scalar this material contributes as fletching — TC's FletchingMaterialStats. */
        public Builder fletching(float accuracy, float modifier) {
            this.rangedStats = new RangedStats(rangedStats.drawSpeed(), rangedStats.range(),
                    rangedStats.bonusDamage(), rangedStats.bowstring(), rangedStats.shaftModifier(),
                    rangedStats.bonusAmmo(), accuracy, modifier);
            return this;
        }

        /** Finalizes and returns the built {@link MaterialStats}. */
        public MaterialStats build() { return new MaterialStats(this); }
    }
}
