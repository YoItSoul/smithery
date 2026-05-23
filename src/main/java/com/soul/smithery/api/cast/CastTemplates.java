package com.soul.smithery.api.cast;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modder-facing registry mapping a template Item (e.g. minecraft:iron_ingot, modder's
 * ender_pearl) to the cast-target PartType id (e.g. smithery:ingot, smithery:pearl) it
 * carves into the sand. The casting table consults this when the player right-clicks a SAND
 * table with an item that isn't a smithery PartItem.
 *
 * Built-in templates:
 *   - iron_ingot, gold_ingot, copper_ingot → smithery:ingot
 *   - iron_nugget, gold_nugget             → smithery:nugget
 *
 * Modder example — register the ender pearl as a "pearl" template:
 * <pre>{@code
 *   CastTemplates.register(Items.ENDER_PEARL, pearlPartTypeId);
 * }</pre>
 * After that, right-clicking a sand-prepared casting table with an ender pearl impresses
 * a pearl-shaped silhouette in the sand.
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
     * Returns the cast-target PartType id for the held stack, or {@code null} if the stack's
     * item isn't a registered template. Smithery PartItems are handled separately by the
     * casting table (no need to register them here).
     */
    public static @Nullable Identifier resolve(ItemStack stack) {
        return stack.isEmpty() ? null : ENTRIES.get(stack.getItem());
    }
}
