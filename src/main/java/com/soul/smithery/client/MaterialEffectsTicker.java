package com.soul.smithery.client;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.material.MaterialColorAnimator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Advances the {@link MaterialColorAnimator} clock once per client tick.
 *
 * <p>A single counter increment drives every animated material — per-material work happens
 * lazily (and memoized per tick) inside the animator only for materials actually rendered.
 * Runs on the Forge bus, client dist only; servers never tick the animation clock.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaterialEffectsTicker {

    private MaterialEffectsTicker() {}

    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MaterialColorAnimator.advanceTick();
        }
    }
}
