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

@Mod(Smithery.MODID)
public class Smithery {
    public static final String MODID = "smithery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FORGE_TAB =
            CREATIVE_MODE_TABS.register("forge_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".forge"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> com.soul.smithery.registry.SmitheryBlocks.FORGE_CONTROLLER_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FURNACE_BRICKS_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_CONTROLLER_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_FUEL_PORT_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FORGE_DRAIN_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.CASTING_TABLE_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.CASTING_SAND_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.FLUID_PIPE_ITEM.get());
                        output.accept(com.soul.smithery.registry.SmitheryBlocks.PART_PRESS_ITEM.get());
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOLTEN_TAB =
            CREATIVE_MODE_TABS.register("molten_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".molten"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> {
                        // Default-icon fallback: iron bucket if registered, otherwise the first
                        // registered molten bucket, otherwise vanilla iron ingot as a last resort.
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
                        // Iterate the live fluid registry so anything dynamically added
                        // by a downstream mod's material registration shows up automatically.
                        // Only buckets — LiquidBlocks have no BlockItem so adding them would
                        // pass an empty (count=0) ItemStack to Output.accept, which throws
                        // "The stack count must be 1". Matches vanilla, where lava/water
                        // appear in creative only as buckets.
                        for (var entry : com.soul.smithery.registry.SmitheryFluids.entries().values()) {
                            output.accept(entry.bucket.get());
                        }
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS_TAB =
            CREATIVE_MODE_TABS.register("tools_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".tools"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> com.soul.smithery.item.tool.SmitheryToolItem.applyComposition(
                            SmitheryItems.SWORD.get().getDefaultInstance(),
                            com.soul.smithery.content.SmitheryToolPresets.iron(SmitheryToolTypes.SWORD)))
                    .displayItems((params, output) -> {
                        // Show one example tool per material as a starter set until recipes land.
                        for (var mat : com.soul.smithery.api.SmitheryAPI.MATERIALS.all()) {
                            output.accept(com.soul.smithery.item.tool.SmitheryToolItem.applyComposition(
                                    SmitheryItems.SWORD.get().getDefaultInstance(),
                                    com.soul.smithery.content.SmitheryToolPresets.uniform(SmitheryToolTypes.SWORD, mat.id())));
                            output.accept(com.soul.smithery.item.tool.SmitheryToolItem.applyComposition(
                                    SmitheryItems.PICKAXE.get().getDefaultInstance(),
                                    com.soul.smithery.content.SmitheryToolPresets.uniform(SmitheryToolTypes.PICKAXE, mat.id())));
                        }
                    })
                    .build());

    public Smithery(IEventBus modEventBus, ModContainer modContainer) {
        // 1. Register Smithery API entries — order is load-bearing:
        //    - PartTypes/ToolTypes must exist before materials reference them.
        //    - Modifiers must exist before MaterialStats.addModifier(...) calls resolve them.
        //    - Materials must exist before items are queued.
        //    - Synergies need both Materials and Modifiers registered first.
        SmitheryPartTypes.register();
        SmitheryToolTypes.register();
        // Action library must register BEFORE modifier reload (server start) so JSON modifiers
        // can resolve their action type ids. Also before SmitheryModifiers in case code-defined
        // modifiers ever want to reference action types directly.
        com.soul.smithery.content.SmitheryModifierActions.register();
        SmitheryModifiers.register();
        SmitheryMaterials.register();
        SmitherySynergies.register();
        SmitheryMeltingRecipes.register();
        // Modder-example bundle: registers material smithery:ender + smithery:pearl cast.
        // Lives in its own class as a copy-paste reference for modders extending the system.
        com.soul.smithery.content.example.EnderExampleContent.register();
        // Queue per-PartType "sand with cutout" block registrations now that PartTypes
        // exist. Must happen before SmitheryBlocks.register(modEventBus) attaches the
        // DeferredRegister to the bus.
        com.soul.smithery.registry.SmitheryBlocks.registerImpressedSandVariants();
        // Build per-material molten fluid entries from the populated MATERIALS registry.
        // Must happen AFTER SmitheryMaterials.register() and BEFORE SmitheryFluids.register
        // (which hands the DeferredRegisters to the mod bus).
        com.soul.smithery.registry.SmitheryFluids.bootstrap();

        // 2. Now that all built-in materials are in the registry, queue one PartItem per
        //    (material × part type) pair into the deferred item register.
        SmitheryItems.registerBuiltInParts();

        // Auto-register a remelt recipe for every PartItem we just queued: putting the part
        // back into the forge yields exactly its castMb of the source material. Lossless
        // "uncraft" path for broken / unwanted parts. Runs here so modder content registered
        // between this point and SmitheryMaterials.register() is included too.
        SmitheryMeltingRecipes.registerPartRemeltRecipes();

        // 3. Hook deferred registers to the mod event bus so they fire at RegisterEvent time.
        // Block registration must occur before item registration completes so that BlockItem
        // suppliers can resolve their backing blocks; both are bound to the same bus event so
        // order of attach doesn't matter, only that they all attach.
        com.soul.smithery.registry.SmitheryBlocks.register(modEventBus);
        SmitheryDataComponents.register(modEventBus);
        SmitheryItems.register(modEventBus);
        com.soul.smithery.registry.SmitheryBlockEntities.register(modEventBus);
        com.soul.smithery.registry.SmitheryRecipes.register(modEventBus);
        com.soul.smithery.registry.SmitheryMenus.register(modEventBus);
        com.soul.smithery.registry.SmitheryFluids.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onBuildCreativeTabs);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Forge fuels — must run after Fluids deferred-register fires (i.e. now in common
        // setup). Modders can register their own hot fuels by calling ForgeFuels.register
        // from their own setup event. Built-ins: vanilla lava (1650°C — vanilla forge target)
        // and smithery:molten_blaze (3500°C — high enough to melt netherite, gates post-nether
        // material recipes behind blaze farming).
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
        // Parts tab already populates its own items via displayItems.
        // This hook is reserved for adding our items into vanilla tabs if needed later.
    }
}
