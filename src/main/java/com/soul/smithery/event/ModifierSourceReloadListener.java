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
 * Loads {@code data/<namespace>/smithery/modifier_source/*.json} files into
 * {@link ModifierSources}'s data-side registry. Re-runs on every {@code /reload}, replacing
 * the prior data-entry set wholesale; code-registered entries are untouched.
 *
 * <h3>JSON schema</h3>
 * One file per source mapping. See {@link ModifierSources.JsonEntry} for the field set.
 * Filename is arbitrary — the {@code source_item} field inside the file determines which
 * Item gets mapped, not the filename. Example file:
 *
 * <pre>{@code
 *   // data/smithery/smithery/modifier_source/ender_affinity.json
 *   {
 *     "source_item": "minecraft:ender_pearl",
 *     "modifier":    "smithery:ender_affinity",
 *     "params":      { "chance": 0.30, "radius": 4.0 }
 *   }
 * }</pre>
 *
 * <h3>How modders extend</h3>
 * Drop a JSON file at the same path under your own mod's namespace
 * ({@code data/<yourmod>/smithery/modifier_source/<anything>.json}) or as a player-installed
 * datapack at {@code <world>/datapacks/<pack>/data/<ns>/smithery/modifier_source/...}.
 * Multiple files coalesce into a single registry. Data entries override code entries on
 * Item collision, so a pack can shadow a built-in mapping by mapping the same source item
 * to a different modifier.
 *
 * <h3>Failure modes (skipped silently)</h3>
 * <ul>
 *   <li>Malformed JSON → logged, file skipped, others continue.</li>
 *   <li>{@code source_item} doesn't resolve to a registered Item → file skipped (typo, or
 *       referring to an item from a mod that isn't installed).</li>
 *   <li>{@code modifier} id doesn't match any registered Modifier → entry still registered;
 *       the anvil hook does the modifier-existence check at apply time so a typo is
 *       discoverable in-game when the player tries to use the source.</li>
 * </ul>
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

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "modifier_sources"),
                new ModifierSourceReloadListener());
    }
}
