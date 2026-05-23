package com.soul.smithery.network;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.client.DebugBoxRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Smithery's custom payload registrations. C2S payloads use playToServer; S2C use playToClient.
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

        reg.playToServer(
                ForgeSelectOutputFluidPayload.TYPE,
                ForgeSelectOutputFluidPayload.STREAM_CODEC,
                SmitheryPayloads::onSelectOutputFluidServer
        );
    }

    /**
     * Client-side handler for ForgeLeakDebugPayload. Defers to DebugBoxRenderer which lives
     * in the client package, so the server never touches client-only code paths.
     */
    private static void onLeakDebugClient(ForgeLeakDebugPayload payload, IPayloadContext context) {
        DebugBoxRenderer.queueLeaks(payload.positions(), payload.durationTicks());
    }

    /**
     * Server-side handler for ForgeSelectOutputFluidPayload. Validates the player is near
     * the controller (to prevent remote-set abuse) and forwards to the BE's setter, which
     * also re-anchors the LinkedHashMap entry so the selected fluid renders at the bottom
     * of the molten stack in the GUI.
     */
    private static void onSelectOutputFluidServer(ForgeSelectOutputFluidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            if (level.getBlockEntity(payload.controllerPos()) instanceof ForgeControllerBlockEntity be) {
                // Anti-cheese: player must be within reasonable range of the controller.
                double dx = payload.controllerPos().getX() + 0.5 - player.getX();
                double dy = payload.controllerPos().getY() + 0.5 - player.getY();
                double dz = payload.controllerPos().getZ() + 0.5 - player.getZ();
                if (dx * dx + dy * dy + dz * dz > 64.0) return;
                // Toggle semantics: clicking the currently-selected fluid clears the selection.
                // Anything else sets it to the requested id (setOutputFluid validates it's stored).
                Identifier id = payload.fluidId();
                Identifier current = be.outputFluidId();
                be.setOutputFluid(id.equals(current) ? null : id);
            }
        });
    }
}
