package com.soul.smithery.compat.draconic;

import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

/**
 * DE-classloading side of {@link DraconicCompat}. Only ever loaded when Draconic Evolution is
 * installed — keep every DE-typed reference on this side of the fence.
 */
final class DraconicItemFactory {

    private DraconicItemFactory() {}

    static SmitheryToolItem newTool(Item.Properties props, ResourceLocation toolTypeId) {
        return new DraconicSmitheryToolItem(props, toolTypeId);
    }

    static SmitheryArmorItem newArmor(ArmorItem.Type type, Item.Properties props, ResourceLocation id) {
        return new DraconicSmitheryArmorItem(type, props, id);
    }

    /**
     * No DE-side Forge listeners are needed.
     *
     * <p>There was a JUMP_BOOST bridge here. DE's own {@code ModularArmorEventHandler} already
     * resolves the chest slot through {@code IModularArmor.getArmor}, which returns any item
     * implementing that interface — Smithery's chestplate included — so the bridge applied a
     * second boost on top of DE's, and DE's fall-damage credit is sized for one. Kept as the
     * hook point for future bridges that DE genuinely does not cover.
     */
    static void initEvents() {
        // intentionally empty
    }
}
