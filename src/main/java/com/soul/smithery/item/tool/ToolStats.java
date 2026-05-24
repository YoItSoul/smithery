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
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives final tool stats from a ToolComposition.
 *
 * Durability formula (from design doc):
 *   durability = (Σ ADDITIVE part durabilities) × Π MULTIPLIER part multipliers × Π modifier multipliers
 *
 * Where:
 *   - additive part durability = material.durabilityPerIngot × partType.durabilityScalar
 *   - multiplier slots use material.binderMultiplier
 *   - modifier multipliers come from all applied modifiers (at-craft + post-craft + synergies)
 *
 * Attack damage, mining speed, harvest level: weighted by which parts each tool type
 * considers "primary." Sword damage = blade material; pickaxe speed/harvest level = head
 * material (weighted average if multiple heads).
 */
public final class ToolStats {

    public final int maxDurability;
    public final float attackDamage;
    public final float miningSpeed;
    public final int harvestLevel;
    /** All modifier effects on this tool, deduped by modifier id (last-wins on collision). */
    public final List<ResolvedEffect> allEffects;
    /** Subset of allEffects with at least one runtime event callback (onAttack/onBreak/etc). */
    public final List<ResolvedEffect> activeEffects;
    /** Subset of allEffects with an onCompose callback. */
    public final List<ResolvedEffect> composeEffects;
    public final List<SynergyDefinition> activeSynergies;

    private ToolStats(int maxDurability, float attackDamage, float miningSpeed, int harvestLevel,
                      List<ResolvedEffect> allEffects,
                      List<ResolvedEffect> activeEffects, List<ResolvedEffect> composeEffects,
                      List<SynergyDefinition> activeSynergies) {
        this.maxDurability = maxDurability;
        this.attackDamage = attackDamage;
        this.miningSpeed = miningSpeed;
        this.harvestLevel = harvestLevel;
        this.allEffects = allEffects;
        this.activeEffects = activeEffects;
        this.composeEffects = composeEffects;
        this.activeSynergies = activeSynergies;
    }

    /** A registered Modifier paired with the ModifierEffect (params) that pointed at it. */
    public record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

    /** Legacy overload — composition only, no post-craft modifiers. */
    public static ToolStats compute(ToolComposition comp) {
        return compute(comp, List.of());
    }

    /**
     * Computes final tool stats from the composition AND any post-craft modifiers applied
     * via the anvil (stored in the {@code APPLIED_MODIFIERS} data component). Post-craft
     * effects layer on top of at-craft effects with the same passive/active treatment —
     * passive ones bake into stats here, active ones land in {@link #activeEffects} for
     * the event router to fire at attack / break time.
     */
    public static ToolStats compute(ToolComposition comp, List<ModifierEffect> appliedModifiers) {
        ToolType tt = comp.toolType();
        if (tt == null || !comp.isValid()) return broken();

        List<ToolType.Slot> slots = tt.slots();
        List<Identifier> materialIds = comp.slotMaterials();

        // ---- Durability ----
        float additive = 0f;
        float multiplier = 1f;
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot s = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) return broken();
            MaterialStats ms = m.stats();
            if (s.role() == DurabilityRole.ADDITIVE) {
                additive += ms.durabilityPerIngot() * s.partType().durabilityScalar();
            } else {
                multiplier *= ms.binderMultiplier();
            }
        }

        // ---- Collect all modifier effects, deduped by modifier id ----
        //
        // Iteration order matters: material grants first, then synergies, then post-craft
        // applied modifiers. The LinkedHashMap put() overwrites by id — later sources win,
        // so a player-applied anvil modifier overrides a material grant of the same id,
        // and a synergy overrides a material grant.
        //
        // Net effect for the tooltip-duplicate bug: when (iron + ender) both grant MAGNETIZED
        // for a pickaxe, the deduped map has ONE Magnetized entry. The runtime callback
        // fires once. Param values come from whoever wrote last (in this case, ender).
        java.util.LinkedHashMap<Identifier, ResolvedEffect> effectsMap = new java.util.LinkedHashMap<>();

        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            for (ModifierEffect effect : m.stats().modifiersFor(tt)) {
                collectInto(effectsMap, effect);
            }
        }

        // ---- Synergies (per distinct pair of materials present) ----
        List<SynergyDefinition> synergies = new ArrayList<>();
        List<Identifier> distinct = comp.distinctMaterials();
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

        // ---- Post-craft applied modifiers (from APPLIED_MODIFIERS component) — last, so they win ----
        for (ModifierEffect effect : appliedModifiers) {
            collectInto(effectsMap, effect);
        }

        // ---- Now compute passive stats + bucket the effects for runtime / tooltip ----
        Modifier.MutablePassiveStats passive = new Modifier.MutablePassiveStats();
        List<ResolvedEffect> all = new ArrayList<>(effectsMap.values());
        List<ResolvedEffect> active = new ArrayList<>();
        List<ResolvedEffect> compose = new ArrayList<>();
        for (ResolvedEffect r : all) {
            applyEffect(r.modifier(), r.effect(), passive, active, compose);
        }

        int finalDurability = Math.max(1, Math.round(additive * multiplier * passive.durabilityMultiplier));

        // ---- Attack damage: base material damage of the primary additive slot + passive bonuses ----
        float baseDamage = primarySlotMaterialStat(tt, materialIds, MaterialStats::attackDamage);
        float damage = baseDamage + passive.bonusAttackDamage;

        // ---- Mining speed + harvest level: aggregated across all additive slots, weighted ----
        float speed = primarySlotMaterialStat(tt, materialIds, MaterialStats::miningSpeed) + passive.bonusMiningSpeed;
        int harvest = maxAdditiveSlotHarvestLevel(tt, materialIds);

        return new ToolStats(finalDurability, damage, speed, harvest, all, active, compose, synergies);
    }

    /** Look up the modifier for an effect and store/overwrite in the dedupe map. */
    private static void collectInto(java.util.LinkedHashMap<Identifier, ResolvedEffect> map,
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
                || mod.onMobDrops() != null) {
            active.add(new ResolvedEffect(mod, effect));
        }
        if (mod.onCompose() != null) {
            compose.add(new ResolvedEffect(mod, effect));
        }
    }

    /** Picks the first additive slot's material and reads the given stat from it. */
    private static float primarySlotMaterialStat(ToolType tt, List<Identifier> materialIds,
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

    private static int maxAdditiveSlotHarvestLevel(ToolType tt, List<Identifier> materialIds) {
        List<ToolType.Slot> slots = tt.slots();
        int max = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).role() != DurabilityRole.ADDITIVE) continue;
            // Heads/blades matter for harvest level; binders and handles do not.
            // For simplicity all ADDITIVE slots contribute, taking the maximum.
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            max = Math.max(max, m.stats().harvestLevel());
        }
        return max;
    }

    private static ToolStats broken() {
        return new ToolStats(1, 0f, 1f, 0, List.of(), List.of(), List.of(), List.of());
    }

    /** Compose all parts' modifier names and active synergies into a list for tooltips. */
    public List<Identifier> allModifierIds() {
        List<Identifier> out = new ArrayList<>();
        for (ResolvedEffect r : activeEffects) out.add(r.effect.modifierId());
        return out;
    }

    private ToolStats() { this(1, 0f, 1f, 0, List.of(), List.of(), List.of(), List.of()); }
}
