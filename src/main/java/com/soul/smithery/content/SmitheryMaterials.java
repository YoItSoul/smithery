package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.part.PartType;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Built-in materials.
 *
 * <h3>Tier organization</h3>
 * <ul>
 *   <li><b>Starters</b> — wood, copper, gold, iron — vanilla-tool-equivalent stats</li>
 *   <li><b>Meltable specialty</b> — stone, lapis, redstone, prismarine, blaze, amethyst,
 *       diamond, emerald, netherite, bedrock — each has a melting recipe and (when meltingTemp > 0)
 *       a corresponding molten fluid auto-bootstrapped by SmitheryFluids.</li>
 *   <li><b>Non-meltable specialty</b> — flint, slime, resin, coral — no forge route, must be
 *       obtained by future crafting recipes (TODO).</li>
 * </ul>
 *
 * <h3>Modifier slots — binder-only model</h3>
 * Modifier slot count is determined SOLELY by the binder material. Non-binder parts
 * (blade/head/handle/guard) grant 0 modifier slots regardless of their material. So a
 * tool's total slot count = its binder material's binder-slot grant. Binder choice
 * therefore drives "how many modifiers can I stack on this tool" independently of the
 * blade/head choice (which drives damage/mining speed/harvest level).
 *
 * <p>Binders also drive durability via {@code binderMultiplier} — the tool's additive
 * durability gets multiplied by this. So a slime binder (1.5×) makes the tool more
 * durable; a gold binder (0.7×) makes it less. Binder slot count and binder multiplier
 * together summarize "what does this binder do" — nothing else flows from the binder.
 *
 * Stat values are starting points; tune as gameplay shakes out.
 */
public final class SmitheryMaterials {
    public static Identifier WOOD;
    public static Identifier COPPER;
    public static Identifier GOLD;
    public static Identifier IRON;
    // Meltable specialty materials.
    public static Identifier STONE;
    public static Identifier LAPIS;
    public static Identifier REDSTONE;
    public static Identifier PRISMARINE;
    public static Identifier BLAZE;
    public static Identifier AMETHYST;
    public static Identifier DIAMOND;
    public static Identifier EMERALD;
    public static Identifier NETHERITE;
    public static Identifier ANCIENT_DEBRIS;     // cast-only — fluid intermediate for netherite alloy
    public static Identifier BEDROCK;
    // Non-meltable specialty materials.
    public static Identifier FLINT;
    public static Identifier SLIME;
    public static Identifier RESIN;
    public static Identifier CORAL;

    public static void register() {
        // ────────────────────────── Starters ──────────────────────────

        WOOD = id("wood");
        SmitheryAPI.registerMaterial(WOOD, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(2.0f)
                .attackDamage(0.5f)
                .durabilityPerIngot(60)
                .meltingTemp(0f)             // wood doesn't melt
                .moltenColor(0xFF8B5A2B)
                .partColor(0xFF8B5A2B)
                .binderMultiplier(0.7f), 1).build());

