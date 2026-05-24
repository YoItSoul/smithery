package com.soul.smithery.api.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modder-facing registry mapping source Items to the {@link ModifierEffect} an anvil applies
 * when the source is placed in the right-hand slot opposite a Smithery tool. Two complementary
 * paths populate this registry:
 *
 * <h3>1. Code path — call {@link #register} from your mod's init</h3>
 * <pre>{@code
 *   ModifierSources.register(Items.NETHER_STAR,
 *           ModifierEffect.of(SmitheryModifiers.NETHER_SHARPENED, Map.of("damage", 6.0f)));
 * }</pre>
 * Stable across resource reloads. Use for built-in / always-on mappings.
 *
 * <h3>2. Data path — drop a JSON file in your data pack</h3>
 * Path: {@code data/<namespace>/smithery/modifier_source/<anything>.json}
 * <pre>{@code
 *   {
 *     "source_item": "minecraft:ender_pearl",
 *     "modifier":    "smithery:ender_affinity",
 *     "params":      { "chance": 0.30, "radius": 4.0 }
 *   }
 * }</pre>
 * Reloaded on every {@code /reload}. Use for modpack / pack-overridable mappings.
 *
 * <h3>Resolution order at anvil time</h3>
 * <ol>
 *   <li>Data entries (from {@code /reload}) — highest priority. Modpack authors override
 *       defaults by shipping their own JSON.</li>
 *   <li>Code entries (from {@code register}) — fallback. Built-in mappings live here.</li>
 *   <li>No entry — anvil refuses the application (event is left untouched).</li>
 * </ol>
 *
 * <h3>One source ↔ one modifier</h3>
 * Each item maps to exactly one ModifierEffect. Re-registering an item replaces the previous
 * mapping in the same registry. To apply the same modifier with different parameters from
 * different items, register each item separately with its own effect (e.g. "minor sharpening
 * stone" and "major sharpening stone" both mapping to {@code SHARP} with different damage
 * params).
 */
public final class ModifierSources {
    private ModifierSources() {}

    /**
     * Runtime entry — pairs the effect to apply with how many "units" one item of this source
     * contributes toward the modifier's level cost. The MODIFIER declares {@code level_cost}
     * (the total units needed for a level), each SOURCE declares {@code unitValue} (units per
     * item), and progress = items × unitValue. Mixed sources add naturally because their
     * contributions land in the same unit total.
     */
    public record Entry(ModifierEffect effect, int unitValue) {
        public Entry(ModifierEffect effect) { this(effect, 1); }
    }

    /** Stable, code-registered entries. Never cleared automatically. */
    private static final Map<Item, Entry> CODE_REGISTRY = new LinkedHashMap<>();
    /** Data-pack-loaded entries. Cleared and repopulated on every {@code /reload}. */
    private static final Map<Item, Entry> DATA_REGISTRY = new LinkedHashMap<>();

    // ---------------------------------------------------------------------
    //  Code path
    // ---------------------------------------------------------------------

    /** Registers a built-in (code-side) source mapping. Survives reloads. */
    public static void register(Item sourceItem, ModifierEffect effect) {
        register(sourceItem, new Entry(effect));
    }

    /** Code-side source mapping with explicit cost / scaling (e.g. costly post-craft sources). */
    public static void register(Item sourceItem, Entry entry) {
        Objects.requireNonNull(sourceItem, "sourceItem");
        Objects.requireNonNull(entry, "entry");
        CODE_REGISTRY.put(sourceItem, entry);
    }

    // ---------------------------------------------------------------------
    //  Data path — internal entry points called by the reload listener
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    //  Lookups
    // ---------------------------------------------------------------------

    /**
     * Returns the source entry for this stack's item, or {@code null} if it's not a
     * registered source. Data entries take precedence over code entries.
     */
    public static @Nullable Entry resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        Entry data = DATA_REGISTRY.get(item);
        if (data != null) return data;
        return CODE_REGISTRY.get(item);
    }

    public static boolean isSource(ItemStack stack) {
        return resolve(stack) != null;
    }

    /** All resolved entries (data overrides code). Insertion-order preserved. Read-only. */
    public static Map<Item, Entry> all() {
        Map<Item, Entry> merged = new LinkedHashMap<>(CODE_REGISTRY);
        merged.putAll(DATA_REGISTRY);
        return Collections.unmodifiableMap(merged);
    }

    // ---------------------------------------------------------------------
    //  JSON schema for data-pack-loaded entries
    // ---------------------------------------------------------------------

    /**
     * One JSON file under {@code data/<ns>/smithery/modifier_source/} parses to one instance
     * of this record. {@link com.soul.smithery.event.ModifierSourceReloadListener} handles
     * the file walk and registry repopulation; modder mods don't construct these directly.
     *
     * <h4>JSON shape</h4>
     * <pre>{@code
     *   {
     *     "source_item": "minecraft:ender_pearl",
     *     "modifier":    "smithery:ender_affinity",
     *     "params":      { "chance": 0.30 }      // optional, defaults to empty
     *   }
     * }</pre>
     */
    public record JsonEntry(
            Identifier sourceItem,
            Identifier modifier,
            Map<String, Float> params,
            int unitValue
    ) {
        public static final Codec<JsonEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("source_item").forGetter(JsonEntry::sourceItem),
                Identifier.CODEC.fieldOf("modifier").forGetter(JsonEntry::modifier),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
                        .optionalFieldOf("params", Map.of())
                        .forGetter(JsonEntry::params),
                Codec.INT.optionalFieldOf("unit_value", 1)
                        .forGetter(JsonEntry::unitValue)
        ).apply(i, JsonEntry::new));

        /** Convert parsed JSON entry into the runtime {@link ModifierEffect} shape. */
        public ModifierEffect toEffect() {
            Map<String, Object> boxed = new HashMap<>(params.size());
            params.forEach(boxed::put);
            return ModifierEffect.of(modifier, boxed);
        }
    }
}
