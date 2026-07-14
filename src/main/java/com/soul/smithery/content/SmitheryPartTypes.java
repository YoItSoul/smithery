package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.cast.CastTemplates;
import com.soul.smithery.api.part.PartType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * Built-in part-type registrations and synthetic cast mappings.
 *
 * <p>Each part type's {@code durabilityScalar} multiplies the source material's
 * {@code durabilityPerIngot} when contributing to the additive durability sum. Heads carry
 * the bulk, handles less, binders zero (binder durability flows through the multiplicative
 * {@code Material.binderMultiplier} instead).
 */
public final class SmitheryPartTypes {
    /** Part type for sword blades (additive durability, full color tint). */
    public static PartType SWORD_BLADE;
    /** Part type for sword guards. */
    public static PartType GUARD;
    /** Part type for tool handles. */
    public static PartType HANDLE;
    /** Part type for tool binders (multiplicative role; drives modifier slot count). */
    public static PartType BINDER;
    /** Part type for pickaxe heads. */
    public static PartType PICK_HEAD;
    /** Part type for axe heads. */
    public static PartType AXE_HEAD;
    /** Part type for shovel heads. */
    public static PartType SHOVEL_HEAD;
    /** Part type for hoe heads. */
    public static PartType HOE_HEAD;
    /** Part type for spear heads. */
    public static PartType SPEAR_HEAD;
    /** Part type for arrow heads (consumable-style; low durability scalar). */
    public static PartType ARROW_HEAD;
    /** Part type for bow limbs; structural, two per bow. */
    public static PartType BOW_LIMB;
    /** Part type for bowstrings; multiplicative role on bows, light durability contribution. */
    public static PartType BOWSTRING;
    /** Part type for arrow shafts; drives arrow durability (shots remaining). */
    public static PartType ARROW_SHAFT;
    /** Part type for arrow fletching; multiplicative role on arrows. */
    public static PartType FLETCHING;
    /** Helmet core part type — primary additive slot for the helmet, casts at 576 mB (4 ingots). */
    public static PartType HELMET_CORE;
    /** Chestplate core part type — primary additive slot for the chestplate, casts at 864 mB (6 ingots). */
    public static PartType CHESTPLATE_CORE;
    /** Leggings core part type — primary additive slot for the leggings, casts at 720 mB (5 ingots). */
    public static PartType LEGGINGS_CORE;
    /** Boots core part type — primary additive slot for the boots, casts at 576 mB (4 ingots). */
    public static PartType BOOTS_CORE;
    /** Armor plates part type — multiplicative durability + toughness layer shared across all armor pieces, casts at 432 mB (3 ingots). */
    public static PartType ARMOR_PLATES;
    /** Armor trim part type — flat durability bonus shared across all armor pieces, casts at 144 mB (1 ingot). */
    public static PartType ARMOR_TRIM;
    /** Synthetic cast target whose pour yields a vanilla ingot resolved via {@link CastResults}. */
    public static PartType INGOT;
    /** Synthetic cast target whose pour yields a vanilla nugget resolved via {@link CastResults}. */
    public static PartType NUGGET;
    /** Large blade part — broadsword primary, twice the metal of a standard blade. */
    public static PartType LARGE_BLADE;
    /** Hammer head part — mining hammer primary; 8 ingots, castable only (no press mapping). */
    public static PartType HAMMER_HEAD;
    /** Large plate part — mining hammer cheeks, two per hammer. */
    public static PartType LARGE_PLATE;
    /** Kama head part — small hooked blade; shears + light harvesting. */
    public static PartType KAMA_HEAD;
    /** Shuriken blade part — quarter of a thrown star, half-ingot each. */
    public static PartType SHURIKEN_BLADE;
    /** Sharpening stone part — anvil-applied tool repair, tier-gated by its material's harvest level. */
    public static PartType SHARPENING_STONE;
    /** Polishing stone part — anvil-applied armor repair, the armor analog of the sharpening stone. */
    public static PartType POLISHING_STONE;

