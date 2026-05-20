package com.soul.smithery.api.tool;

import com.soul.smithery.api.part.PartType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A ToolType is a registered tool template (Sword, Pickaxe, ...).
 *
 * Each slot lists which PartType is required and its DurabilityRole.
 * Order matters: slots are consumed in declaration order against the shaped recipe.
 */
public final class ToolType {
    private final Identifier id;
    private final List<Slot> slots;

    private ToolType(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        this.slots = Collections.unmodifiableList(new ArrayList<>(b.slots));
        if (slots.isEmpty()) throw new IllegalArgumentException("ToolType " + id + " must have at least one slot");
    }

    public Identifier id() { return id; }
    public List<Slot> slots() { return slots; }

    public List<PartType> additiveParts() {
        return slots.stream().filter(s -> s.role == DurabilityRole.ADDITIVE).map(s -> s.partType).toList();
    }

    public List<PartType> multiplierParts() {
        return slots.stream().filter(s -> s.role == DurabilityRole.MULTIPLIER).map(s -> s.partType).toList();
    }

    @Override public boolean equals(Object o) { return o instanceof ToolType t && t.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "ToolType[" + id + "]"; }

    public static Builder builder(Identifier id) { return new Builder(id); }

    public record Slot(PartType partType, DurabilityRole role) {}

    public static final class Builder {
        private final Identifier id;
        private final List<Slot> slots = new ArrayList<>();

        private Builder(Identifier id) { this.id = id; }

        public Builder addPart(PartType partType, DurabilityRole role) {
            slots.add(new Slot(partType, role));
            return this;
        }

        public Builder addPart(PartType partType, DurabilityRole role, int count) {
            for (int i = 0; i < count; i++) slots.add(new Slot(partType, role));
            return this;
        }

        public ToolType build() { return new ToolType(this); }
    }
}
