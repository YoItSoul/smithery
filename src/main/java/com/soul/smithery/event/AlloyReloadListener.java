package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.alloy.AlloyRecipe;
import com.soul.smithery.api.alloy.AlloyRecipes;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AddReloadListenerEvent;

import java.util.Map;

/**
 * Server-side reload listener that loads alloy recipes from
 * {@code data/<namespace>/smithery/alloy/*.json}.
 *
 * <p>Each file is one recipe whose resource id becomes the registered alloy id. Re-runs on every
 * {@code /reload}; the data layer of {@link AlloyRecipes} is cleared first so removed JSON files
 * do not linger. Code-registered alloys are untouched.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class AlloyReloadListener extends SimpleJsonResourceReloadListener<AlloyRecipe> {

    private AlloyReloadListener() {
        super(AlloyRecipe.CODEC, FileToIdConverter.json("smithery/alloy"));
    }

    @Override
    protected void apply(Map<ResourceLocation, AlloyRecipe> entries,
                          ResourceManager manager, ProfilerFiller profiler) {
        AlloyRecipes.clearDataEntries();
        int registered = 0;
        for (Map.Entry<ResourceLocation, AlloyRecipe> e : entries.entrySet()) {
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
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(
                new ResourceLocation(Smithery.MODID, "alloys"),
                new AlloyReloadListener());
    }
}
