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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

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
    public static final RegistryObject<BlockEntityType<ForgeControllerBlockEntity>>
            FORGE_CONTROLLER = BLOCK_ENTITIES.register("forge_controller",
                    () -> BlockEntityType.Builder.of(ForgeControllerBlockEntity::new, SmitheryBlocks.FORGE_CONTROLLER.get()).build(null));

    /** Block entity type for the Forge fuel port; holds molten fuel and forms vertical stack groups. */
    public static final RegistryObject<BlockEntityType<ForgeFuelPortBlockEntity>>
            FORGE_FUEL_PORT = BLOCK_ENTITIES.register("forge_fuel_port",
                    () -> BlockEntityType.Builder.of(ForgeFuelPortBlockEntity::new, SmitheryBlocks.FORGE_FUEL_PORT.get()).build(null));

    /** Block entity type for the Forge drain; pumps molten output toward fluid sinks. */
    public static final RegistryObject<BlockEntityType<ForgeDrainBlockEntity>>
            FORGE_DRAIN = BLOCK_ENTITIES.register("forge_drain",
                    () -> BlockEntityType.Builder.of(ForgeDrainBlockEntity::new, SmitheryBlocks.FORGE_DRAIN.get()).build(null));

    /** Block entity type for the Forge item-input port; forwards inserted items into the connected forge. */
    public static final RegistryObject<BlockEntityType<ForgeItemPortBlockEntity>>
            FORGE_ITEM_PORT = BLOCK_ENTITIES.register("forge_item_port",
                    () -> BlockEntityType.Builder.of(ForgeItemPortBlockEntity::new, SmitheryBlocks.FORGE_ITEM_PORT.get()).build(null));

    /** Block entity type for the fluid pipe; carries one fluid with per-face mode and visual state. */
    public static final RegistryObject<BlockEntityType<FluidPipeBlockEntity>>
            FLUID_PIPE = BLOCK_ENTITIES.register("fluid_pipe",
                    () -> BlockEntityType.Builder.of(FluidPipeBlockEntity::new, SmitheryBlocks.FLUID_PIPE.get()).build(null));

    /** Block entity type for the Casting Table; drives the sand-cast state machine. */
    public static final RegistryObject<BlockEntityType<CastingTableBlockEntity>>
            CASTING_TABLE = BLOCK_ENTITIES.register("casting_table",
                    () -> BlockEntityType.Builder.of(CastingTableBlockEntity::new, SmitheryBlocks.CASTING_TABLE.get()).build(null));

    /** Block entity type for the Part Press; cuts non-meltable inputs into parts. */
    public static final RegistryObject<BlockEntityType<PartPressBlockEntity>>
            PART_PRESS = BLOCK_ENTITIES.register("part_press",
                    () -> BlockEntityType.Builder.of(PartPressBlockEntity::new, SmitheryBlocks.PART_PRESS.get()).build(null));


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
