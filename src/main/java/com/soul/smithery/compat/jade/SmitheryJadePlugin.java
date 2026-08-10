package com.soul.smithery.compat.jade;

import com.soul.smithery.block.CastingBasinBlock;
import com.soul.smithery.block.CastingTableBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration root for Smithery.
 *
 * <p>Discovered by Jade's own annotation scan, so the rest of the mod has no hard dependency on it
 * and this class never loads in a pack without Jade installed.
 *
 * <p>Only client components are registered. Jade's usual server-data channel exists for block
 * entities whose state the client cannot see, but both casting vessels already push their full
 * state to every tracking client on each change — the renderers draw the cooling fade from it —
 * so the providers read the client-side block entity directly. That also gets a smoother countdown
 * than a server round-trip would: {@code clientTick} predicts the cooling timer between syncs.
 */
@WailaPlugin
public class SmitheryJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CastingTableJadeProvider.INSTANCE, CastingTableBlock.class);
        registration.registerBlockComponent(CastingBasinJadeProvider.INSTANCE, CastingBasinBlock.class);
    }
}
