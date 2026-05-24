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

        // --- Stone ---
        // Cobblestone is the "ingot equivalent" for stone tools. Vanilla recipes don't have
        // a nugget; we map a single cobblestone to 144 mB so 1 block = 1 ingot.
        recipe("minecraft:cobblestone",            "smithery:stone", INGOT_MB);
        recipe("minecraft:stone",                  "smithery:stone", INGOT_MB);
        recipe("minecraft:cobbled_deepslate",      "smithery:stone", INGOT_MB);
        recipe("minecraft:deepslate",              "smithery:stone", INGOT_MB);

        // --- Lapis ---
        recipe("minecraft:lapis_lazuli",           "smithery:lapis",    NUGGET_MB);
        recipe("minecraft:lapis_block",            "smithery:lapis",    INGOT_MB);
        recipe("minecraft:lapis_ore",              "smithery:lapis",    RAW_MB);
        recipe("minecraft:deepslate_lapis_ore",    "smithery:lapis",    RAW_MB);

        // --- Redstone ---
        recipe("minecraft:redstone",               "smithery:redstone", NUGGET_MB);
        recipe("minecraft:redstone_block",         "smithery:redstone", INGOT_MB);
        recipe("minecraft:redstone_ore",           "smithery:redstone", RAW_MB);
        recipe("minecraft:deepslate_redstone_ore", "smithery:redstone", RAW_MB);

        // --- Prismarine ---
        recipe("minecraft:prismarine_shard",       "smithery:prismarine", NUGGET_MB);
        recipe("minecraft:prismarine_crystals",    "smithery:prismarine", NUGGET_MB);
        recipe("minecraft:prismarine",             "smithery:prismarine", INGOT_MB);
        recipe("minecraft:dark_prismarine",        "smithery:prismarine", INGOT_MB);

        // --- Blaze ---
        recipe("minecraft:blaze_powder",           "smithery:blaze",    NUGGET_MB);
        recipe("minecraft:blaze_rod",              "smithery:blaze",    INGOT_MB);  // 1 rod = 2 powder vanilla, but as part-material we treat it as ingot

        // --- Amethyst ---
        recipe("minecraft:amethyst_shard",         "smithery:amethyst", NUGGET_MB);
        recipe("minecraft:amethyst_block",         "smithery:amethyst", INGOT_MB);
        recipe("minecraft:amethyst_cluster",       "smithery:amethyst", INGOT_MB);

        // --- Diamond ---
        recipe("minecraft:diamond",                "smithery:diamond",  INGOT_MB);
        recipe("minecraft:diamond_block",          "smithery:diamond",  BLOCK_MB);
        recipe("minecraft:diamond_ore",            "smithery:diamond",  RAW_MB);
        recipe("minecraft:deepslate_diamond_ore",  "smithery:diamond",  RAW_MB);

        // --- Emerald ---
        recipe("minecraft:emerald",                "smithery:emerald",  INGOT_MB);
        recipe("minecraft:emerald_block",          "smithery:emerald",  BLOCK_MB);
        recipe("minecraft:emerald_ore",            "smithery:emerald",  RAW_MB);
        recipe("minecraft:deepslate_emerald_ore",  "smithery:emerald",  RAW_MB);

        // --- Netherite (via alloy: gold + ancient debris → netherite, see data alloy JSON) ---
        // Ancient debris and netherite scrap melt into the smithery:ancient_debris intermediate
        // fluid. The forge's auto-alloy loop combines that fluid with smithery:gold (at 2200°C)
        // into smithery:netherite per the netherite.json alloy recipe (576+576 → 144).
        recipe("minecraft:ancient_debris",         "smithery:ancient_debris", INGOT_MB);
        recipe("minecraft:netherite_scrap",        "smithery:ancient_debris", NUGGET_MB);
        // Refined netherite items melt directly (player crafted them via vanilla smithing).
        recipe("minecraft:netherite_ingot",        "smithery:netherite", INGOT_MB);
        recipe("minecraft:netherite_block",        "smithery:netherite", BLOCK_MB);

        // --- Bedrock ---
        // Unobtainable in survival without creative; included so admins/creative players have
        // an endgame ceiling material.
        recipe("minecraft:bedrock",                "smithery:bedrock",  INGOT_MB);
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
