package com.soul.smithery.item.tool;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.content.SmitheryPartTypes;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived tool stats computed from a {@link ToolComposition} plus any post-craft
 * modifiers. Durability follows {@code (sum of additive durabilities) * product of
 * binder multipliers * product of modifier multipliers}; attack damage, mining speed,
 * and harvest level come from the primary additive slot's material.
 */
public final class ToolStats {

    /** Final maximum durability of the composed tool (always >= 1). */
    public final int maxDurability;
    /** Final attack damage including modifier bonuses. */
    public final float attackDamage;
    /** Final mining speed including modifier bonuses. */
    public final float miningSpeed;
    /**
     * Final harvest level, from the head — the primary additive slot — matching the
     * attack and mining-speed stats beside it.
     *
     * <p>This used to take the max across every additive slot, which for a pickaxe
     * includes the handle: a wooden pick head on a duranite handle mined at duranite
     * level. That let a player skip the material ladder entirely by upgrading the
     * cheapest part of the tool, and it contradicted the class contract above.
     * Tinkers' Construct, which this ports, has always read mining level off the head.</p>
     */
    public final int harvestLevel;
    /** Final armor defense points (post slot-multiplier). Zero for non-armor tools. */
    public final float armorDefense;
    /** Final armor toughness (post slot-multiplier). Zero for non-armor tools. */
    public final float armorToughness;
    /** Every modifier effect on this tool, deduped by modifier id (last-wins on collision). */
    public final List<ResolvedEffect> allEffects;
    /** Effects with at least one runtime event callback (onAttack, onBreak, etc). */
    public final List<ResolvedEffect> activeEffects;
    /** Effects with an onCompose callback that runs at composition time. */
    public final List<ResolvedEffect> composeEffects;
    /** Active synergies between the composition's distinct materials. */
    public final List<SynergyDefinition> activeSynergies;
    /** True when any effect's modifier is durability-scaled — stats change as the tool wears. */
    public boolean hasDurabilityScaled;

    /**
     * Ranged stats, meaningful only on tool types with the matching parts. Ported from Tinkers'
     * Construct 1.12, where a composed bow averaged its limbs' {@code BowMaterialStats}: draw rate
     * (higher draws faster), a velocity scalar, and flat damage added to the shot. Non-bows keep
     * the neutral defaults.
     */
    public float drawSpeed = 1f;
    /** Velocity scalar from the bow limbs; 1.0 is the tool type's base projectile speed. */
    public float range = 1f;
    /** Flat damage the limbs add to what the bow fires. */
    public float bonusDamage = 0f;
    /** Fletching accuracy, 1.0 neutral: above tightens the shot, below widens it. */
    public float accuracy = 1f;
    /** Arrows produced per assembly craft, from the shaft's modifier and bonus ammo. */
    public int ammoCount = 1;
    /**
     * Flat attack damage contributed by modifiers alone, excluding the head material's own.
     *
     * <p>Already folded into {@link #attackDamage} for melee. Ranged weapons need it separately:
     * a bow's shot damage comes from the ammo, so adding the whole {@code attackDamage} would
     * double-count the limb's melee stat, but a damage modifier on the bow still has to reach the
     * arrow — 1.12 passed launcher attribute modifiers through
     * {@code BowCore.modifyProjectileAttributes}.</p>
     */
    public float passiveBonusDamage = 0f;

    private ToolStats(int maxDurability, float attackDamage, float miningSpeed, int harvestLevel,
                      float armorDefense, float armorToughness,
                      List<ResolvedEffect> allEffects,
                      List<ResolvedEffect> activeEffects, List<ResolvedEffect> composeEffects,
                      List<SynergyDefinition> activeSynergies) {
        this.maxDurability = maxDurability;
        this.attackDamage = attackDamage;
        this.miningSpeed = miningSpeed;
        this.harvestLevel = harvestLevel;
        this.armorDefense = armorDefense;
        this.armorToughness = armorToughness;
        this.allEffects = allEffects;
        this.activeEffects = activeEffects;
        this.composeEffects = composeEffects;
        this.activeSynergies = activeSynergies;
    }

