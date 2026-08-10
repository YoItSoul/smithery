package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.cast.CastBlocks;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Built-in Casting Basin recipes: which vanilla storage block each material pours into, and how
 * much molten material that block is worth.
 *
 * <p>Every portion here is the mB the same block melts for in {@link SmitheryMeltingRecipes}, so a
 * block poured in the basin and melted back in the forge is a closed loop. That constraint is not
 * cosmetic: a basin portion <em>below</em> what the block melts for is a duplication cycle, so the
 * two tables have to move together.
 *
 * <p>Most blocks are nine of the material's smallest whole unit — nine 144 mB ingots for the metals,
 * nine 125 mB slime balls, nine 16 mB gems for lapis and redstone — which is what makes those casts
 * take nine times as long to cool as casting one unit on a table. Amethyst and prismarine are the
 * exceptions vanilla crafts from four shards, and are priced (and so cooled) accordingly.
 *
 * <p>Materials absent from this table (blood, stone, blaze, bedrock) have no block form to cast
 * into; the basin refuses their fluid outright. Generated storage forms register themselves in
 * {@link com.soul.smithery.registry.SmitheryMaterialForms}, and data packs can add, retune or
 * remove any of this through {@code data/<namespace>/smithery/basin_cast/*.json}.
 */
public final class SmitheryBlockCasts {
    private SmitheryBlockCasts() {}

    /** Nine 144 mB ingots — the portion every metal storage block melts for. */
    private static final int METAL_BLOCK_MB = 1296;
    /** Nine 125 mB slime balls. */
    private static final int SLIME_BLOCK_MB = 1125;
    /** Nine 16 mB gems — lapis and redstone. */
    private static final int GEM_BLOCK_MB = 144;
    /** Four 16 mB shards — amethyst and prismarine, which vanilla crafts from four, not nine. */
    private static final int SHARD_BLOCK_MB = 64;
    /** One 144 mB unit — glass, which is a single smelted sand. */
    private static final int INGOT_MB = 144;
    /** Four 144 mB units — blocks a crafting table builds from four, like clay and furnace bricks. */
    private static final int CRAFTED_BLOCK_MB = 4 * INGOT_MB;

    /**
     * Registers every built-in basin cast. Must run after {@link SmitheryMaterials#register()} so
     * the material ids it names exist.
     */
    public static void register() {
        cast("iron",      METAL_BLOCK_MB, () -> Items.IRON_BLOCK);
        cast("gold",      METAL_BLOCK_MB, () -> Items.GOLD_BLOCK);
        cast("copper",    METAL_BLOCK_MB, () -> Items.COPPER_BLOCK);
        cast("diamond",   METAL_BLOCK_MB, () -> Items.DIAMOND_BLOCK);
        cast("emerald",   METAL_BLOCK_MB, () -> Items.EMERALD_BLOCK);
        cast("netherite", METAL_BLOCK_MB, () -> Items.NETHERITE_BLOCK);

        cast("slime",     SLIME_BLOCK_MB, () -> Items.SLIME_BLOCK);

        cast("lapis",    GEM_BLOCK_MB, () -> Items.LAPIS_BLOCK);
        cast("redstone", GEM_BLOCK_MB, () -> Items.REDSTONE_BLOCK);

        cast("amethyst",   SHARD_BLOCK_MB, () -> Items.AMETHYST_BLOCK);
        cast("prismarine", SHARD_BLOCK_MB, () -> Items.PRISMARINE);

        // Molten sand sets as glass, which is one smelted sand rather than a nine-unit block — the
        // basin is a block-shaped mould, and a glass block is what a block of molten sand becomes.
        cast("sand", INGOT_MB, () -> Items.GLASS);
        // Clay and furnace bricks are both four-to-the-block in a crafting table; the basin charges
        // exactly that, and both melt back for the same.
        cast("clay", CRAFTED_BLOCK_MB, () -> Items.CLAY);
        cast("furnace_brick", CRAFTED_BLOCK_MB, () -> SmitheryBlocks.FURNACE_BRICKS_ITEM.get());
    }

    private static void cast(String materialPath, int mb, Supplier<Item> result) {
        CastBlocks.register(ResourceLocation.fromNamespaceAndPath(Smithery.MODID, materialPath), mb, result);
    }
}
