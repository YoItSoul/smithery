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
 * Item tint source for per-material molten bucket items.
 *
 * <p>Stateless. Resolves the colour at render time by mapping the bucket item id to its
 * registered fluid entry and returning the material's molten colour. Non-registered items
 * pass through as opaque white so they render unchanged. Mirrors the pattern used by
 * {@link PartMaterialTintSource}.
 */
public record MoltenBucketTintSource() implements ItemTintSource {
    /** Shared instance — the tint has no per-stack configuration. */
    public static final MoltenBucketTintSource INSTANCE = new MoltenBucketTintSource();
    /** Codec that always resolves to {@link #INSTANCE}. */
    public static final MapCodec<MoltenBucketTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return 0xFFFFFFFF;
        SmitheryFluids.Entry entry = SmitheryFluids.forBucketItemId(itemId);
        if (entry == null) return 0xFFFFFFFF;
        return entry.material.stats().moltenColor() | 0xFF000000;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
