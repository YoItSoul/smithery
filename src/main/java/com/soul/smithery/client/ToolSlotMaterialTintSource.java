package com.soul.smithery.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source that returns the partColor of the material occupying a specific slot of
 * a Smithery tool's {@link ToolComposition}. One instance per slot index (0..N-1); the dynamic
 * tool item-definition JSON wires up one of these per layer in its {@code tints} array, so
 * each layer of the layered tool model gets tinted by its own slot's material.
 *
 * Example for the sword (slots: blade, guard, handle, binder):
 * <pre>{@code
 *   "tints": [
 *     { "type": "smithery:tool_slot_material", "slot": 0 },   // blade
 *     { "type": "smithery:tool_slot_material", "slot": 1 },   // guard
 *     { "type": "smithery:tool_slot_material", "slot": 2 },   // handle
 *     { "type": "smithery:tool_slot_material", "slot": 3 }    // binder
 *   ]
 * }</pre>
 *
 * Read order is the {@code ToolType.slots()} order — which is also the slot order in
 * {@link ToolComposition#slotMaterials()}. Both lists move in lockstep.
 *
 * Returns a fully-opaque ARGB. If the composition is missing, slot is out of range, or
 * the referenced material has been removed from the registry, returns white so the
 * underlying texture renders untinted rather than disappearing.
 */
public record ToolSlotMaterialTintSource(int slot) implements ItemTintSource {

    public static final MapCodec<ToolSlotMaterialTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(ToolSlotMaterialTintSource::slot)
    ).apply(i, ToolSlotMaterialTintSource::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        // No item-type instanceof gate — checking for TOOL_COMPOSITION presence below is the
        // authoritative test for "smithery-composed item". This used to filter on
        // SmitheryToolItem only, which silently broke the bow & arrow tints (their items
        // extend BowItem / ArrowItem, not SmitheryToolItem, so the tint short-circuited to
        // white and every layer rendered in the raw template color).
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null) return 0xFFFFFFFF;
        if (slot < 0 || slot >= comp.slotMaterials().size()) return 0xFFFFFFFF;
        Identifier matId = comp.slotMaterials().get(slot);
        if (matId == null) return 0xFFFFFFFF;
        Material mat = SmitheryAPI.MATERIALS.get(matId);
        if (mat == null) return 0xFFFFFFFF;
        return mat.stats().partColor() | 0xFF000000;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
