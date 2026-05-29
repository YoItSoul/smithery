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
 * Server-side reload listener that loads alloy recipes from
 * {@code data/<namespace>/smithery/alloy/*.json}.
 *
 * <p>Each file is one recipe whose resource id becomes the registered alloy id. Re-runs on every
 * {@code /reload}; the data layer of {@link AlloyRecipes} is cleared first so removed JSON files
 * do not linger. Code-registered alloys are untouched.
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

    /**
     * Registers this listener with the server reload pipeline under the {@code smithery:alloys}
     * id.
     *
     * @param event the NeoForge add-reload-listeners event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "alloys"),
                new AlloyReloadListener());
    }
}
