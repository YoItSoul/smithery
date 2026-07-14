package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Built-in material registrations exposed as static identifier handles.
 *
 * <p>Materials fall into four families: starters (wood/copper/gold/iron), meltable specialty
 * (stone/lapis/redstone/prismarine/blaze/amethyst/diamond/emerald/netherite/bedrock), non-meltable
 * specialty (flint/slime/resin/coral), and bowstring-class strings. A material's binder
 * contribution drives modifier slot count and the multiplicative durability term; non-binder
 * roles grant no slots.
 *
 * <p>Stat values are tuned starting points; the iron-iron-wood-iron sword is the canonical
 * anchor against vanilla.
 */
public final class SmitheryMaterials {
    /** ResourceLocation of the built-in wood material. */
    public static ResourceLocation WOOD;
    /** ResourceLocation of the built-in copper material. */
    public static ResourceLocation COPPER;
    /** ResourceLocation of the built-in gold material. */
    public static ResourceLocation GOLD;
    /** ResourceLocation of the built-in iron material. */
    public static ResourceLocation IRON;
    /** ResourceLocation of the built-in stone material. */
    public static ResourceLocation STONE;
    /** ResourceLocation of the built-in lapis material. */
    public static ResourceLocation LAPIS;
    /** ResourceLocation of the built-in redstone material. */
    public static ResourceLocation REDSTONE;
    /** ResourceLocation of the built-in prismarine material. */
    public static ResourceLocation PRISMARINE;
    /** ResourceLocation of the built-in blaze material. */
    public static ResourceLocation BLAZE;
    /** ResourceLocation of the built-in amethyst material. */
    public static ResourceLocation AMETHYST;
    /** ResourceLocation of the built-in diamond material. */
    public static ResourceLocation DIAMOND;
    /** ResourceLocation of the built-in emerald material. */
    public static ResourceLocation EMERALD;
    /** ResourceLocation of the built-in netherite material. */
    public static ResourceLocation NETHERITE;
    /** ResourceLocation of the cast-only ancient-debris intermediate that feeds the netherite alloy. */
    public static ResourceLocation ANCIENT_DEBRIS;
    /** ResourceLocation of the built-in bedrock material. */
    public static ResourceLocation BEDROCK;
    /** ResourceLocation of the slimeknightium easter-egg alloy material. */
    public static ResourceLocation SLIMEKNIGHTIUM;
    /** ResourceLocation of the neoforgium easter-egg alloy material (hidden from JEI). */
    public static ResourceLocation NEOFORGIUM;
    /** ResourceLocation of the generic mob-blood fluid material. */
    public static ResourceLocation BLOOD;
    /** ResourceLocation of the fox-specific blood fluid material that feeds the neoforgium alloy. */
    public static ResourceLocation FOX_BLOOD;
    /** ResourceLocation of the built-in flint material. */
    public static ResourceLocation FLINT;
    /** ResourceLocation of the built-in slime material. */
    public static ResourceLocation SLIME;
    /** ResourceLocation of the built-in resin material. */
    public static ResourceLocation RESIN;
    /** ResourceLocation of the built-in coral material. */
    public static ResourceLocation CORAL;
    /** ResourceLocation of the vanilla-string bowstring-class material. */
    public static ResourceLocation STRING;
    /** ResourceLocation of the flame-themed bowstring-class material. */
    public static ResourceLocation FLAMESTRING;
    /** ResourceLocation of the breeze-themed bowstring-class material. */
    public static ResourceLocation BREEZESTRING;
    /** ResourceLocation of the red-slime material (bowstring-eligible and unrestricted general use). */
    public static ResourceLocation RED_SLIME;
    /** ResourceLocation of the kelp-string bowstring-class material. */
    public static ResourceLocation KELP_STRING;

    /**
     * Registers every built-in material plus the bowstring eligibility allow-list and
     * material-side restrictions for string-class materials.
     *
     * <p>Must run after {@link SmitheryPartTypes#register()} so the binder part type id is
     * available.
     */
    public static void register() {

        WOOD = id("wood");
        SmitheryAPI.registerMaterial(WOOD, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(2.0f)
                .attackDamage(2.0f)
                .durabilityPerIngot(35)
                .meltingTemp(0f)
                .moltenColor(0xFF8B5A2B)
                .partColor(0xFF8B5A2B)
                .binderMultiplier(1.0f), 1)
                .addModifier(ModifierEffect.of(SmitheryModifiers.ECOLOGICAL,
                        Map.of("interval_ticks", 2400)), armorPieces())
                .armor(35f, 1f, 5f, 0.85f, 0f, 3f)
                .build());

