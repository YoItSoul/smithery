package com.soul.smithery;

import com.mojang.logging.LogUtils;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.content.SmitheryMeltingRecipes;
import com.soul.smithery.content.SmitheryModifiers;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.content.SmitherySynergies;
import com.soul.smithery.content.SmitheryToolTypes;
import com.soul.smithery.registry.SmitheryDataComponents;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Mod entry point.
 *
 * <p>Hosts the {@code @Mod} hook, the shared logger, the creative-mode tab registrations, and
 * orchestrates the load-bearing registration order between Smithery API content, deferred-register
 * content (items / blocks / fluids / etc.), and common setup. The constructor wires every
 * subsystem onto the mod event bus.
 */
@Mod(Smithery.MODID)
public class Smithery {
    /** Mod identifier used as the namespace for every {@code Identifier} this mod creates. */
    public static final String MODID = "smithery";

    /** Shared SLF4J logger for the mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Deferred register backing all Smithery creative-mode tabs. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Creative tab listing every auto-generated PartItem. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PARTS_TAB =
            CREATIVE_MODE_TABS.register("parts_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".parts"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> {
                        var di = SmitheryItems.getBuiltInPart(
                                SmitheryMaterials.IRON, SmitheryPartTypes.SWORD_BLADE.id());
                        return di != null ? di.get().getDefaultInstance()
                                          : net.minecraft.world.item.Items.IRON_INGOT.getDefaultInstance();
                    })
                    .displayItems((params, output) -> SmitheryItems.builtInParts().values()
                            .forEach(di -> output.accept(di.get())))
                    .build());

    /** Creative tab listing Smithery's placeable blocks (forge controller, casting table, etc.). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS_TAB =
            CREATIVE_MODE_TABS.register("blocks_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".blocks"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> com.soul.smithery.registry.SmitheryBlocks.FORGE_CONTROLLER_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FURNACE_BRICKS_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_CONTROLLER_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_FUEL_PORT_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_DRAIN_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_ITEM_PORT_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.CASTING_TABLE_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.CASTING_SAND_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FLUID_PIPE_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.PART_PRESS_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.RED_SLIME_BLOCK_ITEM.get());
                    })
                    .build());

    /** Creative tab listing every registered molten-material bucket. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLUIDS_TAB =
            CREATIVE_MODE_TABS.register("fluids_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".fluids"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> {
                        var ironEntry = com.soul.smithery.registry.SmitheryFluids.forMaterial(
                                com.soul.smithery.content.SmitheryMaterials.IRON);
                        if (ironEntry != null) return ironEntry.bucket.get().getDefaultInstance();
                        var entries = com.soul.smithery.registry.SmitheryFluids.entries();
                        if (!entries.isEmpty()) {
                            return entries.values().iterator().next().bucket.get().getDefaultInstance();
                        }
                        return net.minecraft.world.item.Items.IRON_INGOT.getDefaultInstance();
                    })
                    .displayItems((params, output) -> {
                        for (var entry : com.soul.smithery.registry.SmitheryFluids.entries().values()) {
                            output.accept(entry.bucket.get());
                        }
                    })
                    .build());

    /**
     * Creative tab listing miscellaneous Smithery-crafted resource items (bowstring-class items,
     * intermediates) that aren't parts, placeable blocks, or fluids.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS_TAB =
            CREATIVE_MODE_TABS.register("items_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".items"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SmitheryItems.FLAMESTRING.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(SmitheryItems.FLAMESTRING.get());
                        output.accept(SmitheryItems.BREEZESTRING.get());
                        output.accept(SmitheryItems.RED_SLIME.get());
                        output.accept(SmitheryItems.UNFINISHED_KELP_STRING_1.get());
                        output.accept(SmitheryItems.UNFINISHED_KELP_STRING_2.get());
                        output.accept(SmitheryItems.UNFINISHED_KELP_STRING_3.get());
                        output.accept(SmitheryItems.KELP_STRING.get());
                    })
                    .build());

    /** Creative tab listing one example composed tool per (material x tool type) pair. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS_TAB =
            CREATIVE_MODE_TABS.register("tools_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".tools"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> com.soul.smithery.item.tool.ToolCompositions.apply(
                            SmitheryItems.SWORD.get().getDefaultInstance(),
                            com.soul.smithery.content.SmitheryToolPresets.iron(SmitheryToolTypes.SWORD)))
                    .displayItems((params, output) -> {
                        for (var mat : com.soul.smithery.api.SmitheryAPI.MATERIALS.all()) {
                            for (var tt : com.soul.smithery.api.SmitheryAPI.TOOL_TYPES.all()) {
                                var toolItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                        .getValue(tt.id());
                                if (toolItem == null) continue;
                                if (toolItem instanceof com.soul.smithery.item.tool.SmitheryArmorItem) continue;
                                var stack = new net.minecraft.world.item.ItemStack(toolItem);
                                var comp = com.soul.smithery.content.SmitheryToolPresets.uniform(tt, mat.id());
                                output.accept(com.soul.smithery.item.tool.ToolCompositions.apply(stack, comp));
                            }
                        }
                    })
                    .build());

    /** Creative tab listing one example composed armor piece per (material x armor slot) pair. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARMOR_TAB =
            CREATIVE_MODE_TABS.register("armor_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".armor"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> com.soul.smithery.item.tool.ToolCompositions.apply(
                            SmitheryItems.CHESTPLATE.get().getDefaultInstance(),
                            com.soul.smithery.content.SmitheryToolPresets.iron(SmitheryToolTypes.CHESTPLATE)))
                    .displayItems((params, output) -> {
                        for (var mat : com.soul.smithery.api.SmitheryAPI.MATERIALS.all()) {
                            if (!mat.stats().supportsArmor()) continue;
                            for (var tt : com.soul.smithery.api.SmitheryAPI.TOOL_TYPES.all()) {
                                var toolItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                        .getValue(tt.id());
                                if (!(toolItem instanceof com.soul.smithery.item.tool.SmitheryArmorItem)) continue;
                                var stack = new net.minecraft.world.item.ItemStack(toolItem);
                                var comp = com.soul.smithery.content.SmitheryToolPresets.uniform(tt, mat.id());
                                output.accept(com.soul.smithery.item.tool.ToolCompositions.apply(stack, comp));
                            }
                        }
                    })
                    .build());

    /**
     * Wires Smithery into the mod and event buses.
     *
     * <p>Order is load-bearing: PartTypes/ToolTypes register before materials reference them;
     * modifiers before materials add modifier effects; materials before items/fluids derive from
     * them; synergies after both materials and modifiers exist. Deferred registers attach last.
     */
    public Smithery(IEventBus modEventBus, ModContainer modContainer) {
        SmitheryPartTypes.register();
        SmitheryToolTypes.register();
        com.soul.smithery.content.SmitheryModifierActions.register();
        SmitheryModifiers.register();
        SmitheryMaterials.register();
        SmitherySynergies.register();
        SmitheryMeltingRecipes.register();
        com.soul.smithery.content.example.EnderExampleContent.register();
        com.soul.smithery.registry.SmitheryBlocks.registerImpressedSandVariants();
        com.soul.smithery.registry.SmitheryFluids.bootstrap();

        SmitheryItems.registerBuiltInParts();

        SmitheryMeltingRecipes.registerPartRemeltRecipes();

        com.soul.smithery.registry.SmitheryBlocks.register(modEventBus);
        SmitheryDataComponents.register(modEventBus);
        SmitheryItems.register(modEventBus);
        com.soul.smithery.registry.SmitheryBlockEntities.register(modEventBus);
        com.soul.smithery.registry.SmitheryRecipes.register(modEventBus);
        com.soul.smithery.registry.SmitheryMenus.register(modEventBus);
        com.soul.smithery.registry.SmitheryFluids.register(modEventBus);
        com.soul.smithery.registry.SmitheryEntityTypes.register(modEventBus);
        com.soul.smithery.registry.SmitheryAttachments.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onBuildCreativeTabs);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.soul.smithery.api.forge.ForgeFuels.register(
                    net.minecraft.world.level.material.Fluids.LAVA,
                    new com.soul.smithery.api.forge.ForgeFuels.Profile(1650f));
            var blazeEntry = com.soul.smithery.registry.SmitheryFluids.forMaterial(
                    com.soul.smithery.content.SmitheryMaterials.BLAZE);
            if (blazeEntry != null) {
                com.soul.smithery.api.forge.ForgeFuels.register(
                        blazeEntry.source.get(),
                        new com.soul.smithery.api.forge.ForgeFuels.Profile(3500f));
            }

            com.soul.smithery.api.forge.ForgeMobDrops.setDefault(
                    com.soul.smithery.content.SmitheryMaterials.BLOOD);
            com.soul.smithery.api.forge.ForgeMobDrops.register(
                    net.minecraft.world.entity.animal.fox.Fox.class,
                    com.soul.smithery.content.SmitheryMaterials.FOX_BLOOD);
            com.soul.smithery.api.forge.ForgeMobDrops.register(
                    net.minecraft.world.entity.monster.Blaze.class,
                    com.soul.smithery.content.SmitheryMaterials.BLAZE);
        });

        var api = com.soul.smithery.api.SmitheryAPI.MATERIALS;
        LOGGER.info("Smithery: {} materials × {} part types = {} part items; {} modifiers, {} synergies, {} tool types",
                api.size(),
                com.soul.smithery.api.SmitheryAPI.PART_TYPES.size(),
                SmitheryItems.builtInParts().size(),
                com.soul.smithery.api.SmitheryAPI.MODIFIERS.size(),
                com.soul.smithery.api.SmitheryAPI.SYNERGIES.size(),
                com.soul.smithery.api.SmitheryAPI.TOOL_TYPES.size());
    }

    private void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
    }
}
