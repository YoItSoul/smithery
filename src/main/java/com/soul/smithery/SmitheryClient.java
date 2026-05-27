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
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Smithery.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT)
public class SmitheryClient {
    public static final Identifier PART_MATERIAL_TINT_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "part_material");

    public static final Identifier TOOL_PRIMARY_MATERIAL_TINT_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "tool_primary_material");

    public static final Identifier TOOL_SLOT_MATERIAL_TINT_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "tool_slot_material");

    public static final Identifier MOLTEN_BUCKET_TINT_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "molten_bucket");

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
        // Smithery arrow uses vanilla's TippableArrowRenderer — it handles potion-tipped + plain
        // arrows by sampling the entity's color. Smithery arrows extend vanilla Arrow so the
        // existing render pipeline (texture flip on potion contents, in-flight orientation,
        // ground stick) carries over unchanged.
        event.registerEntityRenderer(com.soul.smithery.registry.SmitheryEntityTypes.ARROW.get(),
                net.minecraft.client.renderer.entity.TippableArrowRenderer::new);
    }

    /**
     * Register the smithery:part_material tint source. Item definition JSONs at
     * assets/smithery/items/<material>_<part>.json reference this source by ID; the
     * tint source itself reads the material color from the PartItem at render time.
     */
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
