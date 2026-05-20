package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.MaterialStats;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Built-in starting materials: Copper, Gold, Iron.
 *
 * Stat values match the design doc tables. Modifier wiring is done in SmitheryStartingContent
 * after both Materials and Modifiers are registered, since modifier IDs need to exist before
 * they can be referenced.
 */
public final class SmitheryMaterials {
    public static Identifier WOOD;
    public static Identifier COPPER;
    public static Identifier GOLD;
    public static Identifier IRON;

    public static void register() {
        WOOD = id("wood");
        SmitheryAPI.registerMaterial(WOOD, MaterialStats.builder()
                .harvestLevel(0)
                .miningSpeed(2.0f)
                .attackDamage(0.5f)
                .durabilityPerIngot(60)
                .meltingTemp(0f)             // wood doesn't melt; placeholder for now
                .moltenColor(0xFF8B5A2B)
                .partColor(0xFF8B5A2B)       // medium brown
                .binderMultiplier(0.7f)
                .modifierSlots(SmitheryPartTypes.SWORD_BLADE, 1)
                .modifierSlots(SmitheryPartTypes.GUARD, 1)
                .modifierSlots(SmitheryPartTypes.HANDLE, 1)
                .modifierSlots(SmitheryPartTypes.PICK_HEAD, 1)
                .build());

        COPPER = id("copper");
        SmitheryAPI.registerMaterial(COPPER, MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.0f)
                .attackDamage(1.5f)
                .durabilityPerIngot(80)
                .meltingTemp(1085f)
                .moltenColor(0xFFFF7733)
                .partColor(0xFFB87333)
                .binderMultiplier(0.85f)
                .modifierSlots(SmitheryPartTypes.SWORD_BLADE, 1)
                .modifierSlots(SmitheryPartTypes.GUARD, 1)
                .modifierSlots(SmitheryPartTypes.HANDLE, 1)
                .modifierSlots(SmitheryPartTypes.PICK_HEAD, 1)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.VERDANT,
                        Map.of("chance", 0.15f, "duration_ticks", 60))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.CORROSIVE,
                        Map.of("chance", 0.25f, "duration_ticks", 100, "amplifier", 1))
                .build());

        GOLD = id("gold");
        SmitheryAPI.registerMaterial(GOLD, MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(12.0f)
                .attackDamage(1.0f)
                .durabilityPerIngot(32)
                .meltingTemp(1064f)
                .moltenColor(0xFFFFE066)
                .partColor(0xFFFFD700)
                .binderMultiplier(0.7f)
                .modifierSlots(SmitheryPartTypes.SWORD_BLADE, 2)
                .modifierSlots(SmitheryPartTypes.GUARD, 2)
                .modifierSlots(SmitheryPartTypes.HANDLE, 2)
                .modifierSlots(SmitheryPartTypes.PICK_HEAD, 2)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE,
                        Map.of("xp_multiplier", 1.25f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED,
                        Map.of("xp_multiplier", 1.25f))
                .build());

        IRON = id("iron");
        SmitheryAPI.registerMaterial(IRON, MaterialStats.builder()
                .harvestLevel(2)
                .miningSpeed(6.5f)
                .attackDamage(2.0f)
                .durabilityPerIngot(150)
                .meltingTemp(1538f)
                .moltenColor(0xFFFFAA55)
                .partColor(0xFFCFCFCF)
                .binderMultiplier(1.0f)
                .modifierSlots(SmitheryPartTypes.SWORD_BLADE, 2)
                .modifierSlots(SmitheryPartTypes.GUARD, 1)
                .modifierSlots(SmitheryPartTypes.HANDLE, 1)
                .modifierSlots(SmitheryPartTypes.PICK_HEAD, 2)
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.SHARP,
                        Map.of("damage", 2.0f))
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.MAGNETIZED,
                        Map.of("radius", 5.0f))
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryMaterials() {}
}
