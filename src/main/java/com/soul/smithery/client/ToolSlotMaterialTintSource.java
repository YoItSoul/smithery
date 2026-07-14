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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source that returns the part colour of the material in one specific slot of a
 * Smithery tool's {@link ToolComposition}.
 *
 * <p>One instance per slot index (0..N-1) is wired into the layered tool model so each visual
 * layer is tinted by its own slot's material. Slot order follows {@code ToolType.slots()},
 * which matches {@link ToolComposition#slotMaterials()}. Falls through to opaque white if the
 * composition is missing, the slot is out of range, or the referenced material has been
 * unregistered, so the underlying greyscale layer renders untinted rather than vanishing.
 *
 * @param slot zero-based index into the tool composition's slot material list
 */
public record ToolSlotMaterialTintSource(int slot) implements ItemTintSource {

    /** Codec encoding/decoding the slot index. */
    public static final MapCodec<ToolSlotMaterialTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(ToolSlotMaterialTintSource::slot)
    ).apply(i, ToolSlotMaterialTintSource::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null) return 0xFFFFFFFF;
        if (slot < 0 || slot >= comp.slotMaterials().size()) return 0xFFFFFFFF;
        ResourceLocation matId = comp.slotMaterials().get(slot);
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
