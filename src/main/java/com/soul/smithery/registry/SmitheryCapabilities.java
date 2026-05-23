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
 * Capability registrations for Smithery block entities. Exposes the new NeoForge 26.1
 * {@code ResourceHandler<FluidResource>} on:
 *   - {@link FluidPipeBlockEntity}: sided handler respecting per-face FaceMode
 *     (DISCONNECTED refuses both, OUT refuses insert, IN refuses extract).
 *   - {@link CastingTableBlockEntity}: write-only handler that accepts fluid only while
 *     the table is IMPRESSED or partially-FILLING. Internal state machine guards transitions.
 *
 * Note on capability path: NeoForge 26.1.x replaced {@code Capabilities.FluidHandler.BLOCK}
 * (which used the old {@code IFluidHandler}/{@code FluidStack} pair) with
 * {@link Capabilities.Fluid#BLOCK} (uses {@code ResourceHandler<FluidResource>} +
 * the new transaction API). Anything wired against the old path no longer compiles.
 *
 * RegisterCapabilitiesEvent is a mod-bus event; the @EventBusSubscriber annotation here
 * routes by event type (no bus= attribute in this NeoForge version).
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryCapabilities {

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Pipes intentionally do NOT expose a fluid capability — they're passive channels
        // in the new "pipes-are-just-channels" model. The forge drain is the source pump;
        // sinks (casting tables, tanks, ports) self-gate via their own handlers.

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                SmitheryBlockEntities.CASTING_TABLE.get(),
                (be, side) -> be.fluidHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                SmitheryBlockEntities.FORGE_FUEL_PORT.get(),
                (be, side) -> be.fluidHandlerFor(side));

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                SmitheryBlockEntities.FORGE_DRAIN.get(),
                (be, side) -> be.fluidHandlerFor(side));
    }

    private SmitheryCapabilities() {}
}
