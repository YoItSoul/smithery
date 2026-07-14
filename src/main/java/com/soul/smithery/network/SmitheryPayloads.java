package com.soul.smithery.network;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Registers Smithery's network channel and routes message handlers to the appropriate side.
 *
 * <p>Server-to-client messages route to client-only sinks (the debug renderer, behind a
 * dist check so the class never loads on a dedicated server); client-to-server messages
 * route to block-entity setters with proximity validation.
 */
public final class SmitheryPayloads {

    private static final String PROTOCOL_VERSION = "1";

    /** Smithery's single game channel. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private SmitheryPayloads() {}

    /**
     * Registers all Smithery messages on the channel. Called once from the mod constructor.
     */
    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(ForgeLeakDebugPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeLeakDebugPayload::encode)
                .decoder(ForgeLeakDebugPayload::decode)
                .consumerMainThread(SmitheryPayloads::onLeakDebugClient)
                .add();

        CHANNEL.messageBuilder(ForgeSelectOutputFluidPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ForgeSelectOutputFluidPayload::encode)
                .decoder(ForgeSelectOutputFluidPayload::decode)
                .consumerMainThread(SmitheryPayloads::onSelectOutputFluidServer)
                .add();
    }

    /** Sends a message to one player's client. */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Sends a message from the client to the server. */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static void onLeakDebugClient(ForgeLeakDebugPayload payload, Supplier<NetworkEvent.Context> ctx) {
        // Indirect through a client-only handler class so the renderer never classloads
        // on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPayloadHandler.handleLeakDebug(payload));
        ctx.get().setPacketHandled(true);
    }

    private static void onSelectOutputFluidServer(ForgeSelectOutputFluidPayload payload,
                                                  Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            var level = player.serverLevel();
            // Distance-check before the block-entity lookup: getBlockEntity can force-load
            // chunks, so an arbitrary far-away position must be rejected first.
            double dx = payload.controllerPos().getX() + 0.5 - player.getX();
            double dy = payload.controllerPos().getY() + 0.5 - player.getY();
            double dz = payload.controllerPos().getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz <= 64.0
                    && level.getBlockEntity(payload.controllerPos()) instanceof ForgeControllerBlockEntity be) {
                ResourceLocation id = payload.fluidId();
                ResourceLocation current = be.outputFluidId();
                be.setOutputFluid(id.equals(current) ? null : id);
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
