package com.soul.smithery.network;

import com.soul.smithery.client.DebugBoxRenderer;

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
}
