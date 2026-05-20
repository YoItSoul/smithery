package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.synergy.SynergyDefinition;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Built-in starting synergies: bonus modifiers a tool gets for free when a specific pair of
 * materials appears across its parts. Synergies do not consume modifier slots.
 *
 * Each synergy maps a (materialA, materialB) pair to per-ToolType effects. A synergy that
 * has no effect for a given tool type simply omits that entry.
 */
public final class SmitherySynergies {
    public static SynergyDefinition GALVANIC;
    public static SynergyDefinition GILDED;
    public static SynergyDefinition VERDANT_VEIL;

    public static void register() {
        // Iron × Copper → Galvanic
        // Sword: bonus damage vs targets in water or rain.
        // Pickaxe: no durability cost while mining underwater (Phase 6 hook).
        GALVANIC = SmitheryAPI.registerSynergy(SynergyDefinition.builder(id("galvanic"))
                .materials(SmitheryMaterials.IRON, SmitheryMaterials.COPPER)
                .addEffect(SmitheryToolTypes.SWORD, SmitheryModifiers.SHARP,
                        Map.of("damage", 2.0f, "wet_only", true))
                .build());

        // Iron × Gold → Gilded (synergy version, +XP across the board)
        GILDED = SmitheryAPI.registerSynergy(SynergyDefinition.builder(id("gilded"))
                .materials(SmitheryMaterials.IRON, SmitheryMaterials.GOLD)
                .addEffect(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE,
                        Map.of("xp_multiplier", 1.25f))
                .addEffect(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED,
                        Map.of("xp_multiplier", 1.25f))
                .build());

        // Copper × Gold → Verdant Veil
        VERDANT_VEIL = SmitheryAPI.registerSynergy(SynergyDefinition.builder(id("verdant_veil"))
                .materials(SmitheryMaterials.COPPER, SmitheryMaterials.GOLD)
                .addEffect(SmitheryToolTypes.SWORD, SmitheryModifiers.VERDANT,
                        Map.of("chance", 0.15f, "duration_ticks", 60))
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitherySynergies() {}
}