    /**
     * Per-armor-piece scaling factors keyed by tool-type path. The first three values
     * (durability, defense, toughness) follow Constructs Armory 1.12's pattern, tuned so a
     * baseline iron set lands near vanilla iron armor — vanilla 1.21 armor scale.
     *
     * @param toolPath one of {@code helmet}, {@code chestplate}, {@code leggings}, {@code boots};
     *                 any other value returns null
     * @return three-element float array {@code [durability, defense, toughness]}, or null when
     *         the tool isn't an armor piece
     */
    public static float @org.jetbrains.annotations.Nullable [] armorSlotMultipliers(String toolPath) {
        return switch (toolPath) {
            case "helmet"     -> new float[]{ 0.70f, 0.16f, 1.0f };
            case "chestplate" -> new float[]{ 1.00f, 0.40f, 1.0f };
            case "leggings"   -> new float[]{ 0.90f, 0.30f, 1.0f };
            case "boots"      -> new float[]{ 0.80f, 0.14f, 1.0f };
            default           -> null;
        };
    }

    private static boolean isArmor(ToolType tt) {
        return armorSlotMultipliers(tt.id().getPath()) != null;
    }

    /**
     * A registered {@link Modifier} paired with the {@link ModifierEffect} parameters
     * that pointed at it.
     *
     * @param modifier the resolved registered modifier
     * @param effect   the effect record providing parameter overrides
     */
    public record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

    /**
     * Computes stats from {@code comp} with no post-craft modifiers — equivalent to
     * {@link #compute(ToolComposition, List)} with an empty list.
     */
    public static ToolStats compute(ToolComposition comp) {
        return compute(comp, List.of());
    }

    /**
     * Computes the final tool stats from the composition plus any post-craft modifiers
     * applied via the anvil ({@code APPLIED_MODIFIERS} data component). Material grants,
     * synergies, and applied modifiers are merged with later sources winning collisions
     * by modifier id.
     */
    public static ToolStats compute(ToolComposition comp, List<ModifierEffect> appliedModifiers) {
        return compute(comp, appliedModifiers, 0f);
    }

    /**
     * Full compute with the stack's current wear ({@code missingDurability}, 0..1) exposed to
     * durability-scaled passives (Stonebound-style). Callers without a live stack pass 0.
     */
    public static ToolStats compute(ToolComposition comp, List<ModifierEffect> appliedModifiers,
                                    float missingDurability) {
        ToolType tt = comp.toolType();
        if (tt == null || !comp.isValid()) return broken();

        List<ToolType.Slot> slots = tt.slots();
        List<ResourceLocation> materialIds = comp.slotMaterials();

        boolean armor = isArmor(tt);

        float additive = 0f;
        float multiplier = 1f;
        float armorCoreDur = 0f;
        float armorTrimDur = 0f;
        float armorPlatesDur = 0f;
        float armorPlatesMod = 1f;
        float armorCoreDefenseRaw = 0f;
        float armorPlatesToughRaw = 0f;
        float limbDraw = 0f, limbRange = 0f, limbDamage = 0f;
        float fletchAccuracy = 0f, shaftModifier = 0f;
        int limbCount = 0, fletchCount = 0, shaftCount = 0, shaftBonusAmmo = 0;
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot s = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) return broken();
            MaterialStats ms = m.stats();
            if (armor) {
                MaterialStats.ArmorStats as = ms.armorStats();
                if (as == null) return broken();
                String partPath = s.partType().id().getPath();
                if (partPath.endsWith("_core")) {
                    armorCoreDur += as.coreDurability();
                    armorCoreDefenseRaw += as.coreDefense();
                } else if ("armor_plates".equals(partPath)) {
                    armorPlatesDur += as.platesDurability();
                    armorPlatesMod *= as.platesModifier();
                    armorPlatesToughRaw += as.platesToughness();
                } else if ("armor_trim".equals(partPath)) {
                    armorTrimDur += as.trimDurability();
                }
            } else if (s.role() == DurabilityRole.ADDITIVE) {
                additive += ms.durabilityPerIngot() * s.partType().durabilityScalar();
            } else if (isPart(s, SmitheryPartTypes.BOWSTRING)) {
                // TC 1.12's bowstring contributes its own durability scalar, not a binder value.
                multiplier *= ms.rangedStats().bowstring();
            } else if (isPart(s, SmitheryPartTypes.FLETCHING)) {
                multiplier *= ms.rangedStats().fletchingModifier();
            } else {
                multiplier *= ms.binderMultiplier();
            }

