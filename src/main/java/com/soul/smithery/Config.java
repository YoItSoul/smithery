package com.soul.smithery;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common-side configuration spec for Smithery.
 *
 * <p>Holds the {@link ForgeConfigSpec} the mod registers against
 * {@code ModConfig.Type.COMMON}. Runtime-tunable knobs land here as they are promoted
 * from constants.
 */
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /**
     * Whether a Smithery forge may generate as a village building. Worldgen affects every
     * world the mod is installed in, so it is a switch rather than a constant.
     */
    public static final ForgeConfigSpec.BooleanValue VILLAGE_FORGE = BUILDER
            .comment("Generate a Smithery forge as an occasional village building.")
            .define("worldgen.villageForge", true);

    /**
     * Selection weight against the vanilla house pool, which totals 68-87 depending on the
     * village biome. 0 disables the building outright.
     *
     * <p>This sets how many villages hold a forge, not how many forges a village holds:
     * {@code JigsawPlacerMixin} caps that at one, so raising this cannot produce a second.
     * Turn it up for a forge in nearly every village, down for a rarer find.
     *
     * <p>The dial is coarser than the share suggests. The placer walks every jigsaw
     * connector on every street piece, shuffles the whole pool for each and takes the first
     * candidate that fits, so a village draws from the house pool many more times than it
     * has buildings. 8 was drawing the forge two to five times over -- which is what the cap
     * now absorbs, and why a value that reads like a 4% share still lands in a good half of
     * villages.
     *
     * <p>Deliberately short of the building's ancestor, which held about half of every house
     * lot. 1.12 could afford that because it paired the weight with a per-village cap and
     * dropped the piece from the list once placed; the cap here restores that half of the
     * arrangement, but a share that large would still crowd the pool's own buildings out of
     * the villages that get a forge.
     */
    public static final ForgeConfigSpec.IntValue VILLAGE_FORGE_WEIGHT = BUILDER
            .comment("How often the forge is picked against vanilla houses (the pool totals 68-87)."
                     + " Controls how many villages get a forge, not how many each gets --"
                     + " that is capped at one.")
            .defineInRange("worldgen.villageForgeWeight", 8, 0, 100);

    /**
     * Percentage of villages allowed a forge at all, rolled once per village.
     *
     * <p>The rarity dial. Weight cannot do this job: a village draws from the house pool
     * about thirty times, so even a weight of 1 -- the smallest a pool can express -- still
     * leaves a forge in about a third of villages. This gate sits in front of the weight,
     * which then only decides how surely and on which lot an eligible village places one.
     * At the default weight that is near enough certain, so this reads straight through as
     * the share of villages with a forge.
     *
     * <p>Rolled from the village's own origin rather than from the generator's random, so
     * the answer is the same however the chunks around it are visited, and no vanilla
     * village layout shifts by a block for having the mod installed.
     */
    public static final ForgeConfigSpec.IntValue VILLAGE_FORGE_CHANCE = BUILDER
            .comment("Percentage of villages that may hold a forge. The rarity dial;"
                     + " villageForgeWeight only decides which lot within one of them.")
            .defineInRange("worldgen.villageForgeChance", 20, 0, 100);

    /**
     * Smallest village, counted in jigsaw pieces laid so far, that may take a forge. 0 lets
     * any village have one.
     *
     * <p>An alternative to rolling for it: a forge appears only once a village has grown
     * past a given size, which reads as a settlement established enough to support one.
     * Vanilla villages run from roughly ten pieces to fifty, counting street sections as
     * well as buildings, so a threshold in the twenties keeps forges to the larger half.
     *
     * <p>Two consequences worth knowing. The count is the village so far, not the village
     * finished -- the placer lays pieces out breadth-first from the town centre and the
     * forge is judged as its lot comes up -- so the threshold also pushes the building
     * outward, towards a lot on the edge of town rather than off the square. And it stacks
     * with the chance roll rather than replacing it; set the chance to 100 to gate on size
     * alone.
     */
    public static final ForgeConfigSpec.IntValue VILLAGE_FORGE_MIN_PIECES = BUILDER
            .comment("Smallest village, in jigsaw pieces laid so far, that may take a forge."
                     + " 0 allows any. Also pushes the forge towards the edge of town.")
            .defineInRange("worldgen.villageForgeMinPieces", 0, 0, 200);

    /**
     * Whether to also inject into house pools contributed by village-overhaul mods.
     *
     * <p>Packs that install one of those rarely generate vanilla villages at all -- the
     * overhauls register their own structures with their own pools, so injecting only into
     * vanilla's leaves nearly every village in the pack ineligible. Off restricts the forge
     * to the five vanilla pools.
     */
    public static final ForgeConfigSpec.BooleanValue VILLAGE_FORGE_MODDED = BUILDER
            .comment("Also add the forge to house pools registered by village-overhaul mods.")
            .define("worldgen.villageForgeModdedVillages", true);

    /**
     * Ceiling for the RF coil's target temperature. Melt rate scales with how far the
     * forge is above a material's melting point, so raising this makes melting faster as
     * well as more expensive.
     */
    public static final ForgeConfigSpec.IntValue RF_COIL_MAX_TEMP = BUILDER
            .comment("Highest temperature the RF coil can be set to, in degrees C.")
            .defineInRange("forge.rfCoilMaxTemperatureC", 10000, 100, 100000);

    /**
     * Multiplier on the coil's quadratic draw. Cost is
     * {@code coefficient * target^2 / 1650 + storedMb / divisor} RF/t -- quadratic so that
     * running hot is genuinely wasteful; a linear cost would make the coil cheaper per
     * millibucket the hotter it ran, because melt rate rises with temperature too.
     */
    public static final ForgeConfigSpec.DoubleValue RF_COIL_COEFFICIENT = BUILDER
            .comment("Multiplier on the RF coil's draw. Cost is coefficient * targetC^2 / 1650 RF/t.")
            .defineInRange("forge.rfCoilCoefficient", 2.5, 0.01, 1000.0);

    /** Millibuckets of stored melt per +1 RF/t. Bigger number, cheaper full forge. */
    public static final ForgeConfigSpec.IntValue RF_COIL_THERMAL_MASS_DIVISOR = BUILDER
            .comment("Stored millibuckets per extra RF/t the coil must pay to hold temperature.")
            .defineInRange("forge.rfCoilThermalMassDivisor", 200, 1, 100000);

    /**
     * Buffer size, expressed in seconds of running flat out at the maximum temperature,
     * so it keeps meaning the same thing when the cost or the ceiling are retuned.
     */
    public static final ForgeConfigSpec.IntValue RF_COIL_BUFFER_SECONDS = BUILDER
            .comment("RF coil buffer, in seconds of draw at maximum temperature.")
            .defineInRange("forge.rfCoilBufferSeconds", 5, 1, 600);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
