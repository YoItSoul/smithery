package com.soul.smithery.registry;

import com.mojang.serialization.Codec;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.item.tool.ToolComposition;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Mod-namespaced data component types used to persist Smithery tool state on item stacks.
 *
 * <p>Components written here are persisted with the stack across save/load and synchronized
 * over the network so the client renders the right tooltip and textures.
 */
public final class SmitheryDataComponents {
    /** Deferred register for Smithery-namespaced data component types. */
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Smithery.MODID);

    /**
     * Composition of a Smithery tool stack: the ordered material per slot plus the resolved
     * {@link ToolComposition} aggregate stats. Drives tool tooltip, rendering and behaviour.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolComposition>>
            TOOL_COMPOSITION = COMPONENTS.register("tool_composition",
                    () -> DataComponentType.<ToolComposition>builder()
                            .persistent(ToolComposition.CODEC)
                            .networkSynchronized(ToolComposition.STREAM_CODEC)
                            .build());

    private static final Codec<List<ModifierEffect>> APPLIED_MODIFIERS_CODEC =
            ModifierEffect.CODEC.listOf();
    private static final StreamCodec<ByteBuf, List<ModifierEffect>> APPLIED_MODIFIERS_STREAM_CODEC =
            ModifierEffect.STREAM_CODEC.apply(ByteBufCodecs.list());

    /**
     * Ordered list of post-craft {@link ModifierEffect}s currently applied to the tool.
     * Separate from at-craft modifiers (which derive from {@link #TOOL_COMPOSITION}'s material
     * grants) so the "free slot" math stays simple.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ModifierEffect>>>
            APPLIED_MODIFIERS = COMPONENTS.register("applied_modifiers",
                    () -> DataComponentType.<List<ModifierEffect>>builder()
                            .persistent(APPLIED_MODIFIERS_CODEC)
                            .networkSynchronized(APPLIED_MODIFIERS_STREAM_CODEC)
                            .build());

    private static final java.util.Map<net.minecraft.resources.Identifier, Integer> EMPTY_PROGRESS = java.util.Map.of();
    private static final Codec<java.util.Map<net.minecraft.resources.Identifier, Integer>> MODIFIER_PROGRESS_CODEC =
            Codec.unboundedMap(net.minecraft.resources.Identifier.CODEC, Codec.INT);
    private static final StreamCodec<ByteBuf, java.util.Map<net.minecraft.resources.Identifier, Integer>>
            MODIFIER_PROGRESS_STREAM_CODEC = ByteBufCodecs.map(
                    java.util.HashMap::new,
                    net.minecraft.resources.Identifier.STREAM_CODEC,
                    ByteBufCodecs.VAR_INT);

    /**
     * Partial-application progress toward the next level of an in-progress modifier.
     * Keys are source-item ids; values are the raw item count contributed so far toward
     * the next level threshold.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.Map<net.minecraft.resources.Identifier, Integer>>>
            MODIFIER_PROGRESS = COMPONENTS.register("modifier_progress",
                    () -> DataComponentType.<java.util.Map<net.minecraft.resources.Identifier, Integer>>builder()
                            .persistent(MODIFIER_PROGRESS_CODEC)
                            .networkSynchronized(MODIFIER_PROGRESS_STREAM_CODEC)
                            .build());

    /**
     * Binds the deferred register to the mod event bus.
     *
     * @param modEventBus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }

    private SmitheryDataComponents() {}
}
