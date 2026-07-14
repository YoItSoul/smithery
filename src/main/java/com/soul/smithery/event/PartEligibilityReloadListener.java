package com.soul.smithery.event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.part.PartEligibility;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Server-side reload listener that loads part-eligibility allow-lists from
 * {@code data/<namespace>/smithery/part_eligibility/*.json} into {@link PartEligibility}'s
 * data layer.
 *
 * <p>The {@code part_type} field inside each file is authoritative (not the filename). Re-runs
 * on every {@code /reload} and wipes the data layer wholesale; code-registered allow-lists are
 * untouched and stack additively with data entries at lookup time. Unknown part-type or material
 * ids are accepted silently because registries may not be fully populated at load time; they
 * simply never match at lookup.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class PartEligibilityReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    private PartEligibilityReloadListener() {
        super(GSON, "smithery/part_eligibility");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        PartEligibility.clearDataEntries();
        int registered = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            PartEligibility.JsonEntry parsed = PartEligibility.JsonEntry.CODEC
                    .parse(JsonOps.INSTANCE, e.getValue())
                    .resultOrPartial(err -> Smithery.LOGGER.warn(
                            "smithery:part_eligibility file {} failed to parse: {}", e.getKey(), err))
                    .orElse(null);
            if (parsed == null) continue;
            PartEligibility.registerDataEntry(parsed.partType(), parsed.materialSet());
            registered += parsed.materials().size();
        }
        Smithery.LOGGER.info("Loaded {} part-eligibility entries from data packs", registered);
    }

    /**
     * Registers this listener with the server reload pipeline.
     *
     * @param event Forge's add-reload-listener event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PartEligibilityReloadListener());
    }
}
