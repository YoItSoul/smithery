package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.ForgeDrainBlockEntity;
import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability registrations for Smithery block entities.
 *
 * <p>Wires the NeoForge 26.1 {@code ResourceHandler}-based fluid and item capabilities to
 * every Smithery block entity that exposes one: casting table, fuel port, drain, part press
 * and item port. Pipes intentionally do NOT expose a fluid capability — they're passive
 * channels in the "pipes-are-just-channels" model.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryCapabilities {

    /**
     * Registers fluid and item capabilities on every relevant Smithery block entity.
     *
     * @param event the {@link RegisterCapabilitiesEvent} delivered on the mod event bus
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                SmitheryBlockEntities.CASTING_TABLE.get(),
                (be, side) -> be.fluidHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                SmitheryBlockEntities.CASTING_TABLE.get(),
                (be, side) -> be.itemHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                SmitheryBlockEntities.FORGE_FUEL_PORT.get(),
                (be, side) -> be.fluidHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                SmitheryBlockEntities.FORGE_DRAIN.get(),
                (be, side) -> be.fluidHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                SmitheryBlockEntities.PART_PRESS.get(),
                (be, side) -> be.itemHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                SmitheryBlockEntities.FORGE_ITEM_PORT.get(),
                (be, side) -> be.itemHandlerFor(side));
    }

    private SmitheryCapabilities() {}
}
