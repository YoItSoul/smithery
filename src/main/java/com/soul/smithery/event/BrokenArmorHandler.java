package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Strips every attribute modifier from broken smithery armor (see
 * {@link SmitheryArmorItem#isBrokenArmor}). Runs on the live attribute-gather event rather
 * than rewriting the ATTRIBUTE_MODIFIERS component on damage/repair transitions, so any
 * repair path — anvil, future polishing stones, creative — restores stats with no
 * bookkeeping.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class BrokenArmorHandler {
    private BrokenArmorHandler() {}

    @SubscribeEvent
    public static void onGatherAttributes(ItemAttributeModifierEvent event) {
        if (SmitheryArmorItem.isBrokenArmor(event.getItemStack())) {
            event.clearModifiers();
        }
    }
}
