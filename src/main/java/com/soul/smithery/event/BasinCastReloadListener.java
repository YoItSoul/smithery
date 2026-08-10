package com.soul.smithery.event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.cast.BasinCastEntry;
import com.soul.smithery.api.cast.CastBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side reload listener that loads Casting Basin recipes from
 * {@code data/<namespace>/smithery/basin_cast/*.json}.
 *
 * <p>Each file is one {@link BasinCastEntry}, keyed by the material it names rather than by its file
 * id — a basin has one shape, so a material has one recipe. Re-runs on every {@code /reload}; the
 * data layer of {@link CastBlocks} is cleared first so removed JSON files do not linger.
 * Code-registered casts are untouched except where a file overrides or removes one.
 *
 * <p>Files are applied in file-id order so two packs claiming the same material resolve the same way
 * every load rather than by hash order.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class BasinCastReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    private BasinCastReloadListener() {
        super(GSON, "smithery/basin_cast");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        CastBlocks.clearDataEntries();

        List<ResourceLocation> ids = new ArrayList<>(files.keySet());
        ids.sort(ResourceLocation::compareTo);

        Set<ResourceLocation> claimed = new HashSet<>();
        int registered = 0;
        int removed = 0;
        for (ResourceLocation id : ids) {
            BasinCastEntry entry = BasinCastEntry.CODEC
                    .parse(JsonOps.INSTANCE, files.get(id))
                    .resultOrPartial(err -> Smithery.LOGGER.warn(
                            "smithery:basin_cast file {} failed to parse: {}", id, err))
                    .orElse(null);
            if (entry == null) continue;
            if (!entry.isWellFormed()) {
                Smithery.LOGGER.warn(
                        "smithery:basin_cast file {} needs either \"remove\": true, or both a positive"
                                + " \"mb\" and a \"result\" item — skipping", id);
                continue;
            }
            if (!claimed.add(entry.material())) {
                Smithery.LOGGER.warn(
                        "smithery:basin_cast file {} re-declares material {}, which an earlier file"
                                + " already claimed — the later file wins", id, entry.material());
            }

            if (entry.remove()) {
                CastBlocks.removeDataEntry(entry.material());
                removed++;
                continue;
            }

            ResourceLocation resultId = entry.result().orElseThrow();
            Item result = ForgeRegistries.ITEMS.getValue(resultId);
            if (result == null || result == Items.AIR) {
                Smithery.LOGGER.warn(
                        "smithery:basin_cast file {} names unknown result item {} — skipping",
                        id, resultId);
                continue;
            }
            CastBlocks.registerDataEntry(entry.material(), entry.mb(), () -> result);
            registered++;
        }
        Smithery.LOGGER.info("Loaded {} basin casts ({} removals) from data packs", registered, removed);
    }

    /**
     * Registers this listener with the server reload pipeline.
     *
     * @param event Forge's add-reload-listener event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BasinCastReloadListener());
    }
}
