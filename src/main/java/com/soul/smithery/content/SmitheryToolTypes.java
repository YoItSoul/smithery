package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

/**
 * Built-in tool types.
 *
 * Slot composition:
 *   Sword    = blade + guard + handle + binder           (4 slots)
 *   Pickaxe  = pick_head   + handle × 2 + binder         (4 slots)
 *   Axe      = axe_head    + handle × 2 + binder         (4 slots)
 *   Shovel   = shovel_head + handle × 2 + binder         (4 slots)
 *   Hoe      = hoe_head    + handle × 2 + binder         (4 slots)
 *   Spear    = spear_head  + handle × 2 + binder         (4 slots)
 *   Bow      = bow_limb × 2 + bowstring                  (3 slots)
 *   Arrow    = arrow_head + arrow_shaft + fletching      (3 slots)
 *
 * The "head + 2×handle + binder" family all share the same shaped-recipe layout. Two
 * handles support bi-material grips (e.g. wood main + leather fore-grip wrap) — distinct
 * slots that render as distinct layers in the layered tool model.
 *
 * Ranged: the bow is the launcher (no binder; the bowstring serves the multiplicative role
 * via its material's binderMultiplier when used in that slot). The arrow is the ammunition —
 * structurally a 3-part tool whose "durability" represents shots remaining.
 *
 * Slot order is significant for shaped recipe consumption order, drives the per-slot
 * layer order in the dynamic tool model, and indexes into {@link ToolComposition#slotMaterials()}.
 */
public final class SmitheryToolTypes {
    public static ToolType SWORD;
    public static ToolType PICKAXE;
    public static ToolType AXE;
    public static ToolType SHOVEL;
    public static ToolType HOE;
    public static ToolType SPEAR;
    public static ToolType BOW;
    public static ToolType ARROW;

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

        // Bow: two limbs (additive durability) + bowstring (acts multiplicatively, like a binder —
        // the string's material binderMultiplier scales final bow durability). 3-slot recipe.
        BOW = SmitheryAPI.registerToolType(ToolType.builder(id("bow"))
                .addPart(SmitheryPartTypes.BOW_LIMB, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BOWSTRING, DurabilityRole.MULTIPLIER)
                .build());

        // Arrow: arrow_head (additive — drives damage/effects) + shaft (additive — drives shots
        // remaining) + fletching (multiplicative — small durability scalar via binderMultiplier).
        // 3-slot recipe. Each crafted arrow is a single ItemStack whose durability == shots left.
        ARROW = SmitheryAPI.registerToolType(ToolType.builder(id("arrow"))
                .addPart(SmitheryPartTypes.ARROW_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.ARROW_SHAFT, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.FLETCHING, DurabilityRole.MULTIPLIER)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryToolTypes() {}
}
