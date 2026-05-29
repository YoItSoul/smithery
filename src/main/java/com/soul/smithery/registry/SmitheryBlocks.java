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
import net.minecraft.resources.Identifier;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Smithery.MODID);

    /** Plain structural shell block for the Forge multiblock; the only non-port wall material. */
    public static final DeferredBlock<Block> FURNACE_BRICKS =
            BLOCKS.registerSimpleBlock("furnace_bricks", p -> p
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK));

    /** Forge controller block; the multiblock's master block and only GUI entry point. */
    public static final DeferredBlock<ForgeControllerBlock> FORGE_CONTROLLER =
            BLOCKS.registerBlock("forge_controller",
                    ForgeControllerBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops());

    /** Fuel port block; vertical stacks of these form per-fluid logical groups around the Forge. */
    public static final DeferredBlock<ForgeFuelPortBlock> FORGE_FUEL_PORT =
            BLOCKS.registerBlock("forge_fuel_port",
                    ForgeFuelPortBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.COPPER)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** Fluid-output port block; drains molten contents of the connected Forge. */
    public static final DeferredBlock<ForgeDrainBlock> FORGE_DRAIN =
            BLOCKS.registerBlock("forge_drain",
                    ForgeDrainBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops());

    /**
     * Item input port for the Forge multiblock. Accepts items via right-click or hopper
     * insertion and forwards them into the connected forge's nearest empty interior slot;
     * rejects when the forge is full. Counts as part of the shell for structure validation.
     */
    public static final DeferredBlock<ForgeItemPortBlock> FORGE_ITEM_PORT =
            BLOCKS.registerBlock("forge_item_port",
                    ForgeItemPortBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops());

    /**
     * Casting Table block; sand-casting workbench whose state machine lives on its block entity.
     */
    public static final DeferredBlock<CastingTableBlock> CASTING_TABLE =
            BLOCKS.registerBlock("casting_table",
                    CastingTableBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0f, 4.0f)
                            .sound(SoundType.WOOD));

    /**
     * Fluid pipe block; carries one fluid at a time with per-face IN/OUT/DISCONNECTED mode
     * encoded into six face-visual EnumProperties so a multipart blockstate can render each
     * face independently.
     */
    public static final DeferredBlock<FluidPipeBlock> FLUID_PIPE =
            BLOCKS.registerBlock("fluid_pipe",
                    FluidPipeBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion());

    /**
     * Part Press block; in-world part cutting machine with a redstone-driven open/closed
     * pose. Internal state lives on {@link com.soul.smithery.block.entity.PartPressBlockEntity}.
     */
    public static final DeferredBlock<PartPressBlock> PART_PRESS =
            BLOCKS.registerBlock("part_press",
                    PartPressBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops());

    /** Casting Sand block; vanilla-sand-equivalent falling block used to prep a Casting Table. */
    public static final DeferredBlock<ColoredFallingBlock> CASTING_SAND =
            BLOCKS.registerBlock("casting_sand",
                    props -> new ColoredFallingBlock(new ColorRGBA(0xFF3A3A3A), props),
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(0.5f)
                            .sound(SoundType.SAND)
                            .pushReaction(PushReaction.NORMAL));

    /**
     * Red slime block; sticky/bouncy slime block tinted red that also emits a constant
     * redstone signal. Texture, model and blockstate are runtime-synthesized in
     * {@link SmitheryGeneratedPack}.
     */
    public static final DeferredBlock<com.soul.smithery.block.RedSlimeBlock> RED_SLIME_BLOCK =
            BLOCKS.registerBlock("red_slime_block",
                    com.soul.smithery.block.RedSlimeBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .friction(0.8f)
                            .sound(SoundType.SLIME_BLOCK)
                            .noOcclusion());

    /** BlockItem for {@link #FURNACE_BRICKS}. */
    public static final DeferredItem<BlockItem> FURNACE_BRICKS_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("furnace_bricks", FURNACE_BRICKS);
    /** BlockItem for {@link #FORGE_CONTROLLER}. */
    public static final DeferredItem<BlockItem> FORGE_CONTROLLER_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_controller", FORGE_CONTROLLER);
    /** BlockItem for {@link #FORGE_FUEL_PORT}. */
    public static final DeferredItem<BlockItem> FORGE_FUEL_PORT_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_fuel_port", FORGE_FUEL_PORT);
    /** BlockItem for {@link #FORGE_DRAIN}. */
    public static final DeferredItem<BlockItem> FORGE_DRAIN_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_drain", FORGE_DRAIN);
    /** BlockItem for {@link #FORGE_ITEM_PORT}. */
    public static final DeferredItem<BlockItem> FORGE_ITEM_PORT_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_item_port", FORGE_ITEM_PORT);
    /** BlockItem for {@link #CASTING_TABLE}. */
    public static final DeferredItem<BlockItem> CASTING_TABLE_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("casting_table", CASTING_TABLE);
    /** BlockItem for {@link #CASTING_SAND}. */
    public static final DeferredItem<BlockItem> CASTING_SAND_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("casting_sand", CASTING_SAND);
    /** BlockItem for {@link #FLUID_PIPE}. */
    public static final DeferredItem<BlockItem> FLUID_PIPE_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("fluid_pipe", FLUID_PIPE);
    /** BlockItem for {@link #PART_PRESS}. */
    public static final DeferredItem<BlockItem> PART_PRESS_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("part_press", PART_PRESS);
    /** BlockItem for {@link #RED_SLIME_BLOCK}. */
    public static final DeferredItem<BlockItem> RED_SLIME_BLOCK_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("red_slime_block", RED_SLIME_BLOCK);

    private static final Map<Identifier, DeferredItem<BlockItem>> IMPRESSED_SAND_ITEMS = new LinkedHashMap<>();

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
            DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, p -> p
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.5f)
                    .sound(SoundType.SAND)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable());
            IMPRESSED_SAND_ITEMS.put(pt.id(),
                    SmitheryItems.ITEMS.registerSimpleBlockItem(name, block));
        }
    }

    /**
     * Looks up the impressed-sand BlockItem (the "sand with a PartType-shaped hole" variant)
     * for a given part type.
     *
     * @param partTypeId id of a registered {@link PartType}
     * @return the matching DeferredItem, or null if the part type was never registered
     */
    public static DeferredItem<BlockItem> getImpressedSandItem(Identifier partTypeId) {
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
