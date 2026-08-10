package com.soul.smithery.network;

import com.mojang.serialization.DataResult;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.alloy.AlloyRecipe;
import com.soul.smithery.api.alloy.AlloyRecipes;
import com.soul.smithery.api.cast.CastBlocks;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.api.part.PartEligibility;
import com.soul.smithery.api.stage.SmitheryStages;
import com.soul.smithery.event.ModifierReloadListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-to-client snapshot of every Smithery registry that a data pack can write to.
 *
 * <p>All of them are filled by {@code AddReloadListenerEvent} listeners, which only ever run on the
 * logical server. On a dedicated server that leaves the client's copies holding nothing but their
 * code layer — and since every built-in alloy ships as JSON, the alloying screen in JEI would be
 * empty, while pack-added basin casts, modifier sources, modifiers, part eligibility and stage
 * gates would all be invisible to tooltips and JEI. Vanilla solves the same problem for recipes and
 * tags with a login-time sync; this is the equivalent for ours.
 *
 * <p>Sent on {@code OnDatapackSyncEvent}: once per player on join, and to everyone on {@code /reload}.
 * The client applies it and asks JEI to refresh the categories that read it.
 *
 * <p>Nothing here changes what the server does — it owns gameplay either way. What it fixes is the
 * client's picture of it.
 *
 * @param alloys      data-layer alloy recipes, keyed by their file id
 * @param basinCasts  data-layer Casting Basin casts and suppressions
 * @param sources     data-layer anvil modifier sources, keyed by source item id
 * @param modifiers   data-defined modifiers as their parsed JSON, keyed by id
 * @param eligibility data-layer part-eligibility allow-lists
 * @param stages      every stage gate; the registry is data-driven end to end
 */
