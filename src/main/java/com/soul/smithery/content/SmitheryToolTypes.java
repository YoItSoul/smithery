package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.resources.Identifier;

/**
 * Built-in tool-type registrations.
 *
 * <p>Slot order matters: it drives shaped-recipe consumption order, layered-model render order,
 * and indexing into {@link ToolComposition#slotMaterials()}. The head + 2 handles + binder family
 * shares the same shaped recipe layout across pickaxe / axe / shovel / hoe / spear.
 */
public final class SmitheryToolTypes {
    /** Tool type for swords (blade + guard + handle + binder). */
    public static ToolType SWORD;
    /** Tool type for pickaxes (head + 2 handles + binder). */
    public static ToolType PICKAXE;
    /** Tool type for axes (head + 2 handles + binder). */
    public static ToolType AXE;
    /** Tool type for shovels (head + 2 handles + binder). */
    public static ToolType SHOVEL;
    /** Tool type for hoes (head + 2 handles + binder). */
    public static ToolType HOE;
    /** Tool type for spears (head + 2 handles + binder). */
    public static ToolType SPEAR;
    /** Tool type for bows (2 limbs + bowstring). */
    public static ToolType BOW;
    /** Tool type for arrows (head + shaft + fletching). */
    public static ToolType ARROW;
    /** Tool type for helmets (helmet core + armor plates + armor trim). */
    public static ToolType HELMET;
    /** Tool type for chestplates (chestplate core + armor plates + armor trim). */
    public static ToolType CHESTPLATE;
    /** Tool type for leggings (leggings core + armor plates + armor trim). */
    public static ToolType LEGGINGS;
    /** Tool type for boots (boots core + armor plates + armor trim). */
    public static ToolType BOOTS;

    /**
     * Registers every built-in tool type. Must run after {@link SmitheryPartTypes#register()}.
     */
    public static void register() {
        SWORD = SmitheryAPI.registerToolType(ToolType.builder(id("sword"))
                .addPart(SmitheryPartTypes.SWORD_BLADE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.GUARD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        PICKAXE = SmitheryAPI.registerToolType(ToolType.builder(id("pickaxe"))
                .addPart(SmitheryPartTypes.PICK_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        AXE = SmitheryAPI.registerToolType(ToolType.builder(id("axe"))
                .addPart(SmitheryPartTypes.AXE_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        SHOVEL = SmitheryAPI.registerToolType(ToolType.builder(id("shovel"))
                .addPart(SmitheryPartTypes.SHOVEL_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        HOE = SmitheryAPI.registerToolType(ToolType.builder(id("hoe"))
                .addPart(SmitheryPartTypes.HOE_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        SPEAR = SmitheryAPI.registerToolType(ToolType.builder(id("spear"))
                .addPart(SmitheryPartTypes.SPEAR_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        BOW = SmitheryAPI.registerToolType(ToolType.builder(id("bow"))
                .addPart(SmitheryPartTypes.BOW_LIMB, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BOWSTRING, DurabilityRole.MULTIPLIER)
                .build());

        ARROW = SmitheryAPI.registerToolType(ToolType.builder(id("arrow"))
                .addPart(SmitheryPartTypes.ARROW_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.ARROW_SHAFT, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.FLETCHING, DurabilityRole.MULTIPLIER)
                .build());

        HELMET = SmitheryAPI.registerToolType(ToolType.builder(id("helmet"))
                .addPart(SmitheryPartTypes.HELMET_CORE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.ARMOR_PLATES, DurabilityRole.MULTIPLIER)
                .addPart(SmitheryPartTypes.ARMOR_TRIM, DurabilityRole.ADDITIVE)
                .build());

        CHESTPLATE = SmitheryAPI.registerToolType(ToolType.builder(id("chestplate"))
                .addPart(SmitheryPartTypes.CHESTPLATE_CORE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.ARMOR_PLATES, DurabilityRole.MULTIPLIER)
                .addPart(SmitheryPartTypes.ARMOR_TRIM, DurabilityRole.ADDITIVE)
                .build());

        LEGGINGS = SmitheryAPI.registerToolType(ToolType.builder(id("leggings"))
                .addPart(SmitheryPartTypes.LEGGINGS_CORE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.ARMOR_PLATES, DurabilityRole.MULTIPLIER)
                .addPart(SmitheryPartTypes.ARMOR_TRIM, DurabilityRole.ADDITIVE)
                .build());

        BOOTS = SmitheryAPI.registerToolType(ToolType.builder(id("boots"))
                .addPart(SmitheryPartTypes.BOOTS_CORE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.ARMOR_PLATES, DurabilityRole.MULTIPLIER)
                .addPart(SmitheryPartTypes.ARMOR_TRIM, DurabilityRole.ADDITIVE)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryToolTypes() {}
}
