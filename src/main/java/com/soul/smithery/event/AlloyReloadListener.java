package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.alloy.AlloyRecipe;
import com.soul.smithery.api.alloy.AlloyRecipes;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.Map;

/**
 * Loads alloy recipes from {@code data/<namespace>/smithery/alloy/*.json}.
 * Each file is one recipe; the file's resource id becomes the recipe's id (so
 * {@code data/smithery/smithery/alloy/netherite.json} registers as {@code smithery:netherite}).
 *
 * Reruns on every {@code /reload}; clears the data-side registry first so removed JSON files
 * don't linger. Code-registered alloys (via {@link AlloyRecipes#register}) are not touched.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class AlloyReloadListener extends SimpleJsonResourceReloadListener<AlloyRecipe> {

    private AlloyReloadListener() {
        super(AlloyRecipe.CODEC, FileToIdConverter.json("smithery/alloy"));
    }

    @Override
    protected void apply(Map<Identifier, AlloyRecipe> entries,
                          ResourceManager manager, ProfilerFiller profiler) {
        AlloyRecipes.clearDataEntries();
        int registered = 0;
        for (Map.Entry<Identifier, AlloyRecipe> e : entries.entrySet()) {
            AlloyRecipes.registerDataEntry(e.getKey(), e.getValue());
            registered++;
        }
        Smithery.LOGGER.info("Loaded {} alloy recipes from data packs", registered);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "alloys"),
                new AlloyReloadListener());
    }
}
