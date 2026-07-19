package com.soul.smithery.compat.draconic;

import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;

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

    static void initEvents() {
        MinecraftForge.EVENT_BUS.register(new DraconicEvents());
    }
}
