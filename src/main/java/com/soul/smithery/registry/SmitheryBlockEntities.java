package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.block.entity.ForgeDrainBlockEntity;
import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SmitheryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Smithery.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeControllerBlockEntity>>
            FORGE_CONTROLLER = BLOCK_ENTITIES.register("forge_controller",
                    () -> new BlockEntityType<>(ForgeControllerBlockEntity::new,
                            SmitheryBlocks.FORGE_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeFuelPortBlockEntity>>
            FORGE_FUEL_PORT = BLOCK_ENTITIES.register("forge_fuel_port",
                    () -> new BlockEntityType<>(ForgeFuelPortBlockEntity::new,
                            SmitheryBlocks.FORGE_FUEL_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeDrainBlockEntity>>
            FORGE_DRAIN = BLOCK_ENTITIES.register("forge_drain",
                    () -> new BlockEntityType<>(ForgeDrainBlockEntity::new,
                            SmitheryBlocks.FORGE_DRAIN.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>>
            FLUID_PIPE = BLOCK_ENTITIES.register("fluid_pipe",
                    () -> new BlockEntityType<>(FluidPipeBlockEntity::new,
                            SmitheryBlocks.FLUID_PIPE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingTableBlockEntity>>
            CASTING_TABLE = BLOCK_ENTITIES.register("casting_table",
                    () -> new BlockEntityType<>(CastingTableBlockEntity::new,
                            SmitheryBlocks.CASTING_TABLE.get()));


    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    private SmitheryBlockEntities() {}
}
