package com.soul.smithery.network;

import com.soul.smithery.client.DebugBoxRenderer;
import com.soul.smithery.compat.jei.SmitheryJeiPlugin;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

/**
 * Client-side sinks for server-to-client messages. Kept in its own class (invoked behind a
 * dist check in {@link SmitheryPayloads}) so client-only classes never load on a dedicated
 * server.
 */
final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    /** Queues the leak-debug wireframes on the client renderer. */
    static void handleLeakDebug(ForgeLeakDebugPayload payload) {
        DebugBoxRenderer.queueLeaks(payload.positions(), payload.durationTicks());
    }

    /**
     * Installs the server's data-pack registries locally, then nudges JEI to redraw the categories
     * that read them.
     *
     * <p>Skipped entirely when this client owns an integrated server — single-player and LAN hosts
     * share one JVM, so the registries the packet describes are the very maps it would overwrite,
     * and rewriting them from the client thread could tear a read on the server thread.
     */
    static void handleDataSync(SmitheryDataSyncPayload payload) {
        if (Minecraft.getInstance().getSingleplayerServer() != null) return;
        payload.apply();
        // Guarded so the JEI compat class is never resolved in a pack without JEI.
        if (ModList.get().isLoaded("jei")) {
            SmitheryJeiPlugin.refreshDataDrivenRecipes();
        }
    }
}
