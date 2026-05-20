package com.soul.smithery.api;

import com.soul.smithery.api.alloy.AlloyDefinition;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.registry.SimpleRegistry;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Modder-facing entry point.
 *
 * Register materials, part types, tool types, modifiers, alloys, and synergies here. All Smithery
 * built-in content is registered through this same API so modders see no asymmetry.
 *
 * Registration is performed during the mod's construction phase, before any item/block/fluid
 * registry events fire. The mod will iterate registered Materials × PartTypes to auto-generate
 * part items and molten fluids during those events.
 */
public final class SmitheryAPI {
    private SmitheryAPI() {}

    public static final SimpleRegistry<PartType> PART_TYPES =
            new SimpleRegistry<>("PartType", PartType::id);

    public static final SimpleRegistry<ToolType> TOOL_TYPES =
            new SimpleRegistry<>("ToolType", ToolType::id);

    public static final SimpleRegistry<Material> MATERIALS =
            new SimpleRegistry<>("Material", Material::id);

    public static final SimpleRegistry<Modifier> MODIFIERS =
            new SimpleRegistry<>("Modifier", Modifier::id);

    public static final SimpleRegistry<AlloyDefinition> ALLOYS =
            new SimpleRegistry<>("Alloy", AlloyDefinition::resultMaterialId);

    public static final SimpleRegistry<SynergyDefinition> SYNERGIES =
            new SimpleRegistry<>("Synergy", SynergyDefinition::id);

    // ---- Convenience registration ----

    public static PartType registerPartType(PartType pt) { return PART_TYPES.register(pt); }
    public static ToolType registerToolType(ToolType tt) { return TOOL_TYPES.register(tt); }
    public static Modifier registerModifier(Modifier m) { return MODIFIERS.register(m); }
    public static AlloyDefinition registerAlloy(AlloyDefinition a) { return ALLOYS.register(a); }
    public static SynergyDefinition registerSynergy(SynergyDefinition s) { return SYNERGIES.register(s); }

    public static Material registerMaterial(Identifier id, MaterialStats stats) {
        return MATERIALS.register(new Material(id, stats));
    }

    public static Material registerMaterial(String id, MaterialStats stats) {
        return registerMaterial(Identifier.parse(id), stats);
    }

    // ---- Overrides / removal (also used by datapack JSON loaders) ----

    /** Replace the stats of an existing material. The Material instance is preserved. */
    public static boolean overrideMaterial(Identifier id, MaterialStats newStats) {
        Material m = MATERIALS.get(id);
        if (m == null) return false;
        m.overrideStats(newStats);
        return true;
    }

    /** Remove a material and unregister its auto-generated content. */
    public static boolean removeMaterial(Identifier id) {
        return MATERIALS.remove(id);
    }

    // ---- Synergy lookup ----

    /** Returns all synergies that match the given (a, b) pair, order-independent. */
    public static List<SynergyDefinition> synergiesFor(Identifier a, Identifier b) {
        List<SynergyDefinition> out = new ArrayList<>();
        for (SynergyDefinition s : SYNERGIES.all()) {
            if (s.matches(a, b)) out.add(s);
        }
        return out;
    }

    // ---- Cross-registry lookups ----

    /** Returns every ToolType whose part list includes the given PartType. */
    public static List<ToolType> toolTypesUsingPart(PartType partType) {
        List<ToolType> out = new ArrayList<>();
        for (ToolType tt : TOOL_TYPES.all()) {
            for (ToolType.Slot s : tt.slots()) {
                if (s.partType().equals(partType)) {
                    out.add(tt);
                    break;
                }
            }
        }
        return out;
    }
}
