package com.soul.smithery.event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierSources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class ModifierSourceReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    private ModifierSourceReloadListener() {
        super(GSON, "smithery/modifier_source");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        ModifierSources.clearDataEntries();
        int registered = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            ModifierSources.JsonEntry parsed = ModifierSources.JsonEntry.CODEC
                    .parse(JsonOps.INSTANCE, e.getValue())
                    .resultOrPartial(err -> Smithery.LOGGER.warn(
                            "smithery:modifier_source file {} failed to parse: {}", e.getKey(), err))
                    .orElse(null);
            if (parsed == null) continue;
            Item item = BuiltInRegistries.ITEM.get(parsed.sourceItem());
            if (item == Items.AIR) {
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
     * Registers this listener with the server reload pipeline.
     *
     * @param event Forge's add-reload-listener event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ModifierSourceReloadListener());
    }
}
