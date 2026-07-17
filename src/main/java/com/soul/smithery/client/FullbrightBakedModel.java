package com.soul.smithery.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.QuadTransformers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baked-model wrapper that re-emits the wrapped model's quads with a full-bright baked
 * lightmap, making the item glow regardless of world light (held, dropped, and item-frame
 * renders; GUI items are already lit fullbright).
 *
 * <p>Forge's patched {@code VertexConsumer#putBulkData} merges a quad's baked lightmap with
 * the incoming packed light, so no custom renderer is needed. Transformed quad lists are
 * computed lazily once per face and cached for the lifetime of the model (until the next
 * resource reload rebuilds all baked models), so per-frame cost is a map lookup.
 */
public final class FullbrightBakedModel extends BakedModelWrapper<BakedModel> {

    /** Wrapper identity cache so repeated wrapping of the same base model is free. */
    private static final Map<BakedModel, FullbrightBakedModel> WRAPPED = new ConcurrentHashMap<>();

    private final Map<Direction, List<BakedQuad>> faceQuads = new ConcurrentHashMap<>();
    private volatile List<BakedQuad> unculledQuads;

    private FullbrightBakedModel(BakedModel base) {
        super(base);
    }

    /** Returns the full-bright wrapper for {@code base}, reusing an existing one if present. */
    public static BakedModel of(BakedModel base) {
        if (base instanceof FullbrightBakedModel already) return already;
        return WRAPPED.computeIfAbsent(base, FullbrightBakedModel::new);
    }

    /** Drops every cached wrapper; call when models are rebaked (resource reload). */
    public static void clearCache() {
        WRAPPED.clear();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        if (side == null) {
            List<BakedQuad> cached = unculledQuads;
            if (cached == null) {
                cached = QuadTransformers.settingMaxEmissivity().process(super.getQuads(state, null, rand));
                unculledQuads = cached;
            }
            return cached;
        }
        return faceQuads.computeIfAbsent(side,
                s -> QuadTransformers.settingMaxEmissivity().process(super.getQuads(state, s, rand)));
    }
}
