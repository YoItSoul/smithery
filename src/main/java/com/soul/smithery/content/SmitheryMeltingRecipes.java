package com.soul.smithery.content;

import com.soul.smithery.api.SmitheryAPI;

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
}
