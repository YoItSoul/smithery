package com.soul.smithery.client;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;

/**
 * Client-side wiring for the Smithery molten fluids. Registers one FluidModel
 * per material entry, all sharing the greyscale {@code molten_still}/{@code molten_flow}
 * textures, with per-material tint coming from {@code MaterialStats#moltenColor()}.
 *
 * Called from SmitheryClient.onRegisterFluidModels (RegisterFluidModelsEvent
 * fires during model loading on a worker thread).
 */
public final class SmitheryFluidsClient {

    private static final Identifier MOLTEN_STILL =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "block/molten_still");
    private static final Identifier MOLTEN_FLOW =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "block/molten_flow");

    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        // Material in MC 26.1.2 takes a sprite identifier directly; the atlas is implicit.
        Material still   = new Material(MOLTEN_STILL);
        Material flowing = new Material(MOLTEN_FLOW);

        for (SmitheryFluids.Entry entry : SmitheryFluids.entries().values()) {
            int color = entry.material.stats().moltenColor() | 0xFF000000;
            BlockTintSource tint = new FixedTint(color);
            FluidModel.Unbaked unbaked = new FluidModel.Unbaked(still, flowing, null, tint);
            event.register(unbaked, entry.source.get(), entry.flowing.get());
        }
    }

    /** Tint source that returns a fixed ARGB color regardless of position/state. */
    private record FixedTint(int color) implements BlockTintSource {
        @Override public int color(BlockState state) { return color; }
    }

    private SmitheryFluidsClient() {}
}
