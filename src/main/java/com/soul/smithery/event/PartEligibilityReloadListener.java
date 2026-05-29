package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.part.PartEligibility;
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
@EventBusSubscriber(modid = Smithery.MODID)
public final class PartEligibilityReloadListener
        extends SimpleJsonResourceReloadListener<PartEligibility.JsonEntry> {

    private PartEligibilityReloadListener() {
        super(PartEligibility.JsonEntry.CODEC,
              FileToIdConverter.json("smithery/part_eligibility"));
    }

    @Override
    protected void apply(Map<Identifier, PartEligibility.JsonEntry> entries,
                          ResourceManager manager, ProfilerFiller profiler) {
        PartEligibility.clearDataEntries();
        int registered = 0;
        for (Map.Entry<Identifier, PartEligibility.JsonEntry> e : entries.entrySet()) {
            PartEligibility.JsonEntry parsed = e.getValue();
            PartEligibility.registerDataEntry(parsed.partType(), parsed.materialSet());
            registered += parsed.materials().size();
        }
        Smithery.LOGGER.info("Loaded {} part-eligibility entries from data packs", registered);
    }

    /**
     * Registers this listener with the server reload pipeline under the
     * {@code smithery:part_eligibility} id.
     *
     * @param event the NeoForge add-reload-listeners event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "part_eligibility"),
                new PartEligibilityReloadListener());
    }
}
