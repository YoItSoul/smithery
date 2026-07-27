package com.soul.smithery.api;

import com.soul.smithery.api.alloy.AlloyDefinition;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.registry.SimpleRegistry;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modder-facing entry point for the Smithery registry system.
 *
 * <p>Holds the five {@link SimpleRegistry} instances ({@link PartType}, {@link ToolType},
 * {@link Material}, {@link Modifier}, {@link AlloyDefinition}, {@link SynergyDefinition}) plus the
 * melting-recipe map. Built-in Smithery content registers through this API so modders see no
 * asymmetry between first-party and third-party registrations.
 *
 * <p>All registration runs during mod construction, before any item/block/fluid registry events
 * fire. Smithery then iterates Materials x PartTypes during those events to auto-generate part
 * items and molten fluids.
 */
public final class SmitheryAPI {
    private SmitheryAPI() {}

    /** Registry of every {@link PartType} (slot shapes used by tools and casts). */
    public static final SimpleRegistry<PartType> PART_TYPES =
            new SimpleRegistry<>("PartType", PartType::id);

    /** Registry of every {@link ToolType} (tool templates listing required part slots). */
    public static final SimpleRegistry<ToolType> TOOL_TYPES =
            new SimpleRegistry<>("ToolType", ToolType::id);

    /** Registry of every {@link Material}. */
    public static final SimpleRegistry<Material> MATERIALS =
            new SimpleRegistry<>("Material", Material::id);

    /** Registry of every {@link Modifier} (definitions; per-tool instances are {@code ModifierEffect}). */
    public static final SimpleRegistry<Modifier> MODIFIERS =
            new SimpleRegistry<>("Modifier", Modifier::id);

    /**
     * Registry of legacy {@link AlloyDefinition} entries.
     *
     * <p>Keyed by result-material id. Insertion order is load-bearing for the forge's alloy
     * conflict tiebreaker when two definitions have equal component counts.
     */
    public static final SimpleRegistry<AlloyDefinition> ALLOYS =
            new SimpleRegistry<>("Alloy", AlloyDefinition::resultMaterialId);

    /** Registry of every {@link SynergyDefinition} (two-material bonus effects). */
    public static final SimpleRegistry<SynergyDefinition> SYNERGIES =
            new SimpleRegistry<>("Synergy", SynergyDefinition::id);

    /**
     * Melting recipes keyed by input item id.
     *
     * <p>One recipe per input item; later registrations replace earlier ones, letting datapack
     * overrides win over built-in defaults. O(1) lookup by item id.
     */
    public static final Map<ResourceLocation, MeltingRecipe> MELTING_RECIPES = new HashMap<>();

    /**
     * Registers a {@link PartType}.
     *
     * <p><b>Provide the part's display name</b> as a lang entry keyed
     * {@code smithery.part.<namespace>.<path>} in your own resource pack. Smithery composes
     * part-item names ({@code "<material> <part>"}) and impressed-casting-sand names
     * ({@code "Impressed Sand (<part>)"}) from that one atom — neither needs a per-part lang key.
     */
    public static PartType registerPartType(PartType pt) { return PART_TYPES.register(pt); }

    /**
     * Registers a {@link ToolType}.
     *
     * <p><b>Provide the tool's display name</b> as {@code smithery.tool.<namespace>.<path>};
     * assembled tools compose {@code "<material> <tool>"} from it.
     */
    public static ToolType registerToolType(ToolType tt) { return TOOL_TYPES.register(tt); }

    /**
     * Registers a {@link Modifier}.
     *
     * <p>Provide the modifier's display name as {@code smithery.modifier.<namespace>.<path>} and,
     * optionally, a description as {@code smithery.modifier.<namespace>.<path>.description}. A
     * missing description falls back to a localized "No description"; a missing name renders the
     * raw key.
     */
    public static Modifier registerModifier(Modifier m) { return MODIFIERS.register(m); }

    /** Registers an {@link AlloyDefinition}. */
    public static AlloyDefinition registerAlloy(AlloyDefinition a) { return ALLOYS.register(a); }

    /**
     * Registers a {@link SynergyDefinition} (two-material bonus effect).
     *
     * <p>Provide the synergy's display name as {@code smithery.synergy.<namespace>.<path>}.
     */
    public static SynergyDefinition registerSynergy(SynergyDefinition s) { return SYNERGIES.register(s); }

    /** Register or replace the melting recipe for {@code recipe.inputItemId()}. */
    public static MeltingRecipe registerMeltingRecipe(MeltingRecipe recipe) {
        MELTING_RECIPES.put(recipe.inputItemId(), recipe);
        return recipe;
    }

    /** Convenience overload that constructs a {@link MeltingRecipe} from string ids and mB. */
    public static MeltingRecipe registerMeltingRecipe(String inputItem, String material, int mb) {
        return registerMeltingRecipe(new MeltingRecipe(
                ResourceLocation.parse(inputItem), ResourceLocation.parse(material), mb));
    }

    /**
     * Creates and registers a {@link Material} with the given id and stats.
     *
     * <p><b>Modders must supply the material's display name</b> as a lang entry keyed
     * {@code smithery.material.<namespace>.<path>} — e.g. {@code smithery.material.mymod.mithril}
     * for {@code mymod:mithril} — in their own resource pack. Smithery composes every derived
     * name from that single atom, so no per-fluid or per-bucket lang keys are needed:
     * a meltable material ({@code meltingTemp > 0}) auto-registers a molten fluid, block and bucket
     * displayed as {@code "Molten <name>"} and {@code "Molten <name> Bucket"} (or {@code "<name>"} /
     * {@code "<name> Bucket"} for {@link MaterialStats.FluidBase#WATER} materials), and part items
     * compose {@code "<name> <part>"}. A material without its name atom renders the raw key.
     *
     * @param id    unique material id (use your own mod namespace)
     * @param stats material stat block
     * @return the registered material
     */
    public static Material registerMaterial(ResourceLocation id, MaterialStats stats) {
        return MATERIALS.register(new Material(id, stats));
    }

    /** String-id overload of {@link #registerMaterial(ResourceLocation, MaterialStats)}. */
    public static Material registerMaterial(String id, MaterialStats stats) {
        return registerMaterial(ResourceLocation.parse(id), stats);
    }

    /**
     * Replace the stats of an existing material in-place. The {@link Material} instance and any
     * references to it remain valid.
     *
     * @return {@code true} if the material existed and was updated
     */
    public static boolean overrideMaterial(ResourceLocation id, MaterialStats newStats) {
        Material m = MATERIALS.get(id);
        if (m == null) return false;
        m.overrideStats(newStats);
        return true;
    }

    /** Remove a material and unregister its auto-generated content. */
    public static boolean removeMaterial(ResourceLocation id) {
        return MATERIALS.remove(id);
    }

    /** Returns all synergies that match the given (a, b) pair, order-independent. */
    public static List<SynergyDefinition> synergiesFor(ResourceLocation a, ResourceLocation b) {
        List<SynergyDefinition> out = new ArrayList<>();
        for (SynergyDefinition s : SYNERGIES.all()) {
            if (s.matches(a, b)) out.add(s);
        }
        return out;
    }

    /** Returns every {@link ToolType} whose part list includes the given {@link PartType}. */
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
