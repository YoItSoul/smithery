package com.soul.smithery.api.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of (entity class to Smithery material id) mappings used when a hot forge scalds a mob.
 *
 * <p>When a {@code LivingEntity} takes damage inside a hot forge interior, the controller looks
 * the entity up here and drips the mapped material's fluid into its fluid storage (50 mB per
 * damage event). When no mapping matches, the {@link #getDefault() default fluid} is used.
 *
 * <p>Lookups walk entries in registration order, returning the first class for which
 * {@code Class.isInstance} matches. Register more specific subclasses BEFORE their supertypes if
 * you want the subclass-specific fluid to win. Built-in mappings register in common setup, so
 * modder additions in {@code FMLCommonSetupEvent} layer on top.
 */
public final class ForgeMobDrops {
    private ForgeMobDrops() {}

    private static final Map<Class<? extends LivingEntity>, ResourceLocation> REGISTRY = new LinkedHashMap<>();
    private static @Nullable ResourceLocation defaultMaterial = null;

    /** Registers (or replaces) the fluid material dripped by entities of {@code entityClass}. */
    public static void register(Class<? extends LivingEntity> entityClass, ResourceLocation materialId) {
        Objects.requireNonNull(entityClass, "entityClass");
        Objects.requireNonNull(materialId, "materialId");
        REGISTRY.put(entityClass, materialId);
    }

    /** Removes a previously-registered mapping, if any. */
    public static void unregister(Class<? extends LivingEntity> entityClass) {
        REGISTRY.remove(entityClass);
    }

    /**
     * Sets the fluid used when no specific entity-class mapping matches. {@code null} disables the
     * default so unmatched entities drip nothing.
     */
    public static void setDefault(@Nullable ResourceLocation materialId) {
        defaultMaterial = materialId;
    }

    /** Returns the fallback material id used when no specific mapping matches. */
    public static @Nullable ResourceLocation getDefault() {
        return defaultMaterial;
    }

    /**
     * Returns the material id this entity drips. Walks the registry in insertion order
     * (first {@code isInstance} match wins) and falls back to {@link #getDefault()}.
     */
    public static @Nullable ResourceLocation materialFor(LivingEntity entity) {
        if (entity == null) return null;
        for (Map.Entry<Class<? extends LivingEntity>, ResourceLocation> e : REGISTRY.entrySet()) {
            if (e.getKey().isInstance(entity)) return e.getValue();
        }
        return defaultMaterial;
    }

    /** Unmodifiable view of every registered (entity class to material id) mapping. */
    public static Map<Class<? extends LivingEntity>, ResourceLocation> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
