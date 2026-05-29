package com.soul.smithery.client;

import com.mojang.serialization.MapCodec;
import com.soul.smithery.item.PartItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source that multiplies a part item's greyscale template by its material part colour.
 *
 * <p>Stateless. One declaration in the item definition JSON covers every PartItem because the
 * colour is read from the item stack at render time. Non-{@link PartItem} stacks pass through
 * as opaque white.
 */
public record PartMaterialTintSource() implements ItemTintSource {
    /** Shared instance — the tint has no per-stack configuration. */
    public static final PartMaterialTintSource INSTANCE = new PartMaterialTintSource();
    /** Codec that always resolves to {@link #INSTANCE}. */
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
