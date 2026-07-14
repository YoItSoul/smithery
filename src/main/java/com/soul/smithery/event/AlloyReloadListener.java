package com.soul.smithery.event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.alloy.AlloyRecipe;
import com.soul.smithery.api.alloy.AlloyRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
public final class AlloyReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    private AlloyReloadListener() {
        super(GSON, "smithery/alloy");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        AlloyRecipes.clearDataEntries();
        int registered = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            AlloyRecipe recipe = AlloyRecipe.CODEC
                    .parse(JsonOps.INSTANCE, e.getValue())
                    .resultOrPartial(err -> Smithery.LOGGER.warn(
                            "smithery:alloy file {} failed to parse: {}", e.getKey(), err))
                    .orElse(null);
            if (recipe == null) continue;
            AlloyRecipes.registerDataEntry(e.getKey(), recipe);
            registered++;
        }
        Smithery.LOGGER.info("Loaded {} alloy recipes from data packs", registered);
    }

    /**
     * Registers this listener with the server reload pipeline.
     *
     * @param event Forge's add-reload-listener event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AlloyReloadListener());
    }
}
