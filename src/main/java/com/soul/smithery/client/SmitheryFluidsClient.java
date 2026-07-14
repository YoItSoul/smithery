package com.soul.smithery.client;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RegisterFluidModelsEvent;

/**
 * Client-side fluid model + tint registration for every Smithery fluid.
 *
 * <p>Each material picks one of two animated base sprites — the lava-style smithery
 * {@code molten_still}/{@code molten_flow} (default) or vanilla water's rippling stills
 * (for water-base materials such as blood) — and is tinted per-material from
 * {@code MaterialStats#moltenColor()}. Invoked from the client mod entrypoint when
 * NeoForge fires the fluid-model registration event.
 */
public final class SmitheryFluidsClient {

    private static final ResourceLocation MOLTEN_STILL =
            new ResourceLocation(Smithery.MODID, "block/molten_still");
    private static final ResourceLocation MOLTEN_FLOW =
            new ResourceLocation(Smithery.MODID, "block/molten_flow");
    private static final ResourceLocation WATER_STILL =
            new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation WATER_FLOW =
            new ResourceLocation("minecraft", "block/water_flow");

    /**
     * Registers a fluid model for every Smithery fluid entry on the supplied event.
     *
     * <p>Selects the still/flow sprite pair from the material's {@link com.soul.smithery.api.material.MaterialStats.FluidBase}
     * and wires a fixed-colour tint derived from the material's molten colour.
     *
     * @param event NeoForge fluid-model registration event
     */
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        Material moltenStill  = new Material(MOLTEN_STILL);
        Material moltenFlow   = new Material(MOLTEN_FLOW);
        Material waterStill   = new Material(WATER_STILL);
        Material waterFlow    = new Material(WATER_FLOW);

        for (SmitheryFluids.Entry entry : SmitheryFluids.entries().values()) {
            int color = entry.material.stats().moltenColor() | 0xFF000000;
            BlockTintSource tint = new FixedTint(color);
            boolean water = entry.material.stats().fluidBase() == MaterialStats.FluidBase.WATER;
            Material still   = water ? waterStill  : moltenStill;
            Material flowing = water ? waterFlow   : moltenFlow;
            FluidModel.Unbaked unbaked = new FluidModel.Unbaked(still, flowing, null, tint);
            event.register(unbaked, entry.source.get(), entry.flowing.get());
        }
    }

    private record FixedTint(int color) implements BlockTintSource {
        @Override public int color(BlockState state) { return color; }
    }

    private SmitheryFluidsClient() {}
}
