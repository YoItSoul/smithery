package com.soul.smithery.api.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
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
 * Per-{@link PartType} and per-Material allow-list registry controlling auto-generated PartItems.
 *
 * <p>By default a part type accepts every registered material — one auto-generated PartItem per
 * (material x part) pair. Registering allow-list entries here restricts auto-generation: only the
 * listed combinations produce items, get part-press mappings, or yield casting results.
 *
 * <p>Two registration paths coexist: a code layer (stable across reloads) and a data layer
 * loaded from JSON at {@code data/<ns>/smithery/part_eligibility/<part_path>.json} on every
 * {@code /reload}. Data entries are unioned with code entries, so packs can <em>extend</em> an
 * allow-list but not shrink it.
 *
 * <p>Symmetric per-material restrictions are also supported via {@link #restrictMaterialTo}: a
 * material can declare it should only generate parts for specific part types (used by
 * bowstring-class materials that should never produce sword blades).
 */
public final class PartEligibility {

    private static final Map<ResourceLocation, Set<ResourceLocation>> CODE_ALLOW = new HashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> DATA_ALLOW = new HashMap<>();

    private static final Map<ResourceLocation, Set<ResourceLocation>> CODE_MATERIAL_ALLOW = new HashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> DATA_MATERIAL_ALLOW = new HashMap<>();

    private PartEligibility() {}

    /** Registers {@code materialId} as eligible for {@code partTypeId}. */
    public static void allow(ResourceLocation partTypeId, ResourceLocation materialId) {
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(materialId, "materialId");
        CODE_ALLOW.computeIfAbsent(partTypeId, k -> new LinkedHashSet<>()).add(materialId);
    }

    /** Bulk-register helper: registers every material id in the iterable for the given part type. */
    public static void allowAll(ResourceLocation partTypeId, Iterable<ResourceLocation> materialIds) {
        for (ResourceLocation mat : materialIds) allow(partTypeId, mat);
    }

    /**
     * Restricts {@code materialId} to only produce parts of the given part types.
     *
     * <p>Useful for materials that conceptually belong to a single category (bowstring-class
     * strings shouldn't auto-generate sword blades or pick heads). Multiple calls are additive.
     */
    public static void restrictMaterialTo(ResourceLocation materialId, ResourceLocation... partTypeIds) {
        Objects.requireNonNull(materialId, "materialId");
        Set<ResourceLocation> set = CODE_MATERIAL_ALLOW.computeIfAbsent(materialId, k -> new LinkedHashSet<>());
        for (ResourceLocation pt : partTypeIds) {
            Objects.requireNonNull(pt, "partTypeId");
            set.add(pt);
        }
    }

    /** Wipes the data layer. Called at the start of each reload pass. */
    public static void clearDataEntries() {
        DATA_ALLOW.clear();
        DATA_MATERIAL_ALLOW.clear();
    }

    /** Registers one parsed JSON entry into the data layer. */
    public static void registerDataEntry(ResourceLocation partTypeId, Set<ResourceLocation> materialIds) {
        Objects.requireNonNull(partTypeId, "partTypeId");
        Objects.requireNonNull(materialIds, "materialIds");
        DATA_ALLOW.computeIfAbsent(partTypeId, k -> new LinkedHashSet<>()).addAll(materialIds);
    }

    /** Registers a per-material restriction loaded from a data pack. */
    public static void registerDataMaterialRestriction(ResourceLocation materialId, Set<ResourceLocation> partTypeIds) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(partTypeIds, "partTypeIds");
        DATA_MATERIAL_ALLOW.computeIfAbsent(materialId, k -> new LinkedHashSet<>()).addAll(partTypeIds);
    }

    /**
     * Returns whether the (material, part) combo is allowed.
     *
     * <p>Both directions are checked: if the part has an allow-list the material must be on it,
     * and if the material has a compatible-parts list the part type must be on it. An
     * unrestricted part plus an unrestricted material is always allowed.
     */
    public static boolean isAllowed(ResourceLocation partTypeId, ResourceLocation materialId) {
        Set<ResourceLocation> matCode = CODE_MATERIAL_ALLOW.get(materialId);
        Set<ResourceLocation> matData = DATA_MATERIAL_ALLOW.get(materialId);
        boolean matRestricted = (matCode != null && !matCode.isEmpty())
                              || (matData != null && !matData.isEmpty());
        if (matRestricted) {
            boolean ok = (matCode != null && matCode.contains(partTypeId))
                      || (matData != null && matData.contains(partTypeId));
            if (!ok) return false;
        }

        Set<ResourceLocation> code = CODE_ALLOW.get(partTypeId);
        Set<ResourceLocation> data = DATA_ALLOW.get(partTypeId);
        boolean partRestricted = (code != null && !code.isEmpty())
                              || (data != null && !data.isEmpty());
        if (!partRestricted) return true;
        if (code != null && code.contains(materialId)) return true;
        if (data != null && data.contains(materialId)) return true;
        return false;
    }

    /** True if this part type has any allow-list registration (code or data). */
    public static boolean isRestricted(ResourceLocation partTypeId) {
        Set<ResourceLocation> code = CODE_ALLOW.get(partTypeId);
        Set<ResourceLocation> data = DATA_ALLOW.get(partTypeId);
        return (code != null && !code.isEmpty()) || (data != null && !data.isEmpty());
    }

    /** Union of code + data allow-list for the given part type. Read-only. */
    public static Set<ResourceLocation> allowedMaterials(ResourceLocation partTypeId) {
        Set<ResourceLocation> code = CODE_ALLOW.get(partTypeId);
        Set<ResourceLocation> data = DATA_ALLOW.get(partTypeId);
        if ((code == null || code.isEmpty()) && (data == null || data.isEmpty())) return Set.of();
        Set<ResourceLocation> merged = new LinkedHashSet<>();
        if (code != null) merged.addAll(code);
        if (data != null) merged.addAll(data);
        return Collections.unmodifiableSet(merged);
    }

    /**
     * Schema record for {@code data/<ns>/smithery/part_eligibility/<part_path>.json}.
     *
     * @param partType  the part type id being allow-listed
     * @param materials the list of material ids allowed for that part type
     */
    public record JsonEntry(ResourceLocation partType, List<ResourceLocation> materials) {
        /** Codec for {@link JsonEntry}. */
        public static final Codec<JsonEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("part_type").forGetter(JsonEntry::partType),
                ResourceLocation.CODEC.listOf().fieldOf("materials").forGetter(JsonEntry::materials)
        ).apply(i, JsonEntry::new));

        /** Returns the {@link #materials} list as a {@link Set}. */
        public Set<ResourceLocation> materialSet() { return new HashSet<>(materials); }
    }
}
