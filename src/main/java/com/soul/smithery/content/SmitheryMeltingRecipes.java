package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;

/**
 * Built-in melting recipes mapping vanilla item ids to smithery material fluids.
 *
 * <p>Standard portions are nugget = 16 mB, ingot = 144 mB, raw/ore = 288 mB (the forge doubles
 * ores), block = 1296 mB. Wood deliberately has no entries — it does not melt. Copper has no
 * vanilla nugget so that row is omitted.
 */
public final class SmitheryMeltingRecipes {
    private SmitheryMeltingRecipes() {}

    private static final int NUGGET_MB = 16;
    private static final int INGOT_MB  = 144;
    private static final int RAW_MB    = 288;
    private static final int BLOCK_MB  = 1296;

    /**
     * Registers every built-in melting recipe. Must run before
     * {@link #registerPartRemeltRecipes()} and after material/part-type registration.
     */
    public static void register() {
        recipe("minecraft:iron_nugget",         "smithery:iron", NUGGET_MB);
        recipe("minecraft:iron_ingot",          "smithery:iron", INGOT_MB);
        recipe("minecraft:raw_iron",            "smithery:iron", RAW_MB);
        recipe("minecraft:iron_ore",            "smithery:iron", RAW_MB);
        recipe("minecraft:deepslate_iron_ore",  "smithery:iron", RAW_MB);
        recipe("minecraft:iron_block",          "smithery:iron", BLOCK_MB);
        recipe("minecraft:raw_iron_block",      "smithery:iron", BLOCK_MB);

        recipe("minecraft:gold_nugget",         "smithery:gold", NUGGET_MB);
        recipe("minecraft:gold_ingot",          "smithery:gold", INGOT_MB);
        recipe("minecraft:raw_gold",            "smithery:gold", RAW_MB);
        recipe("minecraft:gold_ore",            "smithery:gold", RAW_MB);
        recipe("minecraft:deepslate_gold_ore",  "smithery:gold", RAW_MB);
        recipe("minecraft:nether_gold_ore",     "smithery:gold", RAW_MB);
        recipe("minecraft:gold_block",          "smithery:gold", BLOCK_MB);
        recipe("minecraft:raw_gold_block",      "smithery:gold", BLOCK_MB);

        recipe("minecraft:copper_ingot",          "smithery:copper", INGOT_MB);
        recipe("minecraft:raw_copper",            "smithery:copper", RAW_MB);
        recipe("minecraft:copper_ore",            "smithery:copper", RAW_MB);
        recipe("minecraft:deepslate_copper_ore",  "smithery:copper", RAW_MB);
        recipe("minecraft:copper_block",          "smithery:copper", BLOCK_MB);
        recipe("minecraft:raw_copper_block",      "smithery:copper", BLOCK_MB);

        recipe("minecraft:cobblestone",            "smithery:stone", INGOT_MB);
        recipe("minecraft:stone",                  "smithery:stone", INGOT_MB);
        recipe("minecraft:cobbled_deepslate",      "smithery:stone", INGOT_MB);
        recipe("minecraft:deepslate",              "smithery:stone", INGOT_MB);

        recipe("minecraft:lapis_lazuli",           "smithery:lapis",    NUGGET_MB);
        recipe("minecraft:lapis_block",            "smithery:lapis",    INGOT_MB);
        recipe("minecraft:lapis_ore",              "smithery:lapis",    RAW_MB);
        recipe("minecraft:deepslate_lapis_ore",    "smithery:lapis",    RAW_MB);

        recipe("minecraft:redstone",               "smithery:redstone", NUGGET_MB);
        recipe("minecraft:redstone_block",         "smithery:redstone", INGOT_MB);
        recipe("minecraft:redstone_ore",           "smithery:redstone", RAW_MB);
        recipe("minecraft:deepslate_redstone_ore", "smithery:redstone", RAW_MB);

        recipe("minecraft:prismarine_shard",       "smithery:prismarine", NUGGET_MB);
        recipe("minecraft:prismarine_crystals",    "smithery:prismarine", NUGGET_MB);
        recipe("minecraft:prismarine",             "smithery:prismarine", INGOT_MB);
        recipe("minecraft:dark_prismarine",        "smithery:prismarine", INGOT_MB);

        recipe("minecraft:blaze_powder",           "smithery:blaze",    NUGGET_MB);
        recipe("minecraft:blaze_rod",              "smithery:blaze",    INGOT_MB);

        recipe("minecraft:amethyst_shard",         "smithery:amethyst", NUGGET_MB);
        recipe("minecraft:amethyst_block",         "smithery:amethyst", INGOT_MB);
        recipe("minecraft:amethyst_cluster",       "smithery:amethyst", INGOT_MB);

        recipe("minecraft:diamond",                "smithery:diamond",  INGOT_MB);
        recipe("minecraft:diamond_block",          "smithery:diamond",  BLOCK_MB);
        recipe("minecraft:diamond_ore",            "smithery:diamond",  RAW_MB);
        recipe("minecraft:deepslate_diamond_ore",  "smithery:diamond",  RAW_MB);

        recipe("minecraft:emerald",                "smithery:emerald",  INGOT_MB);
        recipe("minecraft:emerald_block",          "smithery:emerald",  BLOCK_MB);
        recipe("minecraft:emerald_ore",            "smithery:emerald",  RAW_MB);
        recipe("minecraft:deepslate_emerald_ore",  "smithery:emerald",  RAW_MB);

        recipe("minecraft:ancient_debris",         "smithery:ancient_debris", INGOT_MB);
        recipe("minecraft:netherite_scrap",        "smithery:ancient_debris", NUGGET_MB);
        recipe("minecraft:netherite_ingot",        "smithery:netherite", INGOT_MB);
        recipe("minecraft:netherite_block",        "smithery:netherite", BLOCK_MB);

        recipe("minecraft:bedrock",                "smithery:bedrock",  INGOT_MB);

        recipe("smithery:red_slime",               "smithery:red_slime", INGOT_MB);
    }

    private static void recipe(String input, String material, int mb) {
        SmitheryAPI.registerMeltingRecipe(input, material, mb);
    }

    /**
     * Auto-registers part-remelt recipes so every smithery PartItem melts losslessly back into
     * its source material fluid at the part type's {@link PartType#castMb} amount.
     *
     * <p>Skips cast-only materials, synthetic part types (ingot/nugget/pearl already covered by
     * vanilla recipes), non-smithery materials, and materials whose melting temperature is
     * non-positive (e.g. wood).
     *
     * <p>Must run after {@link SmitheryMaterials#register()} and {@link SmitheryPartTypes#register()},
     * including any modder additions, so every registered part is picked up.
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
