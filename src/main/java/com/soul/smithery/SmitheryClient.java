package com.soul.smithery;

import com.soul.smithery.client.CastingTableRenderer;
import com.soul.smithery.client.FluidPipeRenderer;
import com.soul.smithery.client.ForgeControllerRenderer;
import com.soul.smithery.client.ForgeFuelPortRenderer;
import com.soul.smithery.client.PartPressRenderer;
import com.soul.smithery.client.SmitheryItemColors;
import com.soul.smithery.gui.ForgeControllerScreen;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryEntityTypes;
import com.soul.smithery.registry.SmitheryItems;
import com.soul.smithery.registry.SmitheryMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only bootstrap.
 *
 * <p>Registers block-entity renderers, the controller menu screen, item color handlers that
 * apply per-material color to grayscale textures, and the bow's pull model predicates. Every
 * handler here runs on the mod bus, restricted to {@link Dist#CLIENT}.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmitheryClient {

    private SmitheryClient() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(SmitheryMenus.FORGE_CONTROLLER.get(), ForgeControllerScreen::new);

            // Vanilla-style pull predicates drive the generated bow model's overrides.
            ItemProperties.register(SmitheryItems.BOW.get(),
                    new ResourceLocation("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null || entity.getUseItem() != stack) return 0.0f;
                        return (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0f;
                    });
            ItemProperties.register(SmitheryItems.BOW.get(),
                    new ResourceLocation("pulling"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                    ? 1.0f : 0.0f);
        });
        Smithery.LOGGER.info("Smithery client setup complete.");
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SmitheryBlockEntities.FORGE_CONTROLLER.get(), ForgeControllerRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.CASTING_TABLE.get(), CastingTableRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.FLUID_PIPE.get(), FluidPipeRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.PART_PRESS.get(), PartPressRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.FORGE_FUEL_PORT.get(), ForgeFuelPortRenderer::new);
        event.registerEntityRenderer(SmitheryEntityTypes.ARROW.get(), TippableArrowRenderer::new);
        event.registerEntityRenderer(SmitheryEntityTypes.SHURIKEN.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        SmitheryItemColors.onRegisterItemColors(event);
    }
}
