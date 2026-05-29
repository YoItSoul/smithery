package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.block.entity.ForgeDrainBlockEntity;
import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import com.soul.smithery.block.entity.ForgeItemPortBlockEntity;
import com.soul.smithery.block.entity.PartPressBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of every {@link BlockEntityType} owned by Smithery.
 *
 * <p>Each entry binds one Smithery block (declared in {@link SmitheryBlocks}) to its
 * matching {@link net.minecraft.world.level.block.entity.BlockEntity} class. Bound here
 * rather than inside the block class so the DeferredRegister queue is in one place.
 */
public final class SmitheryBlockEntities {
    /** Deferred register for Smithery block entity types; bound to the mod event bus in {@link #register(IEventBus)}. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Smithery.MODID);

    /** Block entity type for the Forge multiblock controller. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeControllerBlockEntity>>
            FORGE_CONTROLLER = BLOCK_ENTITIES.register("forge_controller",
                    () -> new BlockEntityType<>(ForgeControllerBlockEntity::new,
                            SmitheryBlocks.FORGE_CONTROLLER.get()));

    /** Block entity type for the Forge fuel port; holds molten fuel and forms vertical stack groups. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeFuelPortBlockEntity>>
            FORGE_FUEL_PORT = BLOCK_ENTITIES.register("forge_fuel_port",
                    () -> new BlockEntityType<>(ForgeFuelPortBlockEntity::new,
                            SmitheryBlocks.FORGE_FUEL_PORT.get()));

    /** Block entity type for the Forge drain; pumps molten output toward fluid sinks. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeDrainBlockEntity>>
            FORGE_DRAIN = BLOCK_ENTITIES.register("forge_drain",
                    () -> new BlockEntityType<>(ForgeDrainBlockEntity::new,
                            SmitheryBlocks.FORGE_DRAIN.get()));

    /** Block entity type for the Forge item-input port; forwards inserted items into the connected forge. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeItemPortBlockEntity>>
            FORGE_ITEM_PORT = BLOCK_ENTITIES.register("forge_item_port",
                    () -> new BlockEntityType<>(ForgeItemPortBlockEntity::new,
                            SmitheryBlocks.FORGE_ITEM_PORT.get()));

    /** Block entity type for the fluid pipe; carries one fluid with per-face mode and visual state. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>>
            FLUID_PIPE = BLOCK_ENTITIES.register("fluid_pipe",
                    () -> new BlockEntityType<>(FluidPipeBlockEntity::new,
                            SmitheryBlocks.FLUID_PIPE.get()));

    /** Block entity type for the Casting Table; drives the sand-cast state machine. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingTableBlockEntity>>
            CASTING_TABLE = BLOCK_ENTITIES.register("casting_table",
                    () -> new BlockEntityType<>(CastingTableBlockEntity::new,
                            SmitheryBlocks.CASTING_TABLE.get()));

    /** Block entity type for the Part Press; cuts non-meltable inputs into parts. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartPressBlockEntity>>
            PART_PRESS = BLOCK_ENTITIES.register("part_press",
                    () -> new BlockEntityType<>(PartPressBlockEntity::new,
                            SmitheryBlocks.PART_PRESS.get()));


    /**
     * Binds the deferred register to the mod event bus.
     *
     * @param modEventBus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    private SmitheryBlockEntities() {}
}
