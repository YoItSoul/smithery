package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
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

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    private SmitheryBlockEntities() {}
}
