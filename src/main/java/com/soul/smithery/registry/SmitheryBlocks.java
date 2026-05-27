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
 * Block registrations for the Smithery Forge multiblock. The structural shell uses vanilla
 * deepslate variants (deepslate_bricks, cracked_deepslate_bricks, polished_deepslate); only
 * the "active" parts of the forge live here.
 *
 * Filler textures point at vanilla block textures (iron/copper/gold/lapis) via blockstate JSON
 * — to be replaced with bespoke art later.
 */
public final class SmitheryBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Smithery.MODID);

    /** Plain structural shell block for the Forge multiblock. The only non-port wall material. */
    public static final DeferredBlock<Block> FURNACE_BRICKS =
            BLOCKS.registerSimpleBlock("furnace_bricks", p -> p
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK));

    public static final DeferredBlock<ForgeControllerBlock> FORGE_CONTROLLER =
            BLOCKS.registerBlock("forge_controller",
                    ForgeControllerBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops());

    public static final DeferredBlock<ForgeFuelPortBlock> FORGE_FUEL_PORT =
            BLOCKS.registerBlock("forge_fuel_port",
                    ForgeFuelPortBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.COPPER)
                            .requiresCorrectToolForDrops()
                            // Cutout texture has transparent center pixels — without noOcclusion,
                            // adjacent block faces get hidden and we lose the "looking through
                            // the window into the tank" effect entirely.
                            .noOcclusion());

    public static final DeferredBlock<ForgeDrainBlock> FORGE_DRAIN =
            BLOCKS.registerBlock("forge_drain",
                    ForgeDrainBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops());

    /**
     * Item input port — accepts items via right-click or hopper insertion and forwards them
     * into the connected forge's nearest empty interior slot. Rejects when the forge is full.
     * Counts as part of the shell for structure validation.
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
     * Casting Sand — gravity-falling block, identical behavior to vanilla sand.
     * Used to prep a Casting Table for sand-casting. ColorRGBA controls the
     * falling-dust particle color; matches the darkened sand texture so the
     * particles don't visually clash when the block falls.
     */
    /**
     * Casting Table — sand-casting workbench. BlockEntity holds the cast state machine.
     * Light strength (axe-mineable wood-stat); not requiring a specific tool for drops.
     */
    public static final DeferredBlock<CastingTableBlock> CASTING_TABLE =
            BLOCKS.registerBlock("casting_table",
                    CastingTableBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0f, 4.0f)
                            .sound(SoundType.WOOD));

    /**
     * Fluid pipe — one-way transport block. Per-face mode + single-fluid storage live on the BE;
     * the BlockState carries six EnumProperty&lt;FluidPipeFaceVisual&gt; values (4 visuals × 6 faces =
     * 4096 combinations) so a multipart blockstate can render each face independently.
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
     * Part Press — in-world part cutting block. Redstone-driven open/closed pose; uses
     * Geckolib for the head animation. Internal state on {@link com.soul.smithery.block.entity.PartPressBlockEntity}.
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

    public static final DeferredBlock<ColoredFallingBlock> CASTING_SAND =
            BLOCKS.registerBlock("casting_sand",
                    // Particle color tuned to the darkened texture's average (~dark charcoal gray)
                    // so the falling-dust effect doesn't puff out as pale yellow sand.
                    props -> new ColoredFallingBlock(new ColorRGBA(0xFF3A3A3A), props),
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(0.5f)
                            .sound(SoundType.SAND)
                            .pushReaction(PushReaction.NORMAL));

    /**
     * Red slime block — vanilla SlimeBlock properties (sticky/bouncy + low friction +
     * translucent rendering via noOcclusion) plus a constant redstone signal source, tinted
     * red. Behaves exactly like a slime block in every interaction; just powers everything
     * adjacent like a redstone block.
     *
     * <p>Properties mirror vanilla's {@code SLIME_BLOCK} registration in
     * {@code Blocks.java}, swapping the MapColor for COLOR_RED so the block reads red on a
     * filled map. Texture + model + blockstate are runtime-synthesized in
     * {@link com.soul.smithery.registry.SmitheryGeneratedPack} so no static asset files
     * ship for this block.
     */
    public static final DeferredBlock<com.soul.smithery.block.RedSlimeBlock> RED_SLIME_BLOCK =
            BLOCKS.registerBlock("red_slime_block",
                    com.soul.smithery.block.RedSlimeBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .friction(0.8f)
                            .sound(SoundType.SLIME_BLOCK)
                            .noOcclusion());

    // Corresponding BlockItems live in the Smithery item register so they appear with parts/tools.
    public static final DeferredItem<BlockItem> FURNACE_BRICKS_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("furnace_bricks", FURNACE_BRICKS);
    public static final DeferredItem<BlockItem> FORGE_CONTROLLER_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_controller", FORGE_CONTROLLER);
    public static final DeferredItem<BlockItem> FORGE_FUEL_PORT_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_fuel_port", FORGE_FUEL_PORT);
    public static final DeferredItem<BlockItem> FORGE_DRAIN_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_drain", FORGE_DRAIN);
    public static final DeferredItem<BlockItem> FORGE_ITEM_PORT_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_item_port", FORGE_ITEM_PORT);
    public static final DeferredItem<BlockItem> CASTING_TABLE_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("casting_table", CASTING_TABLE);
    public static final DeferredItem<BlockItem> CASTING_SAND_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("casting_sand", CASTING_SAND);
    public static final DeferredItem<BlockItem> FLUID_PIPE_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("fluid_pipe", FLUID_PIPE);
    public static final DeferredItem<BlockItem> PART_PRESS_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("part_press", PART_PRESS);
    public static final DeferredItem<BlockItem> RED_SLIME_BLOCK_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("red_slime_block", RED_SLIME_BLOCK);

    /**
     * Internal "sand with cutout for PartType" block variants. One per registered PartType,
     * used purely by the CastingTableRenderer to render the impressed sand visual via the
     * standard item-stack-render-state pipeline.
     *
     * These blocks ARE registered in the registry (so /give can produce them and the BlockItem
     * has a valid path for the dynamic models to resolve against), but they're never put in a
     * creative tab and have no loot table so they behave inertly even if placed.
     */
    private static final Map<Identifier, DeferredItem<BlockItem>> IMPRESSED_SAND_ITEMS = new LinkedHashMap<>();

    /**
     * Register one internal "casting_sand_impressed_<part>" block per registered PartType.
     * Must be called AFTER SmitheryPartTypes.register() (so SmitheryAPI.PART_TYPES.all() is
     * populated) and BEFORE register(modEventBus) so the DeferredRegister queue is complete
     * by the time the bus event fires.
     */
    public static void registerImpressedSandVariants() {
        if (!IMPRESSED_SAND_ITEMS.isEmpty()) return; // idempotent guard
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

    /** BlockItem of the "sand with PartType-shaped hole" block; null for unregistered part ids. */
    public static DeferredItem<BlockItem> getImpressedSandItem(Identifier partTypeId) {
        return IMPRESSED_SAND_ITEMS.get(partTypeId);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private SmitheryBlocks() {}
}
