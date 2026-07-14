package com.soul.smithery.api.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Thread-safe, insertion-ordered registry of identified values backed by a {@code LinkedHashMap}.
 *
 * <p>Iteration order matches insertion order — this is intentional and load-bearing for the
 * forge's alloy conflict tiebreaker, which uses insertion order to decide which of two
 * equal-complexity alloys claims fluids first.
 *
 * @param <T> the registered value type
 */
public class SimpleRegistry<T> {
    private final String registryName;
    private final Function<T, ResourceLocation> idFn;
    private final Map<ResourceLocation, T> map = new LinkedHashMap<>();

    /**
     * Creates a registry with a human-readable name and a function that extracts the id of a value.
     */
    public SimpleRegistry(String registryName, Function<T, ResourceLocation> idFn) {
        this.registryName = registryName;
        this.idFn = idFn;
    }

    /**
     * Registers a new value. The id is extracted from the value via the constructor's id function.
     *
     * @throws IllegalStateException if the id is already present
     */
    public synchronized T register(T value) {
        Objects.requireNonNull(value);
        ResourceLocation id = idFn.apply(value);
        Objects.requireNonNull(id, registryName + " value missing id");
        if (map.containsKey(id)) {
            throw new IllegalStateException(registryName + " already contains " + id);
        }
        map.put(id, value);
        return value;
    }

    /** Replace an existing entry (used by datapack overrides). Returns {@code false} if missing. */
    public synchronized boolean replace(T value) {
        ResourceLocation id = idFn.apply(value);
        if (!map.containsKey(id)) return false;
        map.put(id, value);
        return true;
    }

    /** Remove an entry. Returns true if it was present. */
    public synchronized boolean remove(ResourceLocation id) {
        return map.remove(id) != null;
    }

    /** Looks up a value by id, returning {@code null} if missing. */
    public synchronized T get(ResourceLocation id) { return map.get(id); }

    /** Looks up a value by id wrapped in an {@link Optional}. */
    public synchronized Optional<T> getOptional(ResourceLocation id) { return Optional.ofNullable(map.get(id)); }

    /** True if the registry contains an entry with this id. */
    public synchronized boolean contains(ResourceLocation id) { return map.containsKey(id); }

    /** Number of registered entries. */
    public synchronized int size() { return map.size(); }

    /** Snapshot of all values in insertion order. */
    public synchronized Collection<T> all() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(map.values()));
    }

    /** Snapshot of all ids in insertion order. */
    public synchronized Collection<ResourceLocation> ids() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(map.keySet()));
    }

    /** Human-readable name of this registry (used in error messages). */
    public String registryName() { return registryName; }
}