    /**
     * Registers every built-in part type and the synthetic cast mappings tying iron / gold /
     * copper ingots and nuggets to their cast results.
     */
    public static void register() {
        SWORD_BLADE = SmitheryAPI.registerPartType(PartType.builder(id("sword_blade"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        GUARD = SmitheryAPI.registerPartType(PartType.builder(id("guard"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .castMb(144)
                .build());

        HANDLE = SmitheryAPI.registerPartType(PartType.builder(id("handle"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .castMb(144)
                .build());

        BINDER = SmitheryAPI.registerPartType(PartType.builder(id("binder"))
                .durabilityScalar(0.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        PICK_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("pick_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        AXE_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("axe_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        SHOVEL_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("shovel_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        HOE_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("hoe_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        SPEAR_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("spear_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        ARROW_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("arrow_head"))
                .durabilityScalar(0.5f)
                .partColorTint(true)
                .castMb(144)
                .build());

        BOW_LIMB = SmitheryAPI.registerPartType(PartType.builder(id("bow_limb"))
                .durabilityScalar(0.8f)
                .partColorTint(true)
                .castMb(144)
                .build());

        BOWSTRING = SmitheryAPI.registerPartType(PartType.builder(id("bowstring"))
                .durabilityScalar(0.2f)
                .partColorTint(true)
                .castMb(72)
                .build());

        ARROW_SHAFT = SmitheryAPI.registerPartType(PartType.builder(id("arrow_shaft"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .castMb(72)
                .build());

        FLETCHING = SmitheryAPI.registerPartType(PartType.builder(id("fletching"))
                .durabilityScalar(0.2f)
                .partColorTint(true)
                .castMb(72)
                .build());

        HELMET_CORE = SmitheryAPI.registerPartType(PartType.builder(id("helmet_core"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(576)
                .build());

        CHESTPLATE_CORE = SmitheryAPI.registerPartType(PartType.builder(id("chestplate_core"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(864)
                .build());

        LEGGINGS_CORE = SmitheryAPI.registerPartType(PartType.builder(id("leggings_core"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(720)
                .build());

        BOOTS_CORE = SmitheryAPI.registerPartType(PartType.builder(id("boots_core"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(576)
                .build());

        ARMOR_PLATES = SmitheryAPI.registerPartType(PartType.builder(id("armor_plates"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(432)
                .build());

        ARMOR_TRIM = SmitheryAPI.registerPartType(PartType.builder(id("armor_trim"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        INGOT = SmitheryAPI.registerPartType(PartType.builder(id("ingot"))
                .durabilityScalar(0.0f)
                .partColorTint(false)
                .castMb(144)
                .textureTemplate(ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_ingot"))
                .syntheticCast(true)
                .build());

        NUGGET = SmitheryAPI.registerPartType(PartType.builder(id("nugget"))
                .durabilityScalar(0.0f)
                .partColorTint(false)
                .castMb(16)
                .textureTemplate(ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_nugget"))
                .syntheticCast(true)
                .build());

        LARGE_BLADE = SmitheryAPI.registerPartType(PartType.builder(id("large_blade"))
                .durabilityScalar(1.5f)
                .partColorTint(true)
                .castMb(288)
                .build());

        HAMMER_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("hammer_head"))
                .durabilityScalar(2.0f)
                .partColorTint(true)
                .castMb(1152)
                .build());

        LARGE_PLATE = SmitheryAPI.registerPartType(PartType.builder(id("large_plate"))
                .durabilityScalar(0.5f)
                .partColorTint(true)
                .castMb(432)
                .build());

        KAMA_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("kama_head"))
                .durabilityScalar(0.9f)
                .partColorTint(true)
                .castMb(144)
                .build());

        SHURIKEN_BLADE = SmitheryAPI.registerPartType(PartType.builder(id("shuriken_blade"))
                .durabilityScalar(0.25f)
                .partColorTint(true)
                .castMb(72)
                .build());

        SHARPENING_STONE = SmitheryAPI.registerPartType(PartType.builder(id("sharpening_stone"))
                .durabilityScalar(0.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        POLISHING_STONE = SmitheryAPI.registerPartType(PartType.builder(id("polishing_stone"))
                .durabilityScalar(0.0f)
                .partColorTint(true)
                .castMb(288)
                .build());

        registerBuiltInCastMappings();
    }

    private static void registerBuiltInCastMappings() {
        ResourceLocation iron   = id("iron");
        ResourceLocation gold   = id("gold");
        ResourceLocation copper = id("copper");

        CastResults.register(iron,   INGOT.id(),  () -> Items.IRON_INGOT);
        CastResults.register(gold,   INGOT.id(),  () -> Items.GOLD_INGOT);
        CastResults.register(copper, INGOT.id(),  () -> Items.COPPER_INGOT);
        CastResults.register(iron,   NUGGET.id(), () -> Items.IRON_NUGGET);
        CastResults.register(gold,   NUGGET.id(), () -> Items.GOLD_NUGGET);

        CastTemplates.register(Items.IRON_INGOT,   INGOT.id());
        CastTemplates.register(Items.GOLD_INGOT,   INGOT.id());
        CastTemplates.register(Items.COPPER_INGOT, INGOT.id());
        CastTemplates.register(Items.IRON_NUGGET,  NUGGET.id());
        CastTemplates.register(Items.GOLD_NUGGET,  NUGGET.id());
        // Bootstrap templates so the first stone can be cast without owning one already.
        CastTemplates.register(Items.FLINT, SHARPENING_STONE.id());
        CastTemplates.register(Items.BRICK, POLISHING_STONE.id());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryPartTypes() {}
}