        COPPER = id("copper");
        SmitheryAPI.registerMaterial(COPPER, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.0f)
                .attackDamage(1.5f)
                .durabilityPerIngot(80)
                .meltingTemp(1085f)
                .moltenColor(0xFFFF7733)
                .partColor(0xFFB87333)
                .binderMultiplier(0.85f), 1)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.VERDANT,
                        Map.of("chance", 0.15f, "duration_ticks", 60))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.CORROSIVE,
                        Map.of("chance", 0.25f, "duration_ticks", 100, "amplifier", 1))
                .build());

        GOLD = id("gold");
        SmitheryAPI.registerMaterial(GOLD, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(12.0f)
                .attackDamage(1.0f)
                .durabilityPerIngot(32)
                .meltingTemp(1064f)
                .moltenColor(0xFFFFE066)
                .partColor(0xFFFFD700)
                .binderMultiplier(0.7f), 3)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE,
                        Map.of("xp_multiplier", 1.25f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED,
                        Map.of("xp_multiplier", 1.25f))
                // Gold pickaxes intrinsically grant the data-driven smithery:golden_touch
                // modifier — emulates Fortune-on-ores via BlockDropsEvent. See
                // data/smithery/smithery/modifier/golden_touch.json.
                .addModifier(SmitheryToolTypes.PICKAXE,
                        Identifier.fromNamespaceAndPath(Smithery.MODID, "golden_touch"))
                .build());

        IRON = id("iron");
        SmitheryAPI.registerMaterial(IRON, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.5f)
                .attackDamage(2.0f)
                .durabilityPerIngot(150)
                .meltingTemp(1538f)
                .moltenColor(0xFFFFAA55)
                .partColor(0xFFCFCFCF)
                .binderMultiplier(1.0f), 2)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.SHARP,
                        Map.of("damage", 2.0f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.MAGNETIZED,
                        Map.of("radius", 5.0f))
                .build());

        // ────────────────────────── Meltable specialty ──────────────────────────

        STONE = id("stone");
        SmitheryAPI.registerMaterial(STONE, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.0f)
                .attackDamage(1.0f)
                .durabilityPerIngot(131)        // vanilla stone-tool durability
                .meltingTemp(800f)
                .moltenColor(0xFFAAAAAA)
                .partColor(0xFF7E7E7E)
                .binderMultiplier(0.8f), 1).build());

        LAPIS = id("lapis");
        SmitheryAPI.registerMaterial(LAPIS, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.5f)
                .attackDamage(1.5f)
                .durabilityPerIngot(150)
                .meltingTemp(850f)
                .moltenColor(0xFF2030C0)
                .partColor(0xFF345DD0)
                .binderMultiplier(0.8f), 2).build());

        REDSTONE = id("redstone");
        SmitheryAPI.registerMaterial(REDSTONE, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(5.5f)
                .attackDamage(1.0f)
                .durabilityPerIngot(120)
                .meltingTemp(700f)              // low melt — soft material
                .moltenColor(0xFFFF0000)
                .partColor(0xFFAA0000)
                .binderMultiplier(0.7f), 1).build());

        PRISMARINE = id("prismarine");
        SmitheryAPI.registerMaterial(PRISMARINE, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(5.0f)
                .attackDamage(1.5f)
                .durabilityPerIngot(200)
                .meltingTemp(900f)
                .moltenColor(0xFF5FA3A3)
                .partColor(0xFF7CB3A8)
                .binderMultiplier(0.9f), 2).build());

        BLAZE = id("blaze");
        SmitheryAPI.registerMaterial(BLAZE, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(7.0f)
                .attackDamage(2.5f)
                .durabilityPerIngot(180)
                .meltingTemp(1200f)
                .moltenColor(0xFFFF7700)
                .partColor(0xFFFFAA00)
                .binderMultiplier(1.0f), 2).build());
        // TODO blaze items should ALSO be a high-heat fuel source for the forge — separate
        // fuel registry, wire when the fuel/heat system gets revisited.

        AMETHYST = id("amethyst");
        SmitheryAPI.registerMaterial(AMETHYST, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.0f)
                .attackDamage(2.0f)
                .durabilityPerIngot(180)
                .meltingTemp(1100f)
                .moltenColor(0xFFB070F0)
                .partColor(0xFFA85FE6)
                .binderMultiplier(0.9f), 2).build());

        DIAMOND = id("diamond");
        SmitheryAPI.registerMaterial(DIAMOND, binderSlots(MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(8.0f)
                .attackDamage(3.0f)
                .durabilityPerIngot(250)
                .meltingTemp(1800f)
                .moltenColor(0xFF00CCCC)
                .partColor(0xFF4FE2E0)
                .binderMultiplier(1.0f), 3)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.SHARP,
                        Map.of("damage", 3.0f))
                .build());

        EMERALD = id("emerald");
        SmitheryAPI.registerMaterial(EMERALD, binderSlots(MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(7.5f)
                .attackDamage(2.5f)
                .durabilityPerIngot(200)
                .meltingTemp(1500f)
                .moltenColor(0xFF50C878)
                .partColor(0xFF50C878)
                .binderMultiplier(1.0f), 3).build());

        // Ancient debris — fluid-only intermediate consumed by the netherite alloy recipe.
        // castOnly suppresses PartItem generation; the material exists purely so its molten
        // fluid (auto-bootstrapped because meltingTemp > 0) can sit in the forge waiting to
        // be combined with molten gold via data/smithery/smithery/alloy/netherite.json.
        ANCIENT_DEBRIS = id("ancient_debris");
        SmitheryAPI.registerMaterial(ANCIENT_DEBRIS, MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(7.0f)
                .attackDamage(2.5f)
                .durabilityPerIngot(180)
                .meltingTemp(1800f)
                .moltenColor(0xFF503535)         // dark reddish-brown
                .partColor(0xFF433333)
                .binderMultiplier(1.0f)
                .castOnly(true)
                .build());

        NETHERITE = id("netherite");
        SmitheryAPI.registerMaterial(NETHERITE, binderSlots(MaterialStats.builder()
                .harvestLevel(4)
                .miningSpeed(9.0f)
                .attackDamage(4.0f)
                .durabilityPerIngot(350)
                .meltingTemp(2200f)
                .moltenColor(0xFF402F2D)
                .partColor(0xFF4D4946)
                .binderMultiplier(1.2f), 4)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.SHARP,
                        Map.of("damage", 4.0f))
                .build());
        // TODO netherite is technically an alloy of gold + ancient debris — when the
        // alloying system lands, replace the direct netherite_scrap/ingot melt route with
        // an alloy recipe (4 gold + 4 ancient-debris fluid → 1 netherite ingot worth).

        BEDROCK = id("bedrock");
        SmitheryAPI.registerMaterial(BEDROCK, binderSlots(MaterialStats.builder()
                .harvestLevel(5)                 // beyond netherite — endgame
                .miningSpeed(12.0f)
                .attackDamage(5.0f)
                .durabilityPerIngot(1000)
                .meltingTemp(11000f)             // 5× netherite — needs a fuel beyond molten blaze
                .moltenColor(0xFF1A1A1A)
                .partColor(0xFF333333)
                .binderMultiplier(2.0f), 5).build());

        // ────────────────────────── Non-meltable specialty ──────────────────────────

        FLINT = id("flint");
        SmitheryAPI.registerMaterial(FLINT, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.0f)
                .attackDamage(2.0f)              // naturally sharp
                .durabilityPerIngot(100)
                .meltingTemp(0f)
                .partColor(0xFF5A5A5A)
                .binderMultiplier(0.7f), 1).build());

        SLIME = id("slime");
        SmitheryAPI.registerMaterial(SLIME, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(3.0f)
                .attackDamage(1.0f)
                .durabilityPerIngot(80)
                .meltingTemp(0f)
                .partColor(0xFF7FCD33)
                .binderMultiplier(1.5f), 3).build());          // sticky → great binder, high slot count

        RESIN = id("resin");
        SmitheryAPI.registerMaterial(RESIN, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.5f)
                .attackDamage(1.5f)
                .durabilityPerIngot(150)
                .meltingTemp(0f)
                .partColor(0xFFFF8C00)
                .binderMultiplier(1.1f), 2).build());

        CORAL = id("coral");
        SmitheryAPI.registerMaterial(CORAL, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.0f)
                .attackDamage(1.0f)
                .durabilityPerIngot(120)
                .meltingTemp(0f)
                .partColor(0xFFE0E0E0)            // neutral base — per-coral-color variants TODO
                .binderMultiplier(0.9f), 1).build());
        // TODO support per-coral-color variants (tube/brain/bubble/fire/horn) that tint the
        // tool to match. Either as 5 separate material ids or a single material with dynamic
        // partColor stored on the ItemStack.
    }

    /**
     * Sets the modifier-slot count this material contributes WHEN USED AS THE BINDER part.
     * All other part types implicitly grant 0 slots (the modifierSlots map returns 0 for
     * keys not present). The tool's total modifier slot count is therefore exactly the
     * binder material's binder-slot grant — a single deliberate choice per tool.
     */
    private static MaterialStats.Builder binderSlots(MaterialStats.Builder b, int count) {
        return b.modifierSlots(SmitheryPartTypes.BINDER, count);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryMaterials() {}
}
