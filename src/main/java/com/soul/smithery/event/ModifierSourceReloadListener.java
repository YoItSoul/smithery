package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierSources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.Map;

/**
 * Server-side reload listener that loads anvil modifier-source mappings from
 * {@code data/<namespace>/smithery/modifier_source/*.json} into {@link ModifierSources}'s
 * data layer.
 *
 * <p>Each file's {@code source_item} field is authoritative (not the filename), pointing at the
 * vanilla / modded item that, when placed in the anvil, applies the named modifier to the tool.
 * Re-runs on every {@code /reload} and wipes the prior data layer; code-registered entries are
 * untouched, and data entries override code entries on item collision.
 *
 * <p>Files with unresolvable source items are skipped with a warning; unknown modifier ids are
 * accepted at load time and surface at apply time as the anvil hook rejects them.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class ModifierSourceReloadListener
        extends SimpleJsonResourceReloadListener<ModifierSources.JsonEntry> {

    private ModifierSourceReloadListener() {
        super(ModifierSources.JsonEntry.CODEC,
              FileToIdConverter.json("smithery/modifier_source"));
    }

    @Override
    protected void apply(Map<Identifier, ModifierSources.JsonEntry> entries,
                          ResourceManager manager, ProfilerFiller profiler) {
        ModifierSources.clearDataEntries();
        int registered = 0;
        for (Map.Entry<Identifier, ModifierSources.JsonEntry> e : entries.entrySet()) {
            ModifierSources.JsonEntry parsed = e.getValue();
            Item item = BuiltInRegistries.ITEM.get(parsed.sourceItem())
                    .<Item>map(holder -> holder.value()).orElse(null);
            if (item == null) {
                Smithery.LOGGER.warn("smithery:modifier_source file {} → source_item {} not in registry; skipping",
                        e.getKey(), parsed.sourceItem());
                continue;
            }
            ModifierSources.Entry entry = new ModifierSources.Entry(
                    parsed.toEffect(), parsed.unitValue());
            ModifierSources.registerDataEntry(item, entry);
            registered++;
        }
        Smithery.LOGGER.info("Loaded {} modifier-source mappings from data packs", registered);
    }

    /**
     * Registers this listener with the server reload pipeline under the
     * {@code smithery:modifier_sources} id.
     *
     * @param event the NeoForge add-reload-listeners event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "modifier_sources"),
                new ModifierSourceReloadListener());
    }
}
