package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mod-namespaced data component types. Persisted with their stack across save/load and
 * synchronized over the network so the client renders the right tooltip & textures.
 */
public final class SmitheryDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Smithery.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolComposition>>
            TOOL_COMPOSITION = COMPONENTS.register("tool_composition",
                    () -> DataComponentType.<ToolComposition>builder()
                            .persistent(ToolComposition.CODEC)
                            .networkSynchronized(ToolComposition.STREAM_CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }

    private SmitheryDataComponents() {}
}
