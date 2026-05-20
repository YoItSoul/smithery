package com.soul.smithery.client;

import com.mojang.serialization.MapCodec;
import com.soul.smithery.item.PartItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source that returns the part's material color (Material.stats().partColor()).
 *
 * Stateless — the color comes from the item stack at render time, not from the tint source
 * itself. This means every PartItem can use a single shared tint declaration in its item
 * definition JSON: {"type": "smithery:part_material"}.
 *
 * The model JSON for each part item references the PartType's grayscale template texture,
 * and this tint source multiplies the grayscale pixels by the material color.
 */
public record PartMaterialTintSource() implements ItemTintSource {
    public static final PartMaterialTintSource INSTANCE = new PartMaterialTintSource();
    public static final MapCodec<PartMaterialTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        if (stack.getItem() instanceof PartItem partItem) {
            return partItem.tintColor();
        }
        return 0xFFFFFFFF;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
