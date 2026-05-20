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
    public final List<ResolvedEffect> activeEffects;
    public final List<SynergyDefinition> activeSynergies;

    private ToolStats(int maxDurability, float attackDamage, float miningSpeed, int harvestLevel,
                      List<ResolvedEffect> activeEffects, List<SynergyDefinition> activeSynergies) {
        this.maxDurability = maxDurability;
        this.attackDamage = attackDamage;
        this.miningSpeed = miningSpeed;
        this.harvestLevel = harvestLevel;
        this.activeEffects = activeEffects;
        this.activeSynergies = activeSynergies;
    }

    /** A registered Modifier paired with the ModifierEffect (params) that pointed at it. */
    public record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

    public static ToolStats compute(ToolComposition comp) {
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

        // ---- Per-part-type modifiers (each part's material contributes its tool-type modifier) ----
        Modifier.MutablePassiveStats passive = new Modifier.MutablePassiveStats();
        List<ResolvedEffect> active = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Material m = SmitheryAPI.MATERIALS.get(materialIds.get(i));
            if (m == null) continue;
            for (ModifierEffect effect : m.stats().modifiersFor(tt)) {
                Modifier mod = SmitheryAPI.MODIFIERS.get(effect.modifierId());
                if (mod == null) continue;
                applyEffect(mod, effect, passive, active);
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
                    Modifier mod = SmitheryAPI.MODIFIERS.get(effect.modifierId());
                    if (mod == null) continue;
                    synergies.add(s);
                    applyEffect(mod, effect, passive, active);
                }
            }
        }

        int finalDurability = Math.max(1, Math.round(additive * multiplier * passive.durabilityMultiplier));

        // ---- Attack damage: base material damage of the primary additive slot + passive bonuses ----
        float baseDamage = primarySlotMaterialStat(tt, materialIds, MaterialStats::attackDamage);
        float damage = baseDamage + passive.bonusAttackDamage;

        // ---- Mining speed + harvest level: aggregated across all additive slots, weighted ----
        float speed = primarySlotMaterialStat(tt, materialIds, MaterialStats::miningSpeed) + passive.bonusMiningSpeed;
        int harvest = maxAdditiveSlotHarvestLevel(tt, materialIds);

        return new ToolStats(finalDurability, damage, speed, harvest, active, synergies);
    }

    private static void applyEffect(Modifier mod, ModifierEffect effect,
                                    Modifier.MutablePassiveStats passive, List<ResolvedEffect> active) {
        passive.durabilityMultiplier *= mod.durabilityMultiplier();
        if (mod.passive() != null) mod.passive().apply(effect, passive);
        if (mod.onAttack() != null || mod.onBreak() != null) {
            active.add(new ResolvedEffect(mod, effect));
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
        return new ToolStats(1, 0f, 1f, 0, List.of(), List.of());
    }

    /** Compose all parts' modifier names and active synergies into a list for tooltips. */
    public List<Identifier> allModifierIds() {
        List<Identifier> out = new ArrayList<>();
        for (ResolvedEffect r : activeEffects) out.add(r.effect.modifierId());
        return out;
    }

    private ToolStats() { this(1, 0f, 1f, 0, List.of(), List.of()); }
}
