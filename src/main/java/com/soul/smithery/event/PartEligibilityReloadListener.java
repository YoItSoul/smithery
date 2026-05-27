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
 * Loads {@code data/<namespace>/smithery/part_eligibility/*.json} files into
 * {@link PartEligibility}'s data-side allow-lists. Re-runs on every {@code /reload}, wiping
 * the prior data layer wholesale. Code-registered allow-lists are untouched and stack with
 * data entries at lookup time.
 *
 * <p>Filename is arbitrary — the {@code part_type} field inside the file is authoritative.
 *
 * <h3>Failure modes</h3>
 * Malformed JSON → logged + file skipped. Unknown part-type / material ids are accepted at
 * load time (the registry may not be fully populated yet); they simply never match anything
 * at lookup time.
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

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "part_eligibility"),
                new PartEligibilityReloadListener());
    }
}
