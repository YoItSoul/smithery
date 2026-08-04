package com.soul.smithery.event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.stage.SmitheryStages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Loads stage gates from {@code data/<namespace>/smithery/stages/*.json}.
 *
 * <p>Each file may declare any of three maps, and files merge, so a pack can split
 * its ladder across several files or override a single entry from an addon:</p>
 *
 * <pre>{@code
 * {
 *   "materials":  { "smithery:cobalt": "nether", "smithery:bedrock": "awakened" },
 *   "tool_types": { "smithery:rapier": "tactic_blueprint" },
 *   "modifiers":  { "smithery:mending_moss": "hardmode" }
 * }
 * }</pre>
 *
 * <p>Ids are resource locations; a bare {@code "cobalt"} resolves to the
 * {@code smithery} namespace, matching how the rest of the data layer reads ids.</p>
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class StageReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    private StageReloadListener() {
        super(GSON, "smithery/stages");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        SmitheryStages.clearData();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                Smithery.LOGGER.warn("smithery:stages file {} is not a JSON object", entry.getKey());
                continue;
            }
            JsonObject root = entry.getValue().getAsJsonObject();
            readMaterials(root, entry.getKey());
            read(root, entry.getKey(), "tool_types", SmitheryStages::putToolTypeStage);
            read(root, entry.getKey(), "modifiers", SmitheryStages::putModifierStage);
        }
        if (!SmitheryStages.isEmpty()) {
            Smithery.LOGGER.info("Loaded {} Smithery stage gates from data packs", SmitheryStages.gateCount());
        }
    }

    /**
     * Materials accept a bare name as well as a full id, because which mod registers a
     * given material is an implementation detail a pack author should not have to track.
     */
    private static void readMaterials(JsonObject root, ResourceLocation file) {
        if (!root.has("materials") || !root.get("materials").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("materials").entrySet()) {
            if (!e.getValue().isJsonPrimitive()) {
                Smithery.LOGGER.warn("smithery:stages file {} material '{}' has no stage string", file, e.getKey());
                continue;
            }
            String stage = e.getValue().getAsString();
            if (!e.getKey().contains(":")) {
                SmitheryStages.putMaterialStageByPath(e.getKey(), stage);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null) {
                Smithery.LOGGER.warn("smithery:stages file {} has unparseable material id '{}'", file, e.getKey());
                continue;
            }
            SmitheryStages.putMaterialStage(id, stage);
        }
    }

    private static void read(JsonObject root, ResourceLocation file, String key,
                             BiConsumer<ResourceLocation, String> sink) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(key).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(
                    e.getKey().contains(":") ? e.getKey() : Smithery.MODID + ":" + e.getKey());
            if (id == null) {
                Smithery.LOGGER.warn("smithery:stages file {} has unparseable {} id '{}'", file, key, e.getKey());
                continue;
            }
            if (!e.getValue().isJsonPrimitive()) {
                Smithery.LOGGER.warn("smithery:stages file {} entry '{}' has no stage string", file, e.getKey());
                continue;
            }
            sink.accept(id, e.getValue().getAsString());
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StageReloadListener());
    }
}
