package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.network.SmitheryDataSyncPayload;
import com.soul.smithery.network.SmitheryPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ships Smithery's data-pack-driven registries to clients whenever vanilla ships its own.
 *
 * <p>Alloy recipes, Casting Basin casts and anvil modifier sources are filled by
 * {@code AddReloadListenerEvent} listeners, which run only on the logical server. Vanilla solves the
 * same problem for recipes and tags with a login-time sync; this is the equivalent for ours, so a
 * client on a dedicated server holds the same registries the server does instead of just the
 * hard-coded layer.
 *
 * <p>Forge fires {@link OnDatapackSyncEvent} with a player on join and with none after
 * {@code /reload}, which is exactly the pair of moments the client's copy goes stale.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryDataSyncHandler {

    private SmitheryDataSyncHandler() {}

    /**
     * Sends the current data layer to the joining player, or to everyone online after a reload.
     *
     * @param event Forge's datapack-sync event
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SmitheryDataSyncPayload payload = SmitheryDataSyncPayload.snapshot();
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            SmitheryPayloads.sendToPlayer(joining, payload);
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            SmitheryPayloads.sendToPlayer(player, payload);
        }
    }
}