        COPPER = id("copper");
        SmitheryAPI.registerMaterial(COPPER, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(5.3f)
                .attackDamage(3.0f)
                .durabilityPerIngot(210)
                .meltingTemp(1085f)
                .moltenColor(0xFFFF7733)
                .partColor(0xFFB87333)
                .binderMultiplier(1.05f), 1)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.VERDANT,
                        Map.of("chance", 0.15f, "duration_ticks", 60))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.CORROSIVE,
                        Map.of("chance", 0.25f, "duration_ticks", 100, "amplifier", 1))
                .addModifier(ModifierEffect.of(SmitheryModifiers.CONDUCTIVE,
                        Map.of("pct", 0.9f)), armorPieces())
                .armor(210f, 11f, 50f, 1.0f, 0f, 14f)
                .build());

        GOLD = id("gold");
        SmitheryAPI.registerMaterial(GOLD, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(12.0f)
                .attackDamage(4.0f)
                .durabilityPerIngot(32)
                .meltingTemp(1064f)
                .moltenColor(0xFFFFE066)
                .partColor(0xFFFFD700)
                .binderMultiplier(0.7f), 3)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE,
                        Map.of("xp_multiplier", 1.25f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED,
                        Map.of("xp_multiplier", 1.25f))
                .addModifier(SmitheryToolTypes.PICKAXE,
                        ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "golden_touch"))
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.ALLURING,
                        Map.of("radius", 6.0f))
                .armor(32f, 7f, 10f, 0.7f, 0f, 5f)
                .build());

        IRON = id("iron");
        SmitheryAPI.registerMaterial(IRON, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.0f)
                .attackDamage(4.0f)
                .durabilityPerIngot(204)
                .meltingTemp(1538f)
                .moltenColor(0xFFFFAA55)
                .partColor(0xFFCFCFCF)
                .binderMultiplier(0.85f), 2)
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.MAGNETIZED,
                        Map.of("radius", 5.0f))
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.MAGNETIZED,
                        Map.of("radius", 4.0f))
                .armor(204f, 15f, 50f, 1.0f, 0f, 15f)
                .build());

        STONE = id("stone");
        SmitheryAPI.registerMaterial(STONE, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.0f)
                .attackDamage(3.0f)
                .durabilityPerIngot(120)
                .meltingTemp(800f)
                .moltenColor(0xFFAAAAAA)
                .partColor(0xFF7E7E7E)
                .binderMultiplier(0.5f), 1)
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.STALWART,
                        Map.of("amount", 0.05f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.STONEBOUND,
                        Map.of("speed_bonus", 4.0f, "damage_penalty", 2.0f))
                .armor(120f, 5f, 20f, 0.95f, 0f, 8f)
                .build());

        LAPIS = id("lapis");
        SmitheryAPI.registerMaterial(LAPIS, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.5f)
                .attackDamage(3.0f)
                .durabilityPerIngot(150)
                .meltingTemp(850f)
                .moltenColor(0xFF2030C0)
                .partColor(0xFF345DD0)
                .binderMultiplier(0.8f), 2)
                .addModifier(ModifierEffect.of(SmitheryModifiers.WARDED,
                        Map.of("pct", 0.15f)), armorPieces())
                .armor(150f, 6f, 25f, 0.85f, 0f, 10f)
                .build());

        REDSTONE = id("redstone");
        SmitheryAPI.registerMaterial(REDSTONE, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(5.5f)
                .attackDamage(3.0f)
                .durabilityPerIngot(150)
                .meltingTemp(700f)
                .moltenColor(0xFFFF0000)
                .partColor(0xFFAA0000)
                .binderMultiplier(0.7f), 1)
                .addModifier(SmitheryToolTypes.LEGGINGS, SmitheryModifiers.ENERGIZED,
                        Map.of("pct", 0.02f))
                .armor(150f, 5f, 25f, 0.8f, 0f, 10f)
                .build());

        PRISMARINE = id("prismarine");
        SmitheryAPI.registerMaterial(PRISMARINE, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(5.5f)
                .attackDamage(6.2f)
                .durabilityPerIngot(430)
                .meltingTemp(900f)
                .moltenColor(0xFF5FA3A3)
                .partColor(0xFF7CB3A8)
                .binderMultiplier(0.6f), 2)
                .addModifier(ModifierEffect.of(SmitheryModifiers.AQUADYNAMIC,
                        Map.of("amount", 0.15f)), armorPieces())
                .armor(430f, 14f, 60f, 1.0f, 1f, 20f)
                .build());

        BLAZE = id("blaze");
        SmitheryAPI.registerMaterial(BLAZE, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.0f)
                .attackDamage(5.5f)
                .durabilityPerIngot(550)
                .meltingTemp(1200f)
                .moltenColor(0xFFFF7700)
                .partColor(0xFFFFAA00)
                .binderMultiplier(1.0f), 2)
                .addModifier(ModifierEffect.of(SmitheryModifiers.FIREWARD,
                        Map.of("pct", 0.3f)), armorPieces())
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.AUTOSMELT)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.FIERY,
                        Map.of("level", 1))
                .armor(550f, 12f, 60f, 1.0f, 1f, 20f)
                .build());

        AMETHYST = id("amethyst");
        SmitheryAPI.registerMaterial(AMETHYST, binderSlots(MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.0f)
                .attackDamage(4.0f)
                .durabilityPerIngot(200)
                .meltingTemp(1100f)
                .moltenColor(0xFFB070F0)
                .partColor(0xFFA85FE6)
                .binderMultiplier(0.9f), 2)
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.CRYSTALLINE,
                        Map.of("pct", 0.02f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.MOMENTUM,
                        Map.of("max_amplifier", 2))
                .armor(200f, 8f, 30f, 0.9f, 0f, 12f)
                .build());

        DIAMOND = id("diamond");
        SmitheryAPI.registerMaterial(DIAMOND, binderSlots(MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(8.0f)
                .attackDamage(5.0f)
                .durabilityPerIngot(500)
                .meltingTemp(1800f)
                .moltenColor(0xFF00CCCC)
                .partColor(0xFF4FE2E0)
                .binderMultiplier(1.0f), 3)
                .armor(500f, 20f, 80f, 1.1f, 2f, 30f)
                .build());

        EMERALD = id("emerald");
        SmitheryAPI.registerMaterial(EMERALD, binderSlots(MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(7.5f)
                .attackDamage(5.0f)
                .durabilityPerIngot(400)
                .meltingTemp(1500f)
                .moltenColor(0xFF50C878)
                .partColor(0xFF50C878)
                .binderMultiplier(1.0f), 3)
                .armor(400f, 18f, 70f, 1.05f, 1f, 25f)
                .build());

        ANCIENT_DEBRIS = id("ancient_debris");
        SmitheryAPI.registerMaterial(ANCIENT_DEBRIS, MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(7.0f)
                .attackDamage(2.5f)
                .durabilityPerIngot(180)
                .meltingTemp(1800f)
                .moltenColor(0xFF503535)
                .partColor(0xFF433333)
                .binderMultiplier(1.0f)
                .castOnly(true)
                .build());

        NETHERITE = id("netherite");
        SmitheryAPI.registerMaterial(NETHERITE, binderSlots(MaterialStats.builder()
                .harvestLevel(4)
                .miningSpeed(7.0f)
                .attackDamage(8.72f)
                .durabilityPerIngot(820)
                .meltingTemp(2200f)
                .moltenColor(0xFF402F2D)
                .partColor(0xFF4D4946)
                .binderMultiplier(1.2f), 4)
                .addModifier(ModifierEffect.of(SmitheryModifiers.STALWART,
                        Map.of("amount", 0.1f)), armorPieces())
                .armor(820f, 20f, 100f, 1.2f, 3f, 40f)
                .build());

        BEDROCK = id("bedrock");
        SmitheryAPI.registerMaterial(BEDROCK, binderSlots(MaterialStats.builder()
                .harvestLevel(5)
                .miningSpeed(12.0f)
                .attackDamage(10.0f)
                .durabilityPerIngot(1500)
                .meltingTemp(11000f)
                .moltenColor(0xFF1A1A1A)
                .partColor(0xFF333333)
                .binderMultiplier(2.0f), 5)
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.STALWART,
                        Map.of("amount", 0.25f))
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.IMMOVABLE,
                        Map.of("pct", 0.1f))
                .armor(1500f, 25f, 200f, 1.5f, 5f, 80f)
                .build());

        SLIMEKNIGHTIUM = id("slimeknightium");
        SmitheryAPI.registerMaterial(SLIMEKNIGHTIUM, binderSlots(MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(5.8f)
                .attackDamage(5.1f)
                .durabilityPerIngot(850)
                .meltingTemp(1700f)
                .moltenColor(0xFF992233)
                .partColor(0xFFCC2233)
                .binderMultiplier(0.5f), 3)
                .addModifier(SmitheryToolTypes.BOOTS, SmitheryModifiers.BOUNCY)
                .armor(850f, 17f, 90f, 1.1f, 2f, 35f)
                .build());

        NEOFORGIUM = id("neoforgium");
        SmitheryAPI.registerMaterial(NEOFORGIUM, binderSlots(MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(7.0f)
                .attackDamage(8.72f)
                .durabilityPerIngot(820)
                .meltingTemp(1700f)
                .moltenColor(0xFF8B0000)
                .partColor(0xFF7A0E1A)
                .binderMultiplier(0.5f), 3)
                .addModifier(SmitheryToolTypes.BOOTS, SmitheryModifiers.NIMBLE,
                        Map.of("pct", 0.5f))
                .armor(820f, 18f, 100f, 1.15f, 3f, 35f)
                .build());

        BLOOD = id("blood");
        SmitheryAPI.registerMaterial(BLOOD, MaterialStats.builder()
                .harvestLevel(0).miningSpeed(0f).attackDamage(0f).durabilityPerIngot(0)
                .meltingTemp(50f)
                .moltenColor(0xFFB22222)
                .partColor(0xFFB22222)
                .binderMultiplier(1.0f)
                .castOnly(true)
                .fluidBase(MaterialStats.FluidBase.WATER)
                .build());

        FOX_BLOOD = id("fox_blood");
        SmitheryAPI.registerMaterial(FOX_BLOOD, MaterialStats.builder()
                .harvestLevel(0).miningSpeed(0f).attackDamage(0f).durabilityPerIngot(0)
                .meltingTemp(50f)
                .moltenColor(0xFF8B1A1A)
                .partColor(0xFF8B1A1A)
                .binderMultiplier(1.0f)
                .castOnly(true)
                .fluidBase(MaterialStats.FluidBase.WATER)
                .build());

        FLINT = id("flint");
        SmitheryAPI.registerMaterial(FLINT, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(5.0f)
                .attackDamage(2.9f)
                .durabilityPerIngot(150)
                .meltingTemp(0f)
                .partColor(0xFF5A5A5A)
                .binderMultiplier(0.6f), 1)
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.SPINY,
                        Map.of("chance", 0.25f, "damage", 1.0f))
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.JAGGED,
                        Map.of("pct", 0.5f))
                .armor(150f, 3f, 15f, 0.85f, 0f, 8f)
                .build());

        SLIME = id("slime");
        SmitheryAPI.registerMaterial(SLIME, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(4.24f)
                .attackDamage(1.8f)
                .durabilityPerIngot(1000)
                .meltingTemp(0f)
                .partColor(0xFF7FCD33)
                .binderMultiplier(0.7f), 3)
                .addModifier(SmitheryToolTypes.BOOTS, SmitheryModifiers.BOUNCY)
                .armor(1000f, 5f, 30f, 0.9f, 0f, 30f)
                .build());

        RESIN = id("resin");
        SmitheryAPI.registerMaterial(RESIN, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.5f)
                .attackDamage(3.0f)
                .durabilityPerIngot(150)
                .meltingTemp(0f)
                .partColor(0xFFFF8C00)
                .binderMultiplier(1.1f), 2)
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.STICKY,
                        Map.of("duration_ticks", 60, "amplifier", 1))
                .armor(150f, 6f, 20f, 0.95f, 0f, 12f)
                .build());

        CORAL = id("coral");
        SmitheryAPI.registerMaterial(CORAL, binderSlots(MaterialStats.builder()
                .harvestLevel(1)
                .miningSpeed(4.0f)
                .attackDamage(3.0f)
                .durabilityPerIngot(120)
                .meltingTemp(0f)
                .partColor(0xFFE0E0E0)
                .binderMultiplier(0.9f), 1)
                .addModifier(SmitheryToolTypes.CHESTPLATE, SmitheryModifiers.SPINY,
                        Map.of("chance", 0.5f, "damage", 2.0f))
                .armor(120f, 4f, 15f, 0.9f, 0f, 8f)
                .build());

        STRING = id("string");
        SmitheryAPI.registerMaterial(STRING, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(1.0f)
                .attackDamage(0.0f)
                .durabilityPerIngot(50)
                .meltingTemp(0f)
                .partColor(0xFFE8E0C8)
                .binderMultiplier(0.9f), 1).build());

        FLAMESTRING = id("flamestring");
        SmitheryAPI.registerMaterial(FLAMESTRING, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(1.0f)
                .attackDamage(1.0f)
                .durabilityPerIngot(120)
                .meltingTemp(0f)
                .partColor(0xFFFF6622)
                .binderMultiplier(1.0f), 2).build());

        BREEZESTRING = id("breezestring");
        SmitheryAPI.registerMaterial(BREEZESTRING, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(1.0f)
                .attackDamage(0.5f)
                .durabilityPerIngot(140)
                .meltingTemp(0f)
                .partColor(0xFFB0E2FF)
                .binderMultiplier(1.1f), 2).build());

        RED_SLIME = id("red_slime");
        SmitheryAPI.registerMaterial(RED_SLIME, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(4.03f)
                .attackDamage(1.8f)
                .durabilityPerIngot(780)
                .meltingTemp(600f)
                .moltenColor(0xFFCC2233)
                .partColor(0xFFCC2233)
                .binderMultiplier(1.3f), 3)
                .addModifier(SmitheryToolTypes.BOOTS, SmitheryModifiers.BOUNCY)
                .armor(780f, 4f, 25f, 0.9f, 0f, 25f)
                .build());

        KELP_STRING = id("kelp_string");
        SmitheryAPI.registerMaterial(KELP_STRING, binderSlots(MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(1.0f)
                .attackDamage(0.5f)
                .durabilityPerIngot(160)
                .meltingTemp(0f)
                .partColor(0xFF3F8E45)
                .binderMultiplier(1.1f), 2).build());

        ResourceLocation bowstringId = SmitheryPartTypes.BOWSTRING.id();
        com.soul.smithery.api.part.PartEligibility.allow(bowstringId, STRING);
        com.soul.smithery.api.part.PartEligibility.allow(bowstringId, SLIME);
        com.soul.smithery.api.part.PartEligibility.allow(bowstringId, FLAMESTRING);
        com.soul.smithery.api.part.PartEligibility.allow(bowstringId, BREEZESTRING);
        com.soul.smithery.api.part.PartEligibility.allow(bowstringId, RED_SLIME);
        com.soul.smithery.api.part.PartEligibility.allow(bowstringId, KELP_STRING);

        com.soul.smithery.api.part.PartEligibility.restrictMaterialTo(STRING,       bowstringId);
        com.soul.smithery.api.part.PartEligibility.restrictMaterialTo(FLAMESTRING,  bowstringId);
        com.soul.smithery.api.part.PartEligibility.restrictMaterialTo(BREEZESTRING, bowstringId);
        com.soul.smithery.api.part.PartEligibility.restrictMaterialTo(KELP_STRING,  bowstringId);
    }

    /**
     * Grants {@code count} modifier slots on the material's binder AND armor-plates parts —
     * the two multiplier-role parts. The binder carries a tool's modifier capacity; plates are
     * the armor analog, so a piece's anvil capacity comes from its plates material.
     */
    private static MaterialStats.Builder binderSlots(MaterialStats.Builder b, int count) {
        return b.modifierSlots(SmitheryPartTypes.BINDER, count)
                .modifierSlots(SmitheryPartTypes.ARMOR_PLATES, count);
    }

    /** The four armor tool types, for granting one trait across a whole set. */
    private static ToolType[] armorPieces() {
        return new ToolType[]{ SmitheryToolTypes.HELMET, SmitheryToolTypes.CHESTPLATE,
                SmitheryToolTypes.LEGGINGS, SmitheryToolTypes.BOOTS };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryMaterials() {}
}
