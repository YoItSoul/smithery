package com.soul.smithery.api.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-PartType material allow-list. By default a {@code PartType} accepts every registered
 * {@code Material} — i.e. there is one auto-generated {@code PartItem} per (material × part)
 * pair. When a part type appears in this registry, only the materials registered against it
 * become eligible; every other material is excluded — no PartItem generated, no part-press
 * mapping, no casting result.
 *
 * <h3>Two registration paths (mirroring {@code ModifierSources})</h3>
 * <ul>
 *   <li><b>Code path</b> — call {@link #allow(Identifier, Identifier)} from your mod's init.
 *       Stable across reloads; built-in defaults live here.</li>
 *   <li><b>Data path</b> — drop a JSON file at
 *       {@code data/<ns>/smithery/part_eligibility/<part_path>.json}. Loaded on every
 *       {@code /reload} via {@code PartEligibilityReloadListener}; replaces the prior data
 *       layer wholesale. Data entries are unioned with code entries during lookup, so a pack
 *       can <em>extend</em> an allow-list but not shrink it.</li>
 * </ul>
 *
 * <h3>Lookup semantics</h3>
 * <ul>
 *   <li>Part type with zero registrations (code OR data) → <b>unrestricted</b> — every material
 *       is allowed. Backwards-compatible default for existing part types.</li>
 *   <li>Part type with at least one registration → <b>restricted</b> — only the registered
 *       materials are allowed.</li>
 * </ul>
 *
 * <h3>Why allow-list-only, not deny-list</h3>
 * Allow-list keeps the "this part is a curated category" intent explicit. Bowstrings are a
 * curated set: only specific materials make sense (string, slime, exotic strings). A
 * deny-list would invert the burden every time a new material lands, requiring the modder
 * to remember to deny it.
 */
public final class PartEligibility {

    /** Code-registered allow lists. Map part-type id → set of allowed material ids. */
    private static final Map<Identifier, Set<Identifier>> CODE_ALLOW = new HashMap<>();
    /** Data-pack allow lists. Cleared and repopulated on every {@code /reload}. */
    private static final Map<Identifier, Set<Identifier>> DATA_ALLOW = new HashMap<>();

    /**
     * Per-MATERIAL allow lists — the symmetric direction of {@link #CODE_ALLOW}. When a
     * material has an entry here it is "restricted" — it only auto-generates parts for the
     * listed part types. Bowstring-class materials use this to confine themselves to the
     * bowstring slot.
     */
    private static final Map<Identifier, Set<Identifier>> CODE_MATERIAL_ALLOW = new HashMap<>();
    /** Data-pack material allow lists. */
    private static final Map<Identifier, Set<Identifier>> DATA_MATERIAL_ALLOW = new HashMap<>();

    private PartEligibility() {}

    // ---------------------------------------------------------------------
    //  Code path
    // ---------------------------------------------------------------------

    /** Registers {@code materialId} as eligible for {@code partTypeId}. */
    public static void allow(Identifier partTypeId, Identifier materialId) {
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(materialId, "materialId");
        CODE_ALLOW.computeIfAbsent(partTypeId, k -> new LinkedHashSet<>()).add(materialId);
    }

    /** Bulk-register helper. */
    public static void allowAll(Identifier partTypeId, Iterable<Identifier> materialIds) {
        for (Identifier mat : materialIds) allow(partTypeId, mat);
    }

    /**
     * Restricts {@code materialId} to only produce parts of the given part types. Useful for
     * materials that conceptually belong to a single category (bowstring-class strings should
     * not auto-generate sword blades or pick heads). Multiple calls are additive.
     */
    public static void restrictMaterialTo(Identifier materialId, Identifier... partTypeIds) {
        Objects.requireNonNull(materialId, "materialId");
        Set<Identifier> set = CODE_MATERIAL_ALLOW.computeIfAbsent(materialId, k -> new LinkedHashSet<>());
        for (Identifier pt : partTypeIds) {
            Objects.requireNonNull(pt, "partTypeId");
            set.add(pt);
        }
    }

    // ---------------------------------------------------------------------
    //  Data path — called by the reload listener
    // ---------------------------------------------------------------------

    /** Wipes the data layer. Called at the start of each reload pass. */
    public static void clearDataEntries() {
        DATA_ALLOW.clear();
        DATA_MATERIAL_ALLOW.clear();
    }

    /** Registers one parsed JSON entry into the data layer. */
    public static void registerDataEntry(Identifier partTypeId, Set<Identifier> materialIds) {
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(materialIds, "materialIds");
        DATA_ALLOW.computeIfAbsent(partTypeId, k -> new LinkedHashSet<>()).addAll(materialIds);
    }

    /** Registers a per-material restriction loaded from a data pack. */
    public static void registerDataMaterialRestriction(Identifier materialId, Set<Identifier> partTypeIds) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(partTypeIds, "partTypeIds");
        DATA_MATERIAL_ALLOW.computeIfAbsent(materialId, k -> new LinkedHashSet<>()).addAll(partTypeIds);
    }

    // ---------------------------------------------------------------------
    //  Lookups
    // ---------------------------------------------------------------------

    /**
     * Is the (material × part) combo allowed? Two restrictions checked symmetrically:
     * <ol>
     *   <li><b>Part-side</b>: if the part type has an allow-list, the material must be on it.</li>
     *   <li><b>Material-side</b>: if the material has an allow-list of compatible parts, the
     *       part type must be on it.</li>
     * </ol>
     * Both must pass. An unrestricted part + an unrestricted material = always allowed
     * (the existing default behaviour for vanilla smithery materials/parts).
     */
    public static boolean isAllowed(Identifier partTypeId, Identifier materialId) {
        // Material-side check first — cheaper short-circuit for materials with a narrow
        // compatible-parts list (e.g. bowstring-class strings restricted to a single slot).
        Set<Identifier> matCode = CODE_MATERIAL_ALLOW.get(materialId);
        Set<Identifier> matData = DATA_MATERIAL_ALLOW.get(materialId);
        boolean matRestricted = (matCode != null && !matCode.isEmpty())
                              || (matData != null && !matData.isEmpty());
        if (matRestricted) {
            boolean ok = (matCode != null && matCode.contains(partTypeId))
                      || (matData != null && matData.contains(partTypeId));
            if (!ok) return false;
        }

        // Part-side check.
        Set<Identifier> code = CODE_ALLOW.get(partTypeId);
        Set<Identifier> data = DATA_ALLOW.get(partTypeId);
        boolean partRestricted = (code != null && !code.isEmpty())
                              || (data != null && !data.isEmpty());
        if (!partRestricted) return true;
        if (code != null && code.contains(materialId)) return true;
        if (data != null && data.contains(materialId)) return true;
        return false;
    }

    /** True if this part type has any allow-list registration (code or data). */
    public static boolean isRestricted(Identifier partTypeId) {
        Set<Identifier> code = CODE_ALLOW.get(partTypeId);
        Set<Identifier> data = DATA_ALLOW.get(partTypeId);
        return (code != null && !code.isEmpty()) || (data != null && !data.isEmpty());
    }

    /** Union of code + data allow-list for the given part type. Read-only. */
    public static Set<Identifier> allowedMaterials(Identifier partTypeId) {
        Set<Identifier> code = CODE_ALLOW.get(partTypeId);
        Set<Identifier> data = DATA_ALLOW.get(partTypeId);
        if ((code == null || code.isEmpty()) && (data == null || data.isEmpty())) return Set.of();
        Set<Identifier> merged = new LinkedHashSet<>();
        if (code != null) merged.addAll(code);
        if (data != null) merged.addAll(data);
        return Collections.unmodifiableSet(merged);
    }

    // ---------------------------------------------------------------------
    //  JSON schema for data-pack entries
    // ---------------------------------------------------------------------

    /**
     * Schema for {@code data/<ns>/smithery/part_eligibility/<part_path>.json}:
     * <pre>{@code
     *   {
     *     "part_type": "smithery:bowstring",
     *     "materials": ["smithery:string", "smithery:slime", "smithery:flamestring"]
     *   }
     * }</pre>
     */
    public record JsonEntry(Identifier partType, List<Identifier> materials) {
        public static final Codec<JsonEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("part_type").forGetter(JsonEntry::partType),
                Identifier.CODEC.listOf().fieldOf("materials").forGetter(JsonEntry::materials)
        ).apply(i, JsonEntry::new));

        public Set<Identifier> materialSet() { return new HashSet<>(materials); }
    }
}