            MaterialStats.RangedStats rs = ms.rangedStats();
            // Only limbs that actually carry bow stats are averaged. TC 1.12 wouldn't let a
            // statless material be a limb at all; here it abstains instead, so a bow built from
            // one keeps the neutral defaults rather than inheriting a zero draw speed.
            if (isPart(s, SmitheryPartTypes.BOW_LIMB)) {
                if (ms.supportsBow()) {
                    limbDraw += rs.drawSpeed();
                    limbRange += rs.range();
                    limbDamage += rs.bonusDamage();
                    limbCount++;
                }
            } else if (isPart(s, SmitheryPartTypes.FLETCHING)) {
                fletchAccuracy += rs.accuracy();
                fletchCount++;
            } else if (isPart(s, SmitheryPartTypes.ARROW_SHAFT)) {
                shaftModifier += rs.shaftModifier();
                shaftBonusAmmo += rs.bonusAmmo();
                shaftCount++;
            }
        }

        java.util.LinkedHashMap<ResourceLocation, ResolvedEffect> effectsMap = new java.util.LinkedHashMap<>();

        // Embossed donor traits collect FIRST so the tool's own material grants, synergies,
        // and applied modifiers all win same-id collisions — embossment grafts flavor, it
        // never overrides something the tool already earns. The donor occupies no slot, so it
        // grafts everything the material could give, head-only traits included.
        comp.embossedMaterial().ifPresent(donorId -> {
            Material donor = SmitheryAPI.MATERIALS.get(donorId);
            if (donor != null) {
                for (ModifierEffect effect : donor.stats().modifiersFor(tt)) {
                    collectInto(effectsMap, effect);
                }
            }
        });

        int headIndex = primaryAdditiveIndex(tt);
        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            for (ModifierEffect effect
                    : m.stats().modifiersFor(tt, slots.get(i).partType(), i == headIndex)) {
                collectInto(effectsMap, effect);
            }
        }

        List<SynergyDefinition> synergies = new ArrayList<>();
        List<ResourceLocation> distinct = comp.distinctMaterials();
        for (int i = 0; i < distinct.size(); i++) {
            for (int j = i + 1; j < distinct.size(); j++) {
                for (SynergyDefinition s : SmitheryAPI.synergiesFor(distinct.get(i), distinct.get(j))) {
                    ModifierEffect effect = s.effectFor(tt);
                    if (effect == null) continue;
                    synergies.add(s);
                    collectInto(effectsMap, effect);
                }
            }
        }

        for (ModifierEffect effect : appliedModifiers) {
            collectInto(effectsMap, effect);
        }

        Modifier.MutablePassiveStats passive = new Modifier.MutablePassiveStats();
        passive.missingDurability = Math.max(0f, Math.min(1f, missingDurability));
        List<ResolvedEffect> all = new ArrayList<>(effectsMap.values());
        List<ResolvedEffect> active = new ArrayList<>();
        List<ResolvedEffect> compose = new ArrayList<>();
        boolean anyDurabilityScaled = false;
        for (ResolvedEffect r : all) {
            applyEffect(r.modifier(), r.effect(), passive, active, compose);
            anyDurabilityScaled |= r.modifier().durabilityScaled();
        }

        int finalDurability;
        float finalDefense = 0f;
        float finalToughness = 0f;
        if (armor) {
            float[] mults = armorSlotMultipliers(tt.id().getPath());
            float durMult = mults != null ? mults[0] : 1f;
            float defMult = mults != null ? mults[1] : 1f;
            float toughMult = mults != null ? mults[2] : 1f;
            float armorRaw = ((armorCoreDur + armorTrimDur) * armorPlatesMod + armorPlatesDur) * durMult;
            finalDurability = Math.max(1, Math.round(armorRaw * passive.durabilityMultiplier));
            finalDefense   = armorCoreDefenseRaw * defMult;
            finalToughness = armorPlatesToughRaw * toughMult;
        } else {
            finalDurability = Math.max(1, Math.round(additive * multiplier * passive.durabilityMultiplier));
        }

        float baseDamage = primarySlotMaterialStat(tt, materialIds, MaterialStats::attackDamage);
        float damage = baseDamage + passive.bonusAttackDamage;

        float speed = primarySlotMaterialStat(tt, materialIds, MaterialStats::miningSpeed) + passive.bonusMiningSpeed;
        int harvest = Math.max((int) primarySlotMaterialStat(tt, materialIds, MaterialStats::harvestLevel),
                passive.minHarvestLevel);

        ToolStats result = new ToolStats(finalDurability, damage, speed, harvest,
                finalDefense, finalToughness, all, active, compose, synergies);
        result.hasDurabilityScaled = anyDurabilityScaled;
        result.passiveBonusDamage = passive.bonusAttackDamage;

        // Limb stats average across the limbs and floor at 0.001 — ProjectileLauncherNBT.limb().
        if (limbCount > 0) {
            result.drawSpeed = Math.max(0.001f, limbDraw / limbCount);
            result.range = Math.max(0.001f, limbRange / limbCount);
            result.bonusDamage = Math.max(0.001f, limbDamage / limbCount);
        }
        if (fletchCount > 0) {
            result.accuracy = Math.max(0.001f, fletchAccuracy / fletchCount);
        }
        if (shaftCount > 0) {
            result.ammoCount = Math.max(1, Math.min(MAX_AMMO_PER_CRAFT,
                    Math.round(BASE_AMMO_PER_CRAFT * (shaftModifier / shaftCount)) + shaftBonusAmmo));
        }
        return result;
    }

    /** True when this slot is filled by the given part type. */
    private static boolean isPart(ToolType.Slot slot, PartType pt) {
        return pt != null && slot.partType().id().equals(pt.id());
    }

    /**
     * Arrows a shaft of modifier 1.0 yields per craft, and the ceiling any shaft can reach.
     *
     * <p>TC 1.12 stored ammo as the arrow's durability and divided by {@code durabilityPerAmmo};
     * Smithery's arrows are damageable items instead, so the shaft's modifier and bonus ammo scale
     * the craft's output count, which is what those stats meant to a player either way.</p>
     */
    private static final int BASE_AMMO_PER_CRAFT = 4;
    private static final int MAX_AMMO_PER_CRAFT = 64;

    private static void collectInto(java.util.LinkedHashMap<ResourceLocation, ResolvedEffect> map,
                                     ModifierEffect effect) {
        Modifier mod = SmitheryAPI.MODIFIERS.get(effect.modifierId());
        if (mod == null) return;
        map.put(effect.modifierId(), new ResolvedEffect(mod, effect));
    }

    private static void applyEffect(Modifier mod, ModifierEffect effect,
                                    Modifier.MutablePassiveStats passive,
                                    List<ResolvedEffect> active,
                                    List<ResolvedEffect> compose) {
        passive.durabilityMultiplier *= mod.durabilityMultiplier();
        if (mod.passive() != null) mod.passive().apply(effect, passive);
        if (mod.onAttack() != null || mod.onBreak() != null
                || mod.onBlockDrops() != null || mod.onKill() != null
                || mod.onMobDrops() != null || mod.onDealDamage() != null
                || mod.hasArmorHooks()) {
            active.add(new ResolvedEffect(mod, effect));
        }
        if (mod.onCompose() != null) {
            compose.add(new ResolvedEffect(mod, effect));
        }
    }

    /**
     * Index of the tool's head — its first additive slot, the one attack damage, mining speed and
     * harvest level are read from. Head-scoped material traits are granted only from this slot.
     *
     * @return the slot index, or -1 for a tool type with no additive slot
     */
    static int primaryAdditiveIndex(ToolType tt) {
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).role() == DurabilityRole.ADDITIVE) return i;
        }
        return -1;
    }

    private static float primarySlotMaterialStat(ToolType tt, List<ResourceLocation> materialIds,
                                                 java.util.function.ToDoubleFunction<MaterialStats> stat) {
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).role() != DurabilityRole.ADDITIVE) continue;
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            return (float) stat.applyAsDouble(m.stats());
        }
        return 0f;
    }

    private static ToolStats broken() {
        return new ToolStats(1, 0f, 1f, 0, 0f, 0f, List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the modifier ids of every active effect on this tool, for tooltip use. */
    public List<ResourceLocation> allModifierIds() {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResolvedEffect r : activeEffects) out.add(r.effect.modifierId());
        return out;
    }

    private ToolStats() { this(1, 0f, 1f, 0, 0f, 0f, List.of(), List.of(), List.of(), List.of()); }
}
