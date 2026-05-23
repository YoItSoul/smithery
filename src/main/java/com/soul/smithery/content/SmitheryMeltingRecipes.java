package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;

/**
 * Built-in melting recipes for iron, gold, and copper using vanilla item ids.
 *
 * Conventions (per the design doc):
 *   - nugget = 16 mB
 *   - ingot  = 144 mB
 *   - raw / ore form = 288 mB (2× ingot — the forge doubles ores)
 *   - block (9 ingots) = 1296 mB
 *
 * Wood doesn't melt (no melt temperature defined) so wood items are deliberately omitted.
 * Copper has no nugget in vanilla; that row is omitted.
 */
public final class SmitheryMeltingRecipes {
    private SmitheryMeltingRecipes() {}

    private static final int NUGGET_MB = 16;
    private static final int INGOT_MB  = 144;
    private static final int RAW_MB    = 288;
    private static final int BLOCK_MB  = 1296;

    public static void register() {
        // --- Iron ---
        recipe("minecraft:iron_nugget",         "smithery:iron", NUGGET_MB);
        recipe("minecraft:iron_ingot",          "smithery:iron", INGOT_MB);
        recipe("minecraft:raw_iron",            "smithery:iron", RAW_MB);
        recipe("minecraft:iron_ore",            "smithery:iron", RAW_MB);
        recipe("minecraft:deepslate_iron_ore",  "smithery:iron", RAW_MB);
        recipe("minecraft:iron_block",          "smithery:iron", BLOCK_MB);
        recipe("minecraft:raw_iron_block",      "smithery:iron", BLOCK_MB);

        // --- Gold ---
        recipe("minecraft:gold_nugget",         "smithery:gold", NUGGET_MB);
        recipe("minecraft:gold_ingot",          "smithery:gold", INGOT_MB);
        recipe("minecraft:raw_gold",            "smithery:gold", RAW_MB);
        recipe("minecraft:gold_ore",            "smithery:gold", RAW_MB);
        recipe("minecraft:deepslate_gold_ore",  "smithery:gold", RAW_MB);
        recipe("minecraft:nether_gold_ore",     "smithery:gold", RAW_MB);
        recipe("minecraft:gold_block",          "smithery:gold", BLOCK_MB);
        recipe("minecraft:raw_gold_block",      "smithery:gold", BLOCK_MB);

        // --- Copper (no vanilla nugget) ---
        recipe("minecraft:copper_ingot",          "smithery:copper", INGOT_MB);
        recipe("minecraft:raw_copper",            "smithery:copper", RAW_MB);
        recipe("minecraft:copper_ore",            "smithery:copper", RAW_MB);
        recipe("minecraft:deepslate_copper_ore",  "smithery:copper", RAW_MB);
        recipe("minecraft:copper_block",          "smithery:copper", BLOCK_MB);
        recipe("minecraft:raw_copper_block",      "smithery:copper", BLOCK_MB);
    }

    private static void recipe(String input, String material, int mb) {
        SmitheryAPI.registerMeltingRecipe(input, material, mb);
    }

    /**
     * Auto-registers a melting recipe for every auto-generated PartItem in the smithery
     * namespace: {@code smithery:<material>_<part_type>} → {@code <material>} fluid at the
     * part type's {@link PartType#castMb} amount. Parts melt back into their source metal
     * losslessly — recasting a part recovers exactly the material that went into it, so the
     * forge doubles as the "uncraft" path for broken or unwanted tool parts.
     *
     * Skipped: castOnly materials (no PartItems exist), synthetic part types (ingot / nugget
     * / pearl — already covered by direct vanilla item recipes), non-smithery materials, and
     * materials with meltingTemp ≤ 0 (wood doesn't melt; its parts deliberately have no
     * forge route).
     *
     * Must run AFTER {@link SmitheryMaterials#register()} and {@link SmitheryPartTypes#register()}
     * (and after any modder content registers its own additions, so their parts are picked up too).
     */
    public static void registerPartRemeltRecipes() {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            if (!Smithery.MODID.equals(mat.id().getNamespace())) continue;
            if (mat.stats().castOnly()) continue;
            if (mat.stats().meltingTemp() <= 0f) continue;
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (pt.syntheticCast()) continue;
                String partItemId = Smithery.MODID + ":" + mat.id().getPath() + "_" + pt.id().getPath();
                recipe(partItemId, mat.id().toString(), pt.castMb());
            }
        }
    }
}
