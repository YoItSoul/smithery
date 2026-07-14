package com.soul.smithery;

import com.soul.smithery.client.CastingTableRenderer;
import com.soul.smithery.client.FluidPipeRenderer;
import com.soul.smithery.client.ForgeControllerRenderer;
import com.soul.smithery.client.MoltenBucketTintSource;
import com.soul.smithery.client.PartMaterialTintSource;
import com.soul.smithery.client.PartPressRenderer;
import com.soul.smithery.client.SmitheryFluidsClient;
import com.soul.smithery.client.ToolPrimaryMaterialTintSource;
import com.soul.smithery.client.ToolSlotMaterialTintSource;
import com.soul.smithery.gui.ForgeControllerScreen;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterFluidModelsEvent;
import net.minecraftforge.client.event.RegisterMenuScreensEvent;
import net.minecraftforge.client.gui.ConfigurationScreen;
import net.minecraftforge.client.gui.IConfigScreenFactory;

/**
 * Client-only mod entry point.
 *
 * <p>Registers block-entity renderers, menu screens, fluid models, and the item tint sources that
 * apply per-material color to grayscale part textures. Loaded only on {@link Dist#CLIENT}.
 */
@Mod(value = Smithery.MODID, dist = Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT)
public class SmitheryClient {
    /** Tint-source id read from part-item definition JSONs to color a single PartItem. */
    public static final ResourceLocation PART_MATERIAL_TINT_ID =
            new ResourceLocation(Smithery.MODID, "part_material");

    /** Tint-source id for a composed tool's primary (lookup-driven) material layer. */
    public static final ResourceLocation TOOL_PRIMARY_MATERIAL_TINT_ID =
            new ResourceLocation(Smithery.MODID, "tool_primary_material");

    /** Tint-source id for a per-slot material layer on a composed tool. */
    public static final ResourceLocation TOOL_SLOT_MATERIAL_TINT_ID =
            new ResourceLocation(Smithery.MODID, "tool_slot_material");

    /** Tint-source id for a molten-material bucket item. */
    public static final ResourceLocation MOLTEN_BUCKET_TINT_ID =
            new ResourceLocation(Smithery.MODID, "molten_bucket");

    /** Registers the NeoForge mod-config screen factory for Smithery. */
    public SmitheryClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Smithery.LOGGER.info("Smithery client setup complete.");
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SmitheryBlockEntities.FORGE_CONTROLLER.get(), ForgeControllerRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.CASTING_TABLE.get(), CastingTableRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.FLUID_PIPE.get(), FluidPipeRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.PART_PRESS.get(), PartPressRenderer::new);
        event.registerBlockEntityRenderer(SmitheryBlockEntities.FORGE_FUEL_PORT.get(),
                com.soul.smithery.client.ForgeFuelPortRenderer::new);
        event.registerEntityRenderer(com.soul.smithery.registry.SmitheryEntityTypes.ARROW.get(),
                net.minecraft.client.renderer.entity.TippableArrowRenderer::new);
        event.registerEntityRenderer(com.soul.smithery.registry.SmitheryEntityTypes.SHURIKEN.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(SmitheryMenus.FORGE_CONTROLLER.get(), ForgeControllerScreen::new);
    }

    @SubscribeEvent
    static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        SmitheryFluidsClient.onRegisterFluidModels(event);
    }

    @SubscribeEvent
    static void onRegisterTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(PART_MATERIAL_TINT_ID, PartMaterialTintSource.MAP_CODEC);
        event.register(TOOL_PRIMARY_MATERIAL_TINT_ID, ToolPrimaryMaterialTintSource.MAP_CODEC);
        event.register(TOOL_SLOT_MATERIAL_TINT_ID, ToolSlotMaterialTintSource.MAP_CODEC);
        event.register(MOLTEN_BUCKET_TINT_ID, MoltenBucketTintSource.MAP_CODEC);
    }
}
