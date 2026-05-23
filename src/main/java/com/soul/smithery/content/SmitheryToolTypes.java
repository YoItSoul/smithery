package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.resources.Identifier;

/**
 * Built-in tool types: Sword and Pickaxe.
 *
 * Slot composition:
 *   Sword    = blade + guard + handle + binder           (4 slots)
 *   Pickaxe  = pick_head + handle × 2 + binder           (4 slots)
 *
 * Pickaxe's pick_head is a single part (no two-piece head). Two handles support
 * bi-material grips (e.g. wood main + leather fore-grip wrap) — distinct slots that
 * render as distinct layers in the layered tool model.
 *
 * Slot order is significant for shaped recipe consumption order, drives the per-slot
 * layer order in the dynamic tool model, and indexes into {@link ToolComposition#slotMaterials()}.
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
                .addPart(SmitheryPartTypes.PICK_HEAD, DurabilityRole.ADDITIVE)
                .addPart(SmitheryPartTypes.HANDLE, DurabilityRole.ADDITIVE, 2)
                .addPart(SmitheryPartTypes.BINDER, DurabilityRole.MULTIPLIER)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryToolTypes() {}
}
