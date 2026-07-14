package com.soul.smithery;

import com.mojang.logging.LogUtils;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.forge.ForgeFuels;
import com.soul.smithery.api.forge.ForgeMobDrops;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.content.SmitheryMeltingRecipes;
import com.soul.smithery.content.SmitheryModifierActions;
import com.soul.smithery.content.SmitheryModifiers;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.content.SmitherySynergies;
import com.soul.smithery.content.SmitheryToolPresets;
import com.soul.smithery.content.SmitheryToolTypes;
import com.soul.smithery.content.example.EnderExampleContent;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.ToolCompositions;
import com.soul.smithery.network.SmitheryPayloads;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryEntityTypes;
import com.soul.smithery.registry.SmitheryFluids;
import com.soul.smithery.registry.SmitheryItems;
import com.soul.smithery.registry.SmitheryMenus;
import com.soul.smithery.registry.SmitheryRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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
    /** Mod identifier used as the namespace for every {@code ResourceLocation} this mod creates. */
    public static final String MODID = "smithery";

    /** Shared SLF4J logger for the mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Deferred register backing all Smithery creative-mode tabs. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Creative tab listing every auto-generated PartItem. */
    public static final RegistryObject<CreativeModeTab> PARTS_TAB =
            CREATIVE_MODE_TABS.register("parts_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".parts"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> {
                        var part = SmitheryItems.getBuiltInPart(
                                SmitheryMaterials.IRON, SmitheryPartTypes.SWORD_BLADE.id());
                        return part != null ? part.get().getDefaultInstance()
                                            : Items.IRON_INGOT.getDefaultInstance();
                    })
                    .displayItems((params, output) -> SmitheryItems.builtInParts().values()
                            .forEach(part -> output.accept(part.get())))
                    .build());

    /** Creative tab listing Smithery's placeable blocks (forge controller, casting table, etc.). */
    public static final RegistryObject<CreativeModeTab> BLOCKS_TAB =
            CREATIVE_MODE_TABS.register("blocks_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".blocks"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SmitheryBlocks.FORGE_CONTROLLER_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(SmitheryBlocks.FURNACE_BRICKS_ITEM.get());
                        output.accept(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get());
                        output.accept(SmitheryBlocks.FORGE_FUEL_PORT_ITEM.get());
                        output.accept(SmitheryBlocks.FORGE_DRAIN_ITEM.get());
                        output.accept(SmitheryBlocks.FORGE_ITEM_PORT_ITEM.get());
                        output.accept(SmitheryBlocks.CASTING_TABLE_ITEM.get());
                        output.accept(SmitheryBlocks.CASTING_SAND_ITEM.get());
                        output.accept(SmitheryBlocks.FLUID_PIPE_ITEM.get());
                        output.accept(SmitheryBlocks.PART_PRESS_ITEM.get());
                        output.accept(SmitheryBlocks.RED_SLIME_BLOCK_ITEM.get());
                    })
                    .build());

    /** Creative tab listing every registered molten-material bucket. */
    public static final RegistryObject<CreativeModeTab> FLUIDS_TAB =
            CREATIVE_MODE_TABS.register("fluids_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".fluids"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> {
                        var ironEntry = SmitheryFluids.forMaterial(SmitheryMaterials.IRON);
                        if (ironEntry != null) return ironEntry.bucket.get().getDefaultInstance();
                        var entries = SmitheryFluids.entries();
                        if (!entries.isEmpty()) {
                            return entries.values().iterator().next().bucket.get().getDefaultInstance();
                        }
                        return Items.IRON_INGOT.getDefaultInstance();
                    })
                    .displayItems((params, output) -> {
                        for (var entry : SmitheryFluids.entries().values()) {
                            output.accept(entry.bucket.get());
                        }
                    })
                    .build());

    /**
     * Creative tab listing miscellaneous Smithery-crafted resource items (bowstring-class items,
     * intermediates) that aren't parts, placeable blocks, or fluids.
     */
    public static final RegistryObject<CreativeModeTab> ITEMS_TAB =
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
    public static final RegistryObject<CreativeModeTab> TOOLS_TAB =
            CREATIVE_MODE_TABS.register("tools_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".tools"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ToolCompositions.apply(
                            SmitheryItems.SWORD.get().getDefaultInstance(),
                            SmitheryToolPresets.iron(SmitheryToolTypes.SWORD)))
                    .displayItems((params, output) -> {
                        for (Material mat : SmitheryAPI.MATERIALS.all()) {
                            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                                Item toolItem = ForgeRegistries.ITEMS.getValue(tt.id());
                                if (toolItem == null || toolItem == Items.AIR) continue;
                                if (toolItem instanceof SmitheryArmorItem) continue;
                                var comp = SmitheryToolPresets.uniform(tt, mat.id());
                                output.accept(ToolCompositions.apply(new ItemStack(toolItem), comp));
                            }
                        }
                    })
                    .build());

    /** Creative tab listing one example composed armor piece per (material x armor slot) pair. */
    public static final RegistryObject<CreativeModeTab> ARMOR_TAB =
            CREATIVE_MODE_TABS.register("armor_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".armor"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ToolCompositions.apply(
                            SmitheryItems.CHESTPLATE.get().getDefaultInstance(),
                            SmitheryToolPresets.iron(SmitheryToolTypes.CHESTPLATE)))
                    .displayItems((params, output) -> {
                        for (Material mat : SmitheryAPI.MATERIALS.all()) {
                            if (!mat.stats().supportsArmor()) continue;
                            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                                Item toolItem = ForgeRegistries.ITEMS.getValue(tt.id());
                                if (!(toolItem instanceof SmitheryArmorItem)) continue;
                                var comp = SmitheryToolPresets.uniform(tt, mat.id());
                                output.accept(ToolCompositions.apply(new ItemStack(toolItem), comp));
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
    public Smithery() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        SmitheryPartTypes.register();
        SmitheryToolTypes.register();
        SmitheryModifierActions.register();
        SmitheryModifiers.register();
        SmitheryMaterials.register();
        SmitherySynergies.register();
        SmitheryMeltingRecipes.register();
        EnderExampleContent.register();
        SmitheryBlocks.registerImpressedSandVariants();
        SmitheryFluids.bootstrap();

        SmitheryItems.registerBuiltInParts();

        SmitheryMeltingRecipes.registerPartRemeltRecipes();

        SmitheryBlocks.register(modEventBus);
        SmitheryItems.register(modEventBus);
        SmitheryBlockEntities.register(modEventBus);
        SmitheryRecipes.register(modEventBus);
        SmitheryMenus.register(modEventBus);
        SmitheryFluids.register(modEventBus);
        SmitheryEntityTypes.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        SmitheryPayloads.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ForgeFuels.register(Fluids.LAVA, new ForgeFuels.Profile(1650f));
            var blazeEntry = SmitheryFluids.forMaterial(SmitheryMaterials.BLAZE);
            if (blazeEntry != null) {
                ForgeFuels.register(blazeEntry.source.get(), new ForgeFuels.Profile(3500f));
            }

            ForgeMobDrops.setDefault(SmitheryMaterials.BLOOD);
            ForgeMobDrops.register(Fox.class, SmitheryMaterials.FOX_BLOOD);
            ForgeMobDrops.register(Blaze.class, SmitheryMaterials.BLAZE);
        });

        var api = SmitheryAPI.MATERIALS;
        LOGGER.info("Smithery: {} materials × {} part types = {} part items; {} modifiers, {} synergies, {} tool types",
                api.size(),
                SmitheryAPI.PART_TYPES.size(),
                SmitheryItems.builtInParts().size(),
                SmitheryAPI.MODIFIERS.size(),
                SmitheryAPI.SYNERGIES.size(),
                SmitheryAPI.TOOL_TYPES.size());
    }
}
