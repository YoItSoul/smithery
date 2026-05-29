package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.synergy.SynergyDefinition;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Built-in two-material synergies that grant bonus modifiers without consuming modifier slots.
 *
 * <p>Each synergy maps a material pair to per-{@link com.soul.smithery.api.tool.ToolType}
 * effects; tool types not mentioned receive no bonus.
 */
public final class SmitherySynergies {
    /** Iron + copper synergy with damage-vs-wet-target bonus on swords. */
    public static SynergyDefinition GALVANIC;
    /** Iron + gold synergy with XP multipliers on swords and pickaxes. */
    public static SynergyDefinition GILDED;
    /** Copper + gold synergy with chance-to-poison on swords. */
    public static SynergyDefinition VERDANT_VEIL;

    /**
     * Registers every built-in synergy. Must run after the referenced materials, modifiers,
     * and tool types are registered.
     */
    public static void register() {
        GALVANIC = SmitheryAPI.registerSynergy(SynergyDefinition.builder(id("galvanic"))
                .materials(SmitheryMaterials.IRON, SmitheryMaterials.COPPER)
                .addEffect(SmitheryToolTypes.SWORD, SmitheryModifiers.SHARP,
                        Map.of("amount", 2.0f, "wet_only", true))
                .build());

        GILDED = SmitheryAPI.registerSynergy(SynergyDefinition.builder(id("gilded"))
                .materials(SmitheryMaterials.IRON, SmitheryMaterials.GOLD)
                .addEffect(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE,
                        Map.of("xp_multiplier", 1.25f))
                .addEffect(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED,
                        Map.of("xp_multiplier", 1.25f))
                .build());

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