public record SmitheryDataSyncPayload(Map<ResourceLocation, AlloyRecipe> alloys,
                                      BasinCasts basinCasts,
                                      Map<ResourceLocation, List<SourceEntry>> sources,
                                      Map<ResourceLocation, ModifierReloadListener.ModifierJson> modifiers,
                                      Eligibility eligibility,
                                      SmitheryStages.Snapshot stages) {

    /**
     * Casting Basin data layer on the wire. Portions and results share a key set; a cast whose
     * result item is unknown to the receiver is dropped rather than half-applied.
     *
     * @param mb       cast portions, keyed by material id
     * @param results  cast result item ids, keyed by material id
     * @param removals materials whose cast the data layer suppressed
     */
    public record BasinCasts(Map<ResourceLocation, Integer> mb,
                             Map<ResourceLocation, ResourceLocation> results,
                             Set<ResourceLocation> removals) {}

    /**
     * One anvil modifier source on the wire.
     *
     * <p>Parameters travel as floats, the same shape {@link ModifierEffect#CODEC} persists them in;
     * the data layer is only ever filled from {@code ModifierSources.JsonEntry}, whose params are
     * floats to begin with, so nothing is lost in the crossing.
     *
     * @param modifier  id of the modifier this source applies
     * @param params    the effect's numeric parameters
     * @param unitValue units one item contributes toward the modifier's level cost
     */
    public record SourceEntry(ResourceLocation modifier, Map<String, Float> params, int unitValue) {}

    /**
     * Part-eligibility data layer on the wire.
     *
     * @param byPartType materials each part type allows
     * @param byMaterial part types each material is restricted to
     */
    public record Eligibility(Map<ResourceLocation, Set<ResourceLocation>> byPartType,
                              Map<ResourceLocation, Set<ResourceLocation>> byMaterial) {}

    /** Captures the server's current data layers. */
    public static SmitheryDataSyncPayload snapshot() {
        Map<ResourceLocation, Integer> mb = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> results = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CastBlocks.Cast> e : CastBlocks.dataEntries().entrySet()) {
            Item item = e.getValue().result().get();
            ResourceLocation itemId = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null) continue;
            mb.put(e.getKey(), e.getValue().mb());
            results.put(e.getKey(), itemId);
        }

        Map<ResourceLocation, List<SourceEntry>> sources = new LinkedHashMap<>();
        for (Map.Entry<Item, List<ModifierSources.Entry>> e : ModifierSources.dataEntries().entrySet()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(e.getKey());
            if (itemId == null) continue;
            List<SourceEntry> encoded = new ArrayList<>(e.getValue().size());
            for (ModifierSources.Entry entry : e.getValue()) {
                encoded.add(new SourceEntry(
                        entry.effect().modifierId(),
                        numericParams(entry.effect().params()),
                        entry.unitValue()));
            }
            if (!encoded.isEmpty()) sources.put(itemId, encoded);
        }

        return new SmitheryDataSyncPayload(
                new LinkedHashMap<>(AlloyRecipes.dataEntries()),
                new BasinCasts(mb, results, new LinkedHashSet<>(CastBlocks.dataRemovals())),
                sources,
                new LinkedHashMap<>(ModifierReloadListener.dataDefinitions()),
                new Eligibility(PartEligibility.dataAllowByPartType(), PartEligibility.dataAllowByMaterial()),
                SmitheryStages.snapshot());
    }

    /**
     * Narrows a boxed parameter map to its numeric entries, matching what
     * {@link ModifierEffect#CODEC} keeps. Data-layer effects are float-valued already; this only
     * matters for a mod that registered a data entry by hand with something exotic in it.
     */
    private static Map<String, Float> numericParams(Map<String, Object> params) {
        Map<String, Float> out = new LinkedHashMap<>(Math.max(4, params.size()));
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() instanceof Number n) out.put(e.getKey(), n.floatValue());
        }
        return out;
    }

    /**
     * Installs this snapshot into the local registries, replacing whatever their data layers held.
     * Casts and sources naming an item this client does not have are skipped.
     */
    public void apply() {
        AlloyRecipes.replaceDataEntries(alloys);

        Map<ResourceLocation, CastBlocks.Cast> casts = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Integer> e : basinCasts.mb().entrySet()) {
            ResourceLocation itemId = basinCasts.results().get(e.getKey());
            if (itemId == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) continue;
            casts.put(e.getKey(), new CastBlocks.Cast(e.getValue(), () -> item));
        }
        CastBlocks.replaceDataEntries(casts, basinCasts.removals());

        Map<Item, List<ModifierSources.Entry>> decodedSources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<SourceEntry>> e : sources.entrySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(e.getKey());
            if (item == null || item == Items.AIR) continue;
            List<ModifierSources.Entry> entries = new ArrayList<>(e.getValue().size());
            for (SourceEntry se : e.getValue()) {
                Map<String, Object> boxed = new LinkedHashMap<>(Math.max(4, se.params().size()));
                boxed.putAll(se.params());
                entries.add(new ModifierSources.Entry(
                        new ModifierEffect(se.modifier(), boxed), se.unitValue()));
            }
            decodedSources.put(item, entries);
        }
        ModifierSources.replaceDataEntries(decodedSources);

        ModifierReloadListener.replaceDataDefinitions(modifiers);
        PartEligibility.replaceDataEntries(eligibility.byPartType(), eligibility.byMaterial());
        SmitheryStages.replaceData(stages);
    }

    /** Writes this message to the network buffer. */
    public static void encode(SmitheryDataSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.alloys.size());
        for (Map.Entry<ResourceLocation, AlloyRecipe> e : msg.alloys.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            AlloyRecipe recipe = e.getValue();
            buf.writeVarInt(recipe.inputs().size());
            for (AlloyRecipe.Input in : recipe.inputs()) {
                buf.writeResourceLocation(in.material());
                buf.writeVarInt(in.mb());
            }
            buf.writeResourceLocation(recipe.result().material());
            buf.writeVarInt(recipe.result().mb());
            buf.writeFloat(recipe.minTemperatureC());
        }

        buf.writeVarInt(msg.basinCasts.mb().size());
        for (Map.Entry<ResourceLocation, Integer> e : msg.basinCasts.mb().entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeVarInt(e.getValue());
            buf.writeResourceLocation(msg.basinCasts.results().get(e.getKey()));
        }
        writeIds(buf, msg.basinCasts.removals());

        buf.writeVarInt(msg.sources.size());
        for (Map.Entry<ResourceLocation, List<SourceEntry>> e : msg.sources.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (SourceEntry entry : e.getValue()) {
                buf.writeResourceLocation(entry.modifier());
                buf.writeVarInt(entry.params().size());
                for (Map.Entry<String, Float> param : entry.params().entrySet()) {
                    buf.writeUtf(param.getKey());
                    buf.writeFloat(param.getValue());
                }
                buf.writeVarInt(entry.unitValue());
            }
        }

        // Modifier definitions go through their own codec rather than a hand-written encoder: the
        // record carries a dozen action lists, and the codec is already the authority on its shape.
        Map<ResourceLocation, CompoundTag> encodedModifiers = new LinkedHashMap<>();
        msg.modifiers.forEach((id, json) -> {
            DataResult<Tag> encoded = ModifierReloadListener.ModifierJson.CODEC
                    .encodeStart(NbtOps.INSTANCE, json);
            Tag tag = encoded.resultOrPartial(err -> Smithery.LOGGER.warn(
                    "Could not encode modifier {} for sync: {}", id, err)).orElse(null);
            if (tag instanceof CompoundTag compound) encodedModifiers.put(id, compound);
        });
        buf.writeVarInt(encodedModifiers.size());
        encodedModifiers.forEach((id, tag) -> {
            buf.writeResourceLocation(id);
            buf.writeNbt(tag);
        });

        writeIdSets(buf, msg.eligibility.byPartType());
        writeIdSets(buf, msg.eligibility.byMaterial());

        writeStages(buf, msg.stages.materials());
        buf.writeVarInt(msg.stages.materialPaths().size());
        for (Map.Entry<String, String> e : msg.stages.materialPaths().entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
        writeStages(buf, msg.stages.toolTypes());
        writeStages(buf, msg.stages.modifiers());
    }

    /** Reads a message from the network buffer. */
    public static SmitheryDataSyncPayload decode(FriendlyByteBuf buf) {
        int alloyCount = buf.readVarInt();
        Map<ResourceLocation, AlloyRecipe> alloys = new LinkedHashMap<>(Math.max(4, alloyCount));
        for (int i = 0; i < alloyCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int inputCount = buf.readVarInt();
            List<AlloyRecipe.Input> inputs = new ArrayList<>(inputCount);
            for (int j = 0; j < inputCount; j++) {
                inputs.add(new AlloyRecipe.Input(buf.readResourceLocation(), buf.readVarInt()));
            }
            AlloyRecipe.Output out = new AlloyRecipe.Output(buf.readResourceLocation(), buf.readVarInt());
            alloys.put(id, new AlloyRecipe(inputs, out, buf.readFloat()));
        }

        int castCount = buf.readVarInt();
        Map<ResourceLocation, Integer> mb = new LinkedHashMap<>(Math.max(4, castCount));
        Map<ResourceLocation, ResourceLocation> results = new LinkedHashMap<>(Math.max(4, castCount));
        for (int i = 0; i < castCount; i++) {
            ResourceLocation material = buf.readResourceLocation();
            mb.put(material, buf.readVarInt());
            results.put(material, buf.readResourceLocation());
        }
        Set<ResourceLocation> removals = readIds(buf);

        int sourceCount = buf.readVarInt();
        Map<ResourceLocation, List<SourceEntry>> sources = new LinkedHashMap<>(Math.max(4, sourceCount));
        for (int i = 0; i < sourceCount; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            int entryCount = buf.readVarInt();
            List<SourceEntry> entries = new ArrayList<>(entryCount);
            for (int j = 0; j < entryCount; j++) {
                ResourceLocation modifier = buf.readResourceLocation();
                int paramCount = buf.readVarInt();
                Map<String, Float> params = new LinkedHashMap<>(Math.max(4, paramCount));
                for (int k = 0; k < paramCount; k++) {
                    params.put(buf.readUtf(), buf.readFloat());
                }
                entries.add(new SourceEntry(modifier, params, buf.readVarInt()));
            }
            sources.put(itemId, entries);
        }

        int modifierCount = buf.readVarInt();
        Map<ResourceLocation, ModifierReloadListener.ModifierJson> modifiers =
                new LinkedHashMap<>(Math.max(4, modifierCount));
        for (int i = 0; i < modifierCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            CompoundTag tag = buf.readNbt();
            if (tag == null) continue;
            ModifierReloadListener.ModifierJson json = ModifierReloadListener.ModifierJson.CODEC
                    .parse(NbtOps.INSTANCE, tag)
                    .resultOrPartial(err -> Smithery.LOGGER.warn(
                            "Could not decode synced modifier {}: {}", id, err))
                    .orElse(null);
            if (json != null) modifiers.put(id, json);
        }

        Eligibility eligibility = new Eligibility(readIdSets(buf), readIdSets(buf));

        Map<ResourceLocation, String> materialStages = readStages(buf);
        int pathCount = buf.readVarInt();
        Map<String, String> materialPaths = new LinkedHashMap<>(Math.max(4, pathCount));
        for (int i = 0; i < pathCount; i++) {
            materialPaths.put(buf.readUtf(), buf.readUtf());
        }
        SmitheryStages.Snapshot stages = new SmitheryStages.Snapshot(
                materialStages, materialPaths, readStages(buf), readStages(buf));

        return new SmitheryDataSyncPayload(
                alloys,
                new BasinCasts(mb, results, removals),
                sources,
                modifiers,
                eligibility,
                stages);
    }

    private static void writeIds(FriendlyByteBuf buf, Set<ResourceLocation> ids) {
        buf.writeVarInt(ids.size());
        for (ResourceLocation id : ids) buf.writeResourceLocation(id);
    }

    private static Set<ResourceLocation> readIds(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Set<ResourceLocation> ids = new LinkedHashSet<>(Math.max(4, count));
        for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
        return ids;
    }

    private static void writeIdSets(FriendlyByteBuf buf, Map<ResourceLocation, Set<ResourceLocation>> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : map.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            writeIds(buf, e.getValue());
        }
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> readIdSets(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, Set<ResourceLocation>> map = new LinkedHashMap<>(Math.max(4, count));
        for (int i = 0; i < count; i++) {
            map.put(buf.readResourceLocation(), readIds(buf));
        }
        return map;
    }

    private static void writeStages(FriendlyByteBuf buf, Map<ResourceLocation, String> gates) {
        buf.writeVarInt(gates.size());
        for (Map.Entry<ResourceLocation, String> e : gates.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }

    private static Map<ResourceLocation, String> readStages(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, String> gates = new LinkedHashMap<>(Math.max(4, count));
        for (int i = 0; i < count; i++) {
            gates.put(buf.readResourceLocation(), buf.readUtf());
        }
        return gates;
    }
}
