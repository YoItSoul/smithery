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
 * Registers Smithery's custom network payloads and routes their handlers to the appropriate
 * side.
 *
 * <p>Server-to-client payloads route to client-only sinks (the debug renderer); client-to-server
 * payloads route to block-entity setters with proximity validation.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryPayloads {
    private SmitheryPayloads() {}

    /**
     * Registers all Smithery payloads with NeoForge's payload registrar at protocol version
     * {@code "1"}.
     *
     * @param event NeoForge's register-payload-handlers event
     */
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

    private static void onLeakDebugClient(ForgeLeakDebugPayload payload, IPayloadContext context) {
        DebugBoxRenderer.queueLeaks(payload.positions(), payload.durationTicks());
    }

    private static void onSelectOutputFluidServer(ForgeSelectOutputFluidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            if (level.getBlockEntity(payload.controllerPos()) instanceof ForgeControllerBlockEntity be) {
                double dx = payload.controllerPos().getX() + 0.5 - player.getX();
                double dy = payload.controllerPos().getY() + 0.5 - player.getY();
                double dz = payload.controllerPos().getZ() + 0.5 - player.getZ();
                if (dx * dx + dy * dy + dz * dz > 64.0) return;
                Identifier id = payload.fluidId();
                Identifier current = be.outputFluidId();
                be.setOutputFluid(id.equals(current) ? null : id);
            }
        });
    }
}
