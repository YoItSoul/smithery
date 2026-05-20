package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.ForgeControllerBlock;
import com.soul.smithery.block.ForgeDrainBlock;
import com.soul.smithery.block.ForgeFuelPortBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
                            .requiresCorrectToolForDrops());

    public static final DeferredBlock<ForgeDrainBlock> FORGE_DRAIN =
            BLOCKS.registerBlock("forge_drain",
                    ForgeDrainBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops());

    // Corresponding BlockItems live in the Smithery item register so they appear with parts/tools.
    public static final DeferredItem<BlockItem> FURNACE_BRICKS_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("furnace_bricks", FURNACE_BRICKS);
    public static final DeferredItem<BlockItem> FORGE_CONTROLLER_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_controller", FORGE_CONTROLLER);
    public static final DeferredItem<BlockItem> FORGE_FUEL_PORT_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_fuel_port", FORGE_FUEL_PORT);
    public static final DeferredItem<BlockItem> FORGE_DRAIN_ITEM =
            SmitheryItems.ITEMS.registerSimpleBlockItem("forge_drain", FORGE_DRAIN);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private SmitheryBlocks() {}
}
