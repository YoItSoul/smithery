package com.soul.smithery.client;

import com.mojang.serialization.MapCodec;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source for the per-material molten bucket items.
 *
 * Stateless — the color is derived from the stack's item at render time by
 * looking up the bucket-id → fluid entry → material → moltenColor chain.
 * One shared tint declaration covers all buckets, identical pattern to
 * {@link PartMaterialTintSource}.
 *
 * Returns 0xFFFFFFFF (white = identity multiply) if the stack is not a
 * registered molten bucket, so non-smithery items would render unchanged
 * if they ever reached this tint source.
 */
public record MoltenBucketTintSource() implements ItemTintSource {
    public static final MoltenBucketTintSource INSTANCE = new MoltenBucketTintSource();
    public static final MapCodec<MoltenBucketTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return 0xFFFFFFFF;
        SmitheryFluids.Entry entry = SmitheryFluids.forBucketItemId(itemId);
        if (entry == null) return 0xFFFFFFFF;
        // Force full alpha — moltenColor() may have been authored with anything.
        return entry.material.stats().moltenColor() | 0xFF000000;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
