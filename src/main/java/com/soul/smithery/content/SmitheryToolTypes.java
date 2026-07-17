package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.resources.ResourceLocation;

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
    /** Tool type for broadswords (large blade + guard + handle + binder) — slow, heavy hits. */
    public static ToolType BROADSWORD;
    /** Tool type for rapiers (blade + 2 guards + handle + binder) — fast, armor-piercing. */
    public static ToolType RAPIER;
    /** Tool type for paxels (all four tool heads + 2 handles + binder) — the everything-tool. */
    public static ToolType PAXEL;
    /** Tool type for mining hammers (hammer head + 2 large plates + handle + binder) — 3x3 mining. */
    public static ToolType MINING_HAMMER;
    /** Tool type for crossbows (bow limb + handle stock + binder + bowstring). */
    public static ToolType CROSSBOW;
    /** Tool type for kamas (kama head + handle + binder) — shears + 3x3 double-yield harvesting. */
    public static ToolType KAMA;
    public static ToolType SCYTHE;
    public static ToolType SCEPTRE;
    /** Tool type for cleavers (large blade + large plate + 2 handles + binder) — slow, huge hits, beheading. */
    public static ToolType CLEAVER;
    /** Tool type for lumberaxes (2 axe heads + large plate + handle + binder) — fells whole trees. */
    public static ToolType LUMBERAXE;
    /** Tool type for excavators (2 shovel heads + large plate + handle + binder) — 3x3 digging. */
    public static ToolType EXCAVATOR;
    /** Tool type for shurikens (4 blades) — stackable thrown weapon. */
    public static ToolType SHURIKEN;
    /** Tool type for tridents (3 spear heads + 2 handles + binder) — throwable heavy spear. */
    public static ToolType TRIDENT;
    /** Tool type for battlesigns (large plate + handle + binder) — a weapon that partially blocks. */
    public static ToolType BATTLESIGN;
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

        BROADSWORD = SmitheryAPI.registerToolType(ToolType.builder(id("broadsword"))
                .addPart(SmitheryPartTypes.LARGE_BLADE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.GUARD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        RAPIER = SmitheryAPI.registerToolType(ToolType.builder(id("rapier"))
                .addPart(SmitheryPartTypes.SWORD_BLADE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.GUARD, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        PAXEL = SmitheryAPI.registerToolType(ToolType.builder(id("paxel"))
                .addPart(SmitheryPartTypes.PICK_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.AXE_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.SHOVEL_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HOE_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        MINING_HAMMER = SmitheryAPI.registerToolType(ToolType.builder(id("mining_hammer"))
                .addPart(SmitheryPartTypes.HAMMER_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.LARGE_PLATE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        BOW = SmitheryAPI.registerToolType(ToolType.builder(id("bow"))
                .addPart(SmitheryPartTypes.BOW_LIMB, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BOWSTRING, DurabilityRole.MULTIPLIER)
                .build());

        KAMA = SmitheryAPI.registerToolType(ToolType.builder(id("kama"))
                .addPart(SmitheryPartTypes.KAMA_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        // TConEvo sceptre: magic weapon - right-click fires an arcane bolt.
        SCEPTRE = SmitheryAPI.registerToolType(ToolType.builder(id("sceptre"))
                .addPart(SmitheryPartTypes.ARCANE_FOCUS, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        // GC-staple harvest weapon: long two-handed kama that clears a 3x3x3 of plants.
        SCYTHE = SmitheryAPI.registerToolType(ToolType.builder(id("scythe"))
                .addPart(SmitheryPartTypes.KAMA_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        CLEAVER = SmitheryAPI.registerToolType(ToolType.builder(id("cleaver"))
                .addPart(SmitheryPartTypes.LARGE_BLADE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.LARGE_PLATE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        LUMBERAXE = SmitheryAPI.registerToolType(ToolType.builder(id("lumberaxe"))
                .addPart(SmitheryPartTypes.AXE_HEAD, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.LARGE_PLATE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        EXCAVATOR = SmitheryAPI.registerToolType(ToolType.builder(id("excavator"))
                .addPart(SmitheryPartTypes.SHOVEL_HEAD, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.LARGE_PLATE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        SHURIKEN = SmitheryAPI.registerToolType(ToolType.builder(id("shuriken"))
                .addPart(SmitheryPartTypes.SHURIKEN_BLADE, DurabilityRole.ADDITIVE, 4)
                .build());

        TRIDENT = SmitheryAPI.registerToolType(ToolType.builder(id("trident"))
                .addPart(SmitheryPartTypes.SPEAR_HEAD, DurabilityRole.ADDITIVE, 3)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        BATTLESIGN = SmitheryAPI.registerToolType(ToolType.builder(id("battlesign"))
                .addPart(SmitheryPartTypes.LARGE_PLATE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        CROSSBOW = SmitheryAPI.registerToolType(ToolType.builder(id("crossbow"))
                .addPart(SmitheryPartTypes.BOW_LIMB, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryToolTypes() {}
}
