package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.cast.CastTemplates;
import com.soul.smithery.api.part.PartType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Built-in part types. New tool types that need new part shapes register their parts here
 * (or via SmitheryAPI from outside).
 *
 * durabilityScalar is the multiplier applied to material.durabilityPerIngot when this part
 * type contributes to the additive durability sum. Heads carry the bulk; handles less; binders
 * don't contribute additively (their role is multiplicative — see Material.binderMultiplier).
 */
public final class SmitheryPartTypes {
    public static PartType SWORD_BLADE;
    public static PartType GUARD;
    public static PartType HANDLE;
    public static PartType BINDER;
    public static PartType PICK_HEAD;
    /** Additional tool heads. Registered as PartTypes (and thus auto-generate PartItems +
     *  impressed-sand variants + remelt recipes per material), but no ToolType yet references
     *  them — they exist as ingredients waiting for axe / shovel / hoe / spear / bow tool
     *  types to be wired up. castMb sized per "vanilla tool ingot count × head fraction". */
    public static PartType AXE_HEAD;
    public static PartType SHOVEL_HEAD;
    public static PartType HOE_HEAD;
    public static PartType SPEAR_HEAD;
    public static PartType ARROW_HEAD;
    /**
     * "Synthetic" cast targets. INGOT/NUGGET re-use the PartType infrastructure for impression
     * block generation (the casting_sand_impressed_<id> voxelized model) but don't produce a
     * smithery PartItem — the cast yields a vanilla iron/gold/copper ingot or nugget matching
     * the poured material. Players impress with a vanilla ingot/nugget as the template.
     */
    public static PartType INGOT;
    public static PartType NUGGET;

    public static void register() {
        // castMb is a flat 1 ingot (144 mB) per part across the board. Keeps the material
        // economy 1:1 with vanilla ingot recipes: one ingot melted → one part cast, regardless
        // of which part. Tool balance comes from durabilityScalar and material stats, not
        // from accessories being "cheaper".
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
                .durabilityScalar(0.0f) // binder is purely multiplicative — no additive durability
                .partColorTint(true)
                .castMb(144)
                .build());

        PICK_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("pick_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)
                .build());

        // Heads for tool types not yet wired (axe / shovel / hoe / spear / bow). Each casts
        // PartItems for every material and impressed-sand variants automatically. castMb is
        // a flat 1 ingot per part — keeps the material economy 1:1 with vanilla ingot recipes
        // so a single ingot melted yields exactly one head, regardless of which head it is.
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
                .durabilityScalar(0.5f)   // small/consumable — contributes less to tool durability
                .partColorTint(true)
                .castMb(144)
                .build());

        // Synthetic cast targets. The textureTemplate points at vanilla item textures so the
        // impression voxelizer generates the right silhouette in sand. No PartItem is registered
        // for these (see SmitheryItems.registerPartsFor); the cast yields vanilla items resolved
        // from the poured material at retrieve time.
        INGOT = SmitheryAPI.registerPartType(PartType.builder(id("ingot"))
                .durabilityScalar(0.0f)
                .partColorTint(false)
                .castMb(144)              // 1 ingot
                .textureTemplate(Identifier.fromNamespaceAndPath("minecraft", "item/iron_ingot"))
                .syntheticCast(true)
                .build());

        NUGGET = SmitheryAPI.registerPartType(PartType.builder(id("nugget"))
                .durabilityScalar(0.0f)
                .partColorTint(false)
                .castMb(16)               // 1 nugget (1/9 ingot)
                .textureTemplate(Identifier.fromNamespaceAndPath("minecraft", "item/iron_nugget"))
                .syntheticCast(true)
                .build());

        registerBuiltInCastMappings();
    }

    /**
     * Built-in entries for {@link CastResults} and {@link CastTemplates}. Modders can add
     * their own without touching smithery — see those classes for the API.
     *
     * Note: SmitheryMaterials.register runs AFTER SmitheryPartTypes.register, so we can't
     * use SmitheryMaterials.IRON / .GOLD / .COPPER here (they'd be null). We construct the
     * ids inline — same value either way.
     */
    private static void registerBuiltInCastMappings() {
        Identifier iron   = id("iron");
        Identifier gold   = id("gold");
        Identifier copper = id("copper");

        // (material × cast target) → vanilla item
        CastResults.register(iron,   INGOT.id(),  () -> Items.IRON_INGOT);
        CastResults.register(gold,   INGOT.id(),  () -> Items.GOLD_INGOT);
        CastResults.register(copper, INGOT.id(),  () -> Items.COPPER_INGOT);
        CastResults.register(iron,   NUGGET.id(), () -> Items.IRON_NUGGET);
        CastResults.register(gold,   NUGGET.id(), () -> Items.GOLD_NUGGET);
        // No vanilla copper_nugget; intentionally unregistered. Casting copper into a nugget
        // mould yields nothing (resolvePartItem returns ItemStack.EMPTY, retrieve falls back
        // to the discarded-cast warning).

        // Template item → cast target
        CastTemplates.register(Items.IRON_INGOT,   INGOT.id());
        CastTemplates.register(Items.GOLD_INGOT,   INGOT.id());
        CastTemplates.register(Items.COPPER_INGOT, INGOT.id());
        CastTemplates.register(Items.IRON_NUGGET,  NUGGET.id());
        CastTemplates.register(Items.GOLD_NUGGET,  NUGGET.id());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryPartTypes() {}
}
