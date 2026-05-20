package com.soul.smithery.network;

import com.soul.smithery.Smithery;
import com.soul.smithery.client.DebugBoxRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Smithery's custom payload registrations. Currently only the forge-leak debug visualization
 * goes over the wire; expand here as new packets are added.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryPayloads {
    private SmitheryPayloads() {}

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        reg.playToClient(
                ForgeLeakDebugPayload.TYPE,
                ForgeLeakDebugPayload.STREAM_CODEC,
                SmitheryPayloads::onLeakDebugClient
        );
    }

    /**
     * Client-side handler for ForgeLeakDebugPayload. Defers to DebugBoxRenderer which lives
     * in the client package, so the server never touches client-only code paths.
     */
    private static void onLeakDebugClient(ForgeLeakDebugPayload payload, IPayloadContext context) {
        DebugBoxRenderer.queueLeaks(payload.positions(), payload.durationTicks());
    }
}
