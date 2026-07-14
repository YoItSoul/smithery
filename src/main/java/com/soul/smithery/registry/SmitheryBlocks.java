package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.block.CastingTableBlock;
import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.block.ForgeControllerBlock;
import com.soul.smithery.block.ForgeDrainBlock;
import com.soul.smithery.block.ForgeFuelPortBlock;
import com.soul.smithery.block.ForgeItemPortBlock;
import com.soul.smithery.block.PartPressBlock;
import com.soul.smithery.block.RedSlimeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
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
 * Casting Table, Casting Sand, Fluid Pipe, Part Press and the Red Slime block easter egg.
 */
public final class SmitheryBlocks {
    /** Deferred register for every Smithery-namespaced block. */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Smithery.MODID);

    /** Dust-particle color for casting sand and its impressed variants (dark charcoal gray). */
    private static final int CASTING_SAND_DUST_COLOR = 0xFF3A3A3A;

    /** Plain structural shell block for the Forge multiblock; the only non-port wall material. */
    public static final RegistryObject<Block> FURNACE_BRICKS =
            BLOCKS.register("furnace_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)));

    /** Forge controller block; the multiblock's master block and only GUI entry point. */
    public static final RegistryObject<ForgeControllerBlock> FORGE_CONTROLLER =
            BLOCKS.register("forge_controller",
                    () -> new ForgeControllerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /** Fuel port block; vertical stacks of these form per-fluid logical groups around the Forge. */
    public static final RegistryObject<ForgeFuelPortBlock> FORGE_FUEL_PORT =
            BLOCKS.register("forge_fuel_port",
                    () -> new ForgeFuelPortBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.COPPER)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** Fluid-output port block; drains molten contents of the connected Forge. */
    public static final RegistryObject<ForgeDrainBlock> FORGE_DRAIN =
            BLOCKS.register("forge_drain",
                    () -> new ForgeDrainBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(5.0f, 1200.0f)
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
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /**
     * Casting Table block; sand-casting workbench whose state machine lives on its block entity.
     */
    public static final RegistryObject<CastingTableBlock> CASTING_TABLE =
            BLOCKS.register("casting_table",
                    () -> new CastingTableBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0f, 4.0f)
                            .sound(SoundType.WOOD)));

    /**
     * Fluid pipe block; carries one fluid at a time with per-face IN/OUT/DISCONNECTED mode
     * encoded into six face-visual EnumProperties so a multipart blockstate can render each
     * face independently.
     */
    public static final RegistryObject<FluidPipeBlock> FLUID_PIPE =
            BLOCKS.register("fluid_pipe",
                    () -> new FluidPipeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    /**
     * Part Press block; in-world part cutting machine with a redstone-driven open/closed
     * pose. Internal state lives on {@link com.soul.smithery.block.entity.PartPressBlockEntity}.
     */
    public static final RegistryObject<PartPressBlock> PART_PRESS =
            BLOCKS.register("part_press",
                    () -> new PartPressBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 6.0f)
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
            String name = "casting_sand_impressed_" + pt.id().getPath();
            RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new Block(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(0.5f)
                            .sound(SoundType.SAND)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()));
            IMPRESSED_SAND_ITEMS.put(pt.id(), registerBlockItem(name, block));
        }
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
