package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

/**
 * Built-in tool types: Sword and Pickaxe.
 *
 * Slot composition matches the design doc:
 *   Sword    = blade + guard + handle + binder
 *   Pickaxe  = pick_head × 2 + handle × 2 + binder
 *
 * Slot order is significant for shaped recipe consumption order — but does NOT determine
 * the 3×3 grid layout; that lives in the shaped recipe data file.
 */
public final class SmitheryToolTypes {
    public static ToolType SWORD;
    public static ToolType PICKAXE;

    public static void register() {
        SWORD = SmitheryAPI.registerToolType(ToolType.builder(id("sword"))
                .addPart(SmitheryPartTypes.SWORD_BLADE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.GUARD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());

        PICKAXE = SmitheryAPI.registerToolType(ToolType.builder(id("pickaxe"))
                .addPart(SmitheryPartTypes.PICK_HEAD, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryToolTypes() {}
}
