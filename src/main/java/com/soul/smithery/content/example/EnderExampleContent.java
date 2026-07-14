package com.soul.smithery.content.example;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.cast.CastTemplates;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryModifiers;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.content.SmitheryToolTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Reference bundle showing how a modder registers a complete material plus synthetic cast.
 *
 * <p>Exercises every builder method on {@link MaterialStats} and {@link PartType} so this file
 * doubles as a copy-paste template. Adds the {@code smithery:ender} material, the synthetic
 * {@code smithery:pearl} part type, the matching {@link CastResults} / {@link CastTemplates}
 * mappings, and a melting recipe for vanilla ender pearls.
 *
 * <p>Must run after {@link SmitheryPartTypes#register()}, {@link SmitheryToolTypes#register()},
 * and {@link SmitheryModifiers#register()}, and before fluid bootstrap so the auto-generated
 * molten-ender fluid sees the material.
 */
public final class EnderExampleContent {
    private EnderExampleContent() {}

    /** ResourceLocation of the ender material registered by this example. */
    public static final ResourceLocation ENDER_MATERIAL_ID =
            new ResourceLocation(Smithery.MODID, "ender");
    /** ResourceLocation of the synthetic pearl part type registered by this example. */
    public static final ResourceLocation PEARL_PART_TYPE_ID =
            new ResourceLocation(Smithery.MODID, "pearl");
    /** ResourceLocation of the data-driven ender_affinity modifier referenced by this example. */
    public static final ResourceLocation ENDER_AFFINITY_MODIFIER_ID =
            new ResourceLocation(Smithery.MODID, "ender_affinity");

    /** Cached pearl part-type handle for downstream renderer / capability lookups. */
    public static PartType PEARL;

    /**
     * Registers the ender material, the pearl part type, their cast mappings, and the ender-pearl
     * melting recipe.
     */
    public static void register() {
        PEARL = SmitheryAPI.registerPartType(PartType.builder(PEARL_PART_TYPE_ID)
                .durabilityScalar(0.0f)
                .partColorTint(false)
                .castMb(64)
                .textureTemplate(new ResourceLocation("minecraft", "item/ender_pearl"))
                .syntheticCast(true)
                .build());

        SmitheryAPI.registerMaterial(ENDER_MATERIAL_ID, MaterialStats.builder()
                .harvestLevel(3)
                .miningSpeed(9.0f)
                .attackDamage(3.0f)
                .durabilityPerIngot(220)
                .meltingTemp(900f)
                .moltenColor(0xFF1FC891)
                .partColor(0xFF8DDEC1)
                .binderMultiplier(1.25f)
                .castOnly(false)
                .modifierSlots(SmitheryPartTypes.SWORD_BLADE, 2)
                .modifierSlots(SmitheryPartTypes.GUARD, 2)
                .modifierSlots(SmitheryPartTypes.HANDLE, 2)
                .modifierSlots(SmitheryPartTypes.BINDER, 2)
                .modifierSlots(SmitheryPartTypes.PICK_HEAD, 2)
                .modifierSlots(SmitheryPartTypes.INGOT, 0)
                .modifierSlots(SmitheryPartTypes.NUGGET, 0)
                .modifierSlots(PEARL, 0)
                .addModifier(SmitheryToolTypes.SWORD,
                        ModifierEffect.of(SmitheryModifiers.VERDANT,
                                Map.of("chance", 0.25f, "duration_ticks", 80, "amplifier", 1)))
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE)
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.MAGNETIZED,
                        Map.of("radius", 8.0f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED)
                .build());

        CastResults.register(ENDER_MATERIAL_ID, PEARL_PART_TYPE_ID, () -> Items.ENDER_PEARL);

        CastTemplates.register(Items.ENDER_PEARL, PEARL_PART_TYPE_ID);

        SmitheryAPI.registerMeltingRecipe("minecraft:ender_pearl", ENDER_MATERIAL_ID.toString(), 64);
    }
}
