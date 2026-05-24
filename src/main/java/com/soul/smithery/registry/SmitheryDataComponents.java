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

    // List<ModifierEffect> post-craft applied to the tool. Separate from at-craft modifiers
    // (which derive from TOOL_COMPOSITION's material grants) so the "free slot" math works:
    //   freeSlots = totalSlots(comp) − APPLIED_MODIFIERS.size()
    // Anvil application appends to this list; remelting clears it (the tool is destroyed).
    private static final Codec<List<ModifierEffect>> APPLIED_MODIFIERS_CODEC =
            ModifierEffect.CODEC.listOf();
    private static final StreamCodec<ByteBuf, List<ModifierEffect>> APPLIED_MODIFIERS_STREAM_CODEC =
            ModifierEffect.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ModifierEffect>>>
            APPLIED_MODIFIERS = COMPONENTS.register("applied_modifiers",
                    () -> DataComponentType.<List<ModifierEffect>>builder()
                            .persistent(APPLIED_MODIFIERS_CODEC)
                            .networkSynchronized(APPLIED_MODIFIERS_STREAM_CODEC)
                            .build());

    // Partial-application progress toward the NEXT level of an in-progress modifier. Anvil
    // applications that don't have enough material to complete a level (e.g. you dropped 64
    // lapis when 90 are needed for Lapis Blessing I) store the remainder here as a RAW ITEM
    // COUNT of the source items contributed so far. When the count reaches the source's
    // materialCost-for-this-level, the level locks in and the progress entry is cleared.
    //
    // Cross-source mixing caveat: progress is in items, threshold is per-source. If you start
    // with 45 lapis dust (cost 90) then switch to lapis blocks (cost 10), the stored "45"
    // immediately exceeds the block threshold and triggers a level-up. Recommendation: stick
    // to one source per level — the math is intuitive when you do.
    private static final java.util.Map<net.minecraft.resources.Identifier, Integer> EMPTY_PROGRESS = java.util.Map.of();
    private static final Codec<java.util.Map<net.minecraft.resources.Identifier, Integer>> MODIFIER_PROGRESS_CODEC =
            Codec.unboundedMap(net.minecraft.resources.Identifier.CODEC, Codec.INT);
    private static final StreamCodec<ByteBuf, java.util.Map<net.minecraft.resources.Identifier, Integer>>
            MODIFIER_PROGRESS_STREAM_CODEC = ByteBufCodecs.map(
                    java.util.HashMap::new,
                    net.minecraft.resources.Identifier.STREAM_CODEC,
                    ByteBufCodecs.VAR_INT);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.Map<net.minecraft.resources.Identifier, Integer>>>
            MODIFIER_PROGRESS = COMPONENTS.register("modifier_progress",
                    () -> DataComponentType.<java.util.Map<net.minecraft.resources.Identifier, Integer>>builder()
                            .persistent(MODIFIER_PROGRESS_CODEC)
                            .networkSynchronized(MODIFIER_PROGRESS_STREAM_CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }

    private SmitheryDataComponents() {}
}
