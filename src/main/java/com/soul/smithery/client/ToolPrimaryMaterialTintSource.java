package com.soul.smithery.client;

import com.mojang.serialization.MapCodec;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Tints a Smithery tool by its primary additive material color (sword = blade, pickaxe = head).
 *
 * Stateless: reads {@link com.soul.smithery.item.tool.ToolComposition} from the stack at render
 * time. Composite per-part rendering is a future enhancement; this gives a single coherent
 * silhouette tint for v1.
 */
public record ToolPrimaryMaterialTintSource() implements ItemTintSource {
    public static final ToolPrimaryMaterialTintSource INSTANCE = new ToolPrimaryMaterialTintSource();
    public static final MapCodec<ToolPrimaryMaterialTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        // Composition-presence-based — covers SmitheryToolItem as well as SmitheryBowItem and
        // SmitheryArrowItem (which don't extend SmitheryToolItem). The static helper returns
        // opaque white when no composition is present, so non-smithery items also pass through
        // untinted as before.
        return SmitheryToolItem.primaryTintColorFor(stack);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
