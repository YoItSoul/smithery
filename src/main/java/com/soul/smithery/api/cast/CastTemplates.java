package com.soul.smithery.api.cast;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modder-facing registry mapping a template Item to the cast-target {@code PartType} id it
 * impresses into casting-table sand.
 *
 * <p>Consulted by the casting table when the player right-clicks a sand-prepared table with an
 * item that isn't a Smithery PartItem. Smithery PartItems are handled directly by the casting
 * table and don't need to be registered here. Built-in templates cover the vanilla
 * iron/gold/copper ingot and iron/gold nugget shapes.
 */
public final class CastTemplates {
    private static final Map<Item, Identifier> ENTRIES = new HashMap<>();

    private CastTemplates() {}

    /** Registers {@code templateItem} as a valid impression template for cast type {@code partTypeId}. */
    public static void register(Item templateItem, Identifier partTypeId) {
        Objects.requireNonNull(templateItem, "templateItem");
        Objects.requireNonNull(partTypeId, "partTypeId");
        ENTRIES.put(templateItem, partTypeId);
    }

    /**
     * Returns the cast-target PartType id for the held stack, or {@code null} if the stack's item
     * isn't a registered template.
     */
    public static @Nullable Identifier resolve(ItemStack stack) {
        return stack.isEmpty() ? null : ENTRIES.get(stack.getItem());
    }
}
