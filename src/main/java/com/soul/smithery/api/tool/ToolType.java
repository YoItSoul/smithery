package com.soul.smithery.api.tool;

import com.soul.smithery.api.part.PartType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registered tool template (Sword, Pickaxe, etc.).
 *
 * <p>Each slot lists the required {@link PartType} and its {@link DurabilityRole}. Slot order
 * matters — slots are consumed in declaration order against the shaped crafting recipe.
 */
public final class ToolType {
    private final Identifier id;
    private final List<Slot> slots;

    private ToolType(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        this.slots = Collections.unmodifiableList(new ArrayList<>(b.slots));
        if (slots.isEmpty()) throw new IllegalArgumentException("ToolType " + id + " must have at least one slot");
    }

    /** Identifier for this tool type. */
    public Identifier id() { return id; }

    /** Unmodifiable list of part slots in declaration order. */
    public List<Slot> slots() { return slots; }

    /** Part types occupying additive durability slots. */
    public List<PartType> additiveParts() {
        return slots.stream().filter(s -> s.role == DurabilityRole.ADDITIVE).map(s -> s.partType).toList();
    }

    /** Part types occupying multiplicative durability slots. */
    public List<PartType> multiplierParts() {
        return slots.stream().filter(s -> s.role == DurabilityRole.MULTIPLIER).map(s -> s.partType).toList();
    }

    @Override public boolean equals(Object o) { return o instanceof ToolType t && t.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "ToolType[" + id + "]"; }

    /** Begins building a {@link ToolType} with the given id. */
    public static Builder builder(Identifier id) { return new Builder(id); }

    /**
     * One part slot in a {@link ToolType}.
     *
     * @param partType the required part type
     * @param role     whether this slot contributes additively or multiplicatively to durability
     */
    public record Slot(PartType partType, DurabilityRole role) {}

    /** Fluent builder for {@link ToolType}. */
    public static final class Builder {
        private final Identifier id;
        private final List<Slot> slots = new ArrayList<>();

        private Builder(Identifier id) { this.id = id; }

        /** Appends a single part slot with the given role. */
        public Builder addPart(PartType partType, DurabilityRole role) {
            slots.add(new Slot(partType, role));
            return this;
        }

        /** Appends {@code count} identical slots with the given part type and role. */
        public Builder addPart(PartType partType, DurabilityRole role, int count) {
            for (int i = 0; i < count; i++) slots.add(new Slot(partType, role));
            return this;
        }

        /** Finalizes and returns the built {@link ToolType}. */
        public ToolType build() { return new ToolType(this); }
    }
}
