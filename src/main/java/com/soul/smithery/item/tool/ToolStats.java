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
    /** Final harvest level, taken as the max across additive slots. */
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
    public static float @org.jspecify.annotations.Nullable [] armorSlotMultipliers(String toolPath) {
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
            } else {
                multiplier *= ms.binderMultiplier();
            }
        }

        java.util.LinkedHashMap<ResourceLocation, ResolvedEffect> effectsMap = new java.util.LinkedHashMap<>();

        // Embossed donor traits collect FIRST so the tool's own material grants, synergies,
        // and applied modifiers all win same-id collisions — embossment grafts flavor, it
        // never overrides something the tool already earns.
        comp.embossedMaterial().ifPresent(donorId -> {
            Material donor = SmitheryAPI.MATERIALS.get(donorId);
            if (donor != null) {
                for (ModifierEffect effect : donor.stats().modifiersFor(tt)) {
                    collectInto(effectsMap, effect);
                }
            }
        });

        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            for (ModifierEffect effect : m.stats().modifiersFor(tt)) {
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
        int harvest = maxAdditiveSlotHarvestLevel(tt, materialIds);

        ToolStats result = new ToolStats(finalDurability, damage, speed, harvest,
                finalDefense, finalToughness, all, active, compose, synergies);
        result.hasDurabilityScaled = anyDurabilityScaled;
        return result;
    }

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

    private static int maxAdditiveSlotHarvestLevel(ToolType tt, List<ResourceLocation> materialIds) {
        List<ToolType.Slot> slots = tt.slots();
        int max = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).role() != DurabilityRole.ADDITIVE) continue;
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            max = Math.max(max, m.stats().harvestLevel());
        }
        return max;
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
