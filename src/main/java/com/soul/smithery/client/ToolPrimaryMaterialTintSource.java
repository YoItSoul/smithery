package com.soul.smithery.client;

import com.mojang.serialization.MapCodec;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source that tints a Smithery tool by its primary additive material colour.
 *
 * <p>Stateless. Reads {@link com.soul.smithery.item.tool.ToolComposition} from the stack at
 * render time via {@link SmitheryToolItem#primaryTintColorFor(ItemStack)}, so the same
 * declaration covers tool items, bow items, and arrow items uniformly. Stacks without a
 * composition fall through to opaque white.
 */
public record ToolPrimaryMaterialTintSource() implements ItemTintSource {
    /** Shared instance — the tint has no per-stack configuration. */
    public static final ToolPrimaryMaterialTintSource INSTANCE = new ToolPrimaryMaterialTintSource();
    /** Codec that always resolves to {@link #INSTANCE}. */
    public static final MapCodec<ToolPrimaryMaterialTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return SmitheryToolItem.primaryTintColorFor(stack);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
