package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.block.CastingBasinBlock;
import com.soul.smithery.block.CastingTableBlock;
import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.block.ForgeControllerBlock;
import com.soul.smithery.block.ForgeDrainBlock;
import com.soul.smithery.block.ForgeFuelPortBlock;
import com.soul.smithery.block.ForgeItemPortBlock;
import com.soul.smithery.block.ForgeRfCoilBlock;
import com.soul.smithery.block.FurnaceGlassBlock;
import com.soul.smithery.block.PartPressBlock;
import com.soul.smithery.block.RedSlimeBlock;
import com.soul.smithery.item.PartItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Block registrations for the Smithery Forge multiblock and adjacent machinery.
 *
 * <p>The structural shell of the Forge multiblock uses vanilla deepslate variants; only the
 * "active" forge parts (controller, fuel port, drain, item port) live here alongside the
 * Casting Table, Casting Basin, Casting Sand, Fluid Pipe, Part Press and the Red Slime block
 * easter egg.
 */
public final class SmitheryBlocks {
    /** Deferred register for every Smithery-namespaced block. */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Smithery.MODID);

    /** Dust-particle color for casting sand and its impressed variants (dark charcoal gray). */
    private static final int CASTING_SAND_DUST_COLOR = 0xFF3A3A3A;

    /**
     * Mining hardness shared by every forge/furnace block, matched to vanilla stone bricks so
     * the machinery breaks at a familiar speed. Explosion resistance stays per-block — the
     * forge ports keep their blast-proof value.
     */
    private static final float STONE_BRICK_HARDNESS = 1.5f;

    /** Plain structural shell block for the Forge multiblock; the only non-port wall material. */
    public static final RegistryObject<Block> FURNACE_BRICKS =
            BLOCKS.register("furnace_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(STONE_BRICK_HARDNESS, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * Half-height furnace bricks. Exists because the village forge's roof and steps are
     * built from seared slabs, which have no counterpart here; a plain vanilla SlabBlock
     * with the furnace brick texture covers it.
     */
    public static final RegistryObject<SlabBlock> FURNACE_BRICK_SLAB =
            BLOCKS.register("furnace_brick_slab", () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(STONE_BRICK_HARDNESS, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * Shared behaviour for every furnace glass variant: vanilla glass in every respect that
     * matters to the game, so it breaks, spawns and occludes like players expect.
     */
    private static BlockBehaviour.Properties furnaceGlassProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .strength(0.3f)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn((s, l, p, e) -> false)
                .isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false);
    }

    /** Undyed glazing for the forge shell. */
    public static final RegistryObject<FurnaceGlassBlock> FURNACE_GLASS =
            BLOCKS.register("furnace_glass", () -> new FurnaceGlassBlock(furnaceGlassProperties()));

    /**
     * One furnace glass per vanilla dye colour, so a pane can be coloured the same way
     * stained glass is. Registered in {@link DyeColor} order and keyed by colour, which is
     * what the dyeing recipe will look up.
     */
    public static final Map<DyeColor, RegistryObject<FurnaceGlassBlock>> DYED_FURNACE_GLASS =
            new LinkedHashMap<>();

    static {
        for (DyeColor colour : DyeColor.values()) {
            DYED_FURNACE_GLASS.put(colour, BLOCKS.register(
                    colour.getName() + "_furnace_glass",
                    () -> new FurnaceGlassBlock(furnaceGlassProperties())));
        }
    }

    /**
     * Electric heat source. Ships without a recipe on purpose -- what an RF heater should
     * cost is a pack-balance question, so packs define it.
     */
    public static final RegistryObject<ForgeRfCoilBlock> FORGE_RF_COIL =
            BLOCKS.register("forge_rf_coil",
                    () -> new ForgeRfCoilBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(STONE_BRICK_HARDNESS, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .lightLevel(s -> s.getValue(ForgeRfCoilBlock.LIT) ? 10 : 0)));

    /** Forge controller block; the multiblock's master block and only GUI entry point. */
    public static final RegistryObject<ForgeControllerBlock> FORGE_CONTROLLER =
            BLOCKS.register("forge_controller",
                    () -> new ForgeControllerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(STONE_BRICK_HARDNESS, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /** Fuel port block; vertical stacks of these form per-fluid logical groups around the Forge. */
    public static final RegistryObject<ForgeFuelPortBlock> FORGE_FUEL_PORT =
            BLOCKS.register("forge_fuel_port",
                    () -> new ForgeFuelPortBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(STONE_BRICK_HARDNESS, 1200.0f)
                            .sound(SoundType.COPPER)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** Fluid-output port block; drains molten contents of the connected Forge. */
    public static final RegistryObject<ForgeDrainBlock> FORGE_DRAIN =
            BLOCKS.register("forge_drain",
                    () -> new ForgeDrainBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(STONE_BRICK_HARDNESS, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /**
     * Item input port for the Forge multiblock. Accepts items via right-click or hopper
     * insertion and forwards them into the connected forge's nearest empty interior slot;
     * rejects when the forge is full. Counts as part of the shell for structure validation.
     */
    public static final RegistryObject<ForgeItemPortBlock> FORGE_ITEM_PORT =
            BLOCKS.register("forge_item_port",
                    () -> new ForgeItemPortBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(STONE_BRICK_HARDNESS, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /**
     * Casting Table block; sand-casting workbench whose state machine lives on its block entity.
     */
    public static final RegistryObject<CastingTableBlock> CASTING_TABLE =
            BLOCKS.register("casting_table",
                    () -> new CastingTableBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(STONE_BRICK_HARDNESS, 4.0f)
                            .sound(SoundType.WOOD)
                            .requiresCorrectToolForDrops()));

    /**
     * Casting Basin block; block-scale casting vessel whose state machine lives on its block entity.
     */
    public static final RegistryObject<CastingBasinBlock> CASTING_BASIN =
            BLOCKS.register("casting_basin",
                    () -> new CastingBasinBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(STONE_BRICK_HARDNESS, 4.0f)
                            .sound(SoundType.STONE)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    /**
     * Fluid pipe block; carries one fluid at a time with per-face IN/OUT/DISCONNECTED mode
     * encoded into six face-visual EnumProperties so a multipart blockstate can render each
     * face independently.
     */
    public static final RegistryObject<FluidPipeBlock> FLUID_PIPE =
            BLOCKS.register("fluid_pipe",
                    () -> new FluidPipeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(STONE_BRICK_HARDNESS, 6.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    /**
     * Part Press block; in-world part cutting machine with a redstone-driven open/closed
     * pose. Internal state lives on {@link com.soul.smithery.block.entity.PartPressBlockEntity}.
     */
    public static final RegistryObject<PartPressBlock> PART_PRESS =
            BLOCKS.register("part_press",
                    () -> new PartPressBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(STONE_BRICK_HARDNESS, 6.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    /** Casting Sand block; vanilla-sand-equivalent falling block used to prep a Casting Table. */
    public static final RegistryObject<FallingBlock> CASTING_SAND =
            BLOCKS.register("casting_sand",
                    () -> castingSandBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(0.5f)
                            .sound(SoundType.SAND)
                            .pushReaction(PushReaction.NORMAL)));

    /**
     * Red slime block; sticky/bouncy slime block tinted red that also emits a constant
     * redstone signal. Texture, model and blockstate are runtime-synthesized in
     * {@link SmitheryGeneratedPack}.
     */
    public static final RegistryObject<RedSlimeBlock> RED_SLIME_BLOCK =
            BLOCKS.register("red_slime_block",
                    () -> new RedSlimeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .friction(0.8f)
                            .sound(SoundType.SLIME_BLOCK)
                            .noOcclusion()));

    /** BlockItem for {@link #FURNACE_BRICKS}. */
    public static final RegistryObject<BlockItem> FURNACE_BRICKS_ITEM =
            registerBlockItem("furnace_bricks", FURNACE_BRICKS);
    /** BlockItem for {@link #FORGE_RF_COIL}. */
    public static final RegistryObject<BlockItem> FORGE_RF_COIL_ITEM =
            registerBlockItem("forge_rf_coil", FORGE_RF_COIL);
    /** BlockItem for {@link #FURNACE_BRICK_SLAB}. */
    public static final RegistryObject<BlockItem> FURNACE_BRICK_SLAB_ITEM =
            registerBlockItem("furnace_brick_slab", FURNACE_BRICK_SLAB);
    /** BlockItem for {@link #FURNACE_GLASS}. */
    public static final RegistryObject<BlockItem> FURNACE_GLASS_ITEM =
            registerBlockItem("furnace_glass", FURNACE_GLASS);
    /** BlockItems for every dyed furnace glass, keyed by colour. */
    public static final Map<DyeColor, RegistryObject<BlockItem>> DYED_FURNACE_GLASS_ITEMS =
            new LinkedHashMap<>();

    static {
        for (Map.Entry<DyeColor, RegistryObject<FurnaceGlassBlock>> e : DYED_FURNACE_GLASS.entrySet()) {
            DYED_FURNACE_GLASS_ITEMS.put(e.getKey(),
                    registerBlockItem(e.getKey().getName() + "_furnace_glass", e.getValue()));
        }
    }

    /** BlockItem for {@link #FORGE_CONTROLLER}. */
    public static final RegistryObject<BlockItem> FORGE_CONTROLLER_ITEM =
            registerBlockItem("forge_controller", FORGE_CONTROLLER);
    /** BlockItem for {@link #FORGE_FUEL_PORT}. */
    public static final RegistryObject<BlockItem> FORGE_FUEL_PORT_ITEM =
            registerBlockItem("forge_fuel_port", FORGE_FUEL_PORT);
    /** BlockItem for {@link #FORGE_DRAIN}. */
    public static final RegistryObject<BlockItem> FORGE_DRAIN_ITEM =
            registerBlockItem("forge_drain", FORGE_DRAIN);
    /** BlockItem for {@link #FORGE_ITEM_PORT}. */
    public static final RegistryObject<BlockItem> FORGE_ITEM_PORT_ITEM =
            registerBlockItem("forge_item_port", FORGE_ITEM_PORT);
    /** BlockItem for {@link #CASTING_TABLE}. */
    public static final RegistryObject<BlockItem> CASTING_TABLE_ITEM =
            registerBlockItem("casting_table", CASTING_TABLE);
    /** BlockItem for {@link #CASTING_BASIN}. */
    public static final RegistryObject<BlockItem> CASTING_BASIN_ITEM =
            registerBlockItem("casting_basin", CASTING_BASIN);
    /** BlockItem for {@link #CASTING_SAND}. */
    public static final RegistryObject<BlockItem> CASTING_SAND_ITEM =
            registerBlockItem("casting_sand", CASTING_SAND);
    /** BlockItem for {@link #FLUID_PIPE}. */
    public static final RegistryObject<BlockItem> FLUID_PIPE_ITEM =
            registerBlockItem("fluid_pipe", FLUID_PIPE);
    /** BlockItem for {@link #PART_PRESS}. */
    public static final RegistryObject<BlockItem> PART_PRESS_ITEM =
            registerBlockItem("part_press", PART_PRESS);
    /** BlockItem for {@link #RED_SLIME_BLOCK}. */
    public static final RegistryObject<BlockItem> RED_SLIME_BLOCK_ITEM =
            registerBlockItem("red_slime_block", RED_SLIME_BLOCK);

    private static final Map<ResourceLocation, RegistryObject<BlockItem>> IMPRESSED_SAND_ITEMS = new LinkedHashMap<>();

    /**
     * Creates a casting-sand style {@link FallingBlock} with Smithery's dust color.
     * (1.20.1 has no {@code ColoredFallingBlock}; the color is an override instead.)
     */
    private static FallingBlock castingSandBlock(BlockBehaviour.Properties properties) {
        return new FallingBlock(properties) {
            @Override
            public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
                return CASTING_SAND_DUST_COLOR;
            }
        };
    }

    /** Registers the default {@link BlockItem} for a block under the same registry path. */
    private static RegistryObject<BlockItem> registerBlockItem(
            String name, RegistryObject<? extends Block> block) {
        return SmitheryItems.ITEMS.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /**
     * Registers one internal "casting_sand_impressed_&lt;part&gt;" block per registered
     * {@link PartType}.
     *
     * <p>Must be called AFTER {@code SmitheryPartTypes.register()} (so
     * {@link SmitheryAPI#PART_TYPES} is populated) and BEFORE {@link #register(IEventBus)}
     * so the DeferredRegister queue is complete by the time the bus event fires.
     */
    public static void registerImpressedSandVariants() {
        if (!IMPRESSED_SAND_ITEMS.isEmpty()) return;
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            ResourceLocation partId = pt.id();
            String name = "casting_sand_impressed_" + partId.getPath();
            RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new Block(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(0.5f)
                            .sound(SoundType.SAND)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()) {
                        // In-world block name composes from the part name — no per-part lang key.
                        @Override
                        public MutableComponent getName() {
                            return impressedSandName(partId);
                        }
                    });
            RegistryObject<BlockItem> item = SmitheryItems.ITEMS.register(name,
                    () -> new BlockItem(block.get(), new Item.Properties()) {
                        @Override
                        public Component getName(ItemStack stack) {
                            return impressedSandName(partId);
                        }
                    });
            IMPRESSED_SAND_ITEMS.put(partId, item);
        }
    }

    /**
     * Composed display name for an impressed casting-sand block: the {@code "Impressed Sand (%s)"}
     * format string filled with the part type's own translated name. Because the name comes from
     * the part atom, any part type — including runtime-contributed ones — renders with no per-part
     * lang entry.
     *
     * @param partTypeId id of the impressed part type
     * @return the composed "Impressed Sand (&lt;part&gt;)" component
     */
    public static MutableComponent impressedSandName(ResourceLocation partTypeId) {
        return Component.translatable("smithery.casting_sand.impressed",
                Component.translatable(PartItem.partTranslationKey(partTypeId)));
    }

    /**
     * Looks up the impressed-sand BlockItem (the "sand with a PartType-shaped hole" variant)
     * for a given part type.
     *
     * @param partTypeId id of a registered {@link PartType}
     * @return the matching RegistryObject, or null if the part type was never registered
     */
    public static RegistryObject<BlockItem> getImpressedSandItem(ResourceLocation partTypeId) {
        return IMPRESSED_SAND_ITEMS.get(partTypeId);
    }

    /**
     * Binds the block deferred register to the mod event bus.
     *
     * @param modEventBus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private SmitheryBlocks() {}
}
