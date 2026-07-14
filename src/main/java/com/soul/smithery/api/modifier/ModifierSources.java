package com.soul.smithery.api.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modder-facing registry mapping source Items to the {@link ModifierEffect} an anvil applies when
 * the source is placed opposite a Smithery tool.
 *
 * <p>Two layers coexist: a code layer ({@link #register}) populated by mod init for stable
 * built-in mappings, and a data layer repopulated on every {@code /reload} from JSON files at
 * {@code data/<namespace>/smithery/modifier_source/<anything>.json}. Data entries take precedence
 * on collision so modpacks can override defaults.
 *
 * <p>Each item maps to exactly one {@link Entry}. Re-registering an item replaces the previous
 * mapping in the same registry. To apply the same modifier with different parameters from
 * different items, register each item separately with its own effect.
 */
public final class ModifierSources {
    private ModifierSources() {}

    /**
     * Runtime entry pairing the effect to apply with how many "units" one item of this source
     * contributes toward the modifier's level cost.
     *
     * <p>The {@link Modifier} declares a total level cost; each source declares its
     * {@link #unitValue} (units per item) and progress = {@code items * unitValue}. Mixed sources
     * add naturally because their contributions land in the same unit total.
     *
     * @param effect    the modifier effect produced by anvil application
     * @param unitValue units contributed per item consumed (default 1)
     */
    public record Entry(ModifierEffect effect, int unitValue) {
        /** Single-item-per-unit convenience constructor. */
        public Entry(ModifierEffect effect) { this(effect, 1); }
    }

    private static final Map<Item, Entry> CODE_REGISTRY = new LinkedHashMap<>();
    private static final Map<Item, Entry> DATA_REGISTRY = new LinkedHashMap<>();

    /** Registers a built-in (code-side) source mapping. Survives reloads. */
    public static void register(Item sourceItem, ModifierEffect effect) {
        register(sourceItem, new Entry(effect));
    }

    /** Code-side source mapping with explicit cost (e.g. costly post-craft sources). */
    public static void register(Item sourceItem, Entry entry) {
        Objects.requireNonNull(sourceItem, "sourceItem");
        Objects.requireNonNull(entry, "entry");
        CODE_REGISTRY.put(sourceItem, entry);
    }

    /** Clears all data-loaded entries. Called at the start of each reload pass. */
    public static void clearDataEntries() {
        DATA_REGISTRY.clear();
    }

    /** Adds a data-loaded entry. Called for each parsed JSON file during reload. */
    public static void registerDataEntry(Item sourceItem, Entry entry) {
        Objects.requireNonNull(sourceItem, "sourceItem");
        Objects.requireNonNull(entry, "entry");
        DATA_REGISTRY.put(sourceItem, entry);
    }

    /**
     * Returns the source entry for this stack's item, or {@code null} if it isn't a registered
     * source. Data entries take precedence over code entries.
     */
    public static @Nullable Entry resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        Entry data = DATA_REGISTRY.get(item);
        if (data != null) return data;
        return CODE_REGISTRY.get(item);
    }

    /** True iff this stack maps to a registered modifier source. */
    public static boolean isSource(ItemStack stack) {
        return resolve(stack) != null;
    }

    /** All resolved entries (data overrides code), preserving insertion order. Read-only. */
    public static Map<Item, Entry> all() {
        Map<Item, Entry> merged = new LinkedHashMap<>(CODE_REGISTRY);
        merged.putAll(DATA_REGISTRY);
        return Collections.unmodifiableMap(merged);
    }

    /**
     * Schema record for one JSON file under {@code data/<ns>/smithery/modifier_source/}.
     *
     * <p>Parsed by {@code ModifierSourceReloadListener}; modders don't construct these directly.
     *
     * @param sourceItem id of the source item that triggers the modifier
     * @param modifier   id of the {@link Modifier} to apply
     * @param params     optional parameter map (defaults to empty)
     * @param unitValue  units this item contributes per application (defaults to 1)
     */
    public record JsonEntry(
            ResourceLocation sourceItem,
            ResourceLocation modifier,
            Map<String, Float> params,
            int unitValue
    ) {
        /** Codec for {@link JsonEntry}. */
        public static final Codec<JsonEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("source_item").forGetter(JsonEntry::sourceItem),
                ResourceLocation.CODEC.fieldOf("modifier").forGetter(JsonEntry::modifier),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
                        .optionalFieldOf("params", Map.of())
                        .forGetter(JsonEntry::params),
                Codec.INT.optionalFieldOf("unit_value", 1)
                        .forGetter(JsonEntry::unitValue)
        ).apply(i, JsonEntry::new));

        /** Convert this parsed JSON entry into the runtime {@link ModifierEffect} shape. */
        public ModifierEffect toEffect() {
            Map<String, Object> boxed = new HashMap<>(params.size());
            params.forEach(boxed::put);
            return ModifierEffect.of(modifier, boxed);
        }
    }
}
