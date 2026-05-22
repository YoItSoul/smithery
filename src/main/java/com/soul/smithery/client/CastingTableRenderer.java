package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.CastingTableBlockEntity.State;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * BER for the Casting Table.
 *
 * Step 3 scope:
 *   - state >= SAND: render the casting_sand block as an item, scaled flat into the
 *     14×1×14 cavity between the rims.
 *   - state == IMPRESSED: also render the wood-material PartItem of the impressed
 *     PartType, laid flat just above the sand surface. Wood is the darkest material
 *     so it reads as the recessed shape pressed into the sand.
 *
 * Later steps will extend this to render molten fluid filling the impression,
 * the cooled metal, and the brush-clearance animation.
 */
public class CastingTableRenderer
        implements BlockEntityRenderer<CastingTableBlockEntity, CastingTableRenderer.RenderState> {

    /**
     * Render state captured each frame from the BE.
     * ItemStackRenderState objects are pre-allocated in the constructor — mirrors
     * the CampfireRenderState pattern. Lazy allocation in extractRenderState
     * was suspected of confusing the deferred batcher and rendering nothing.
     */
    public static final class RenderState extends BlockEntityRenderState {
        public State castState = State.EMPTY;
        public final ItemStackRenderState sand       = new ItemStackRenderState();
        public final ItemStackRenderState impression = new ItemStackRenderState();
    }

    private final ItemModelResolver itemModelResolver;

    public CastingTableRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(CastingTableBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        state.castState = be.state();
        // Always update the pre-allocated render states. Clear if not needed.
        state.sand.clear();
        state.impression.clear();

        // State >= SAND: render a sand layer. For IMPRESSED, swap to the per-PartType
        // "sand with cutout" variant — same rendering path, different block. The block's
        // model uses RenderType.cutout() with a composite texture that has the part shape
        // punched to alpha=0, producing an actual hole in the sand instead of a dark blob.
        if (state.castState.ordinal() >= State.SAND.ordinal()) {
            ItemStack sandStack = pickSandItemStack(be, state.castState);
            itemModelResolver.updateForTopItem(state.sand, sandStack,
                    ItemDisplayContext.FIXED, be.getLevel(), null, 0);
        }
        // The old wood-silhouette "impression" layer is gone — the cutout in the sand
        // itself communicates the impressed shape now. state.impression stays cleared.
    }

    /** Selects the sand block to render for the current cast state. */
    private static ItemStack pickSandItemStack(CastingTableBlockEntity be, State castState) {
        if (castState == State.IMPRESSED) {
            Identifier ptId = be.impressedPartTypeId();
            if (ptId != null) {
                DeferredItem<BlockItem> impressedItem = SmitheryBlocks.getImpressedSandItem(ptId);
                if (impressedItem != null) {
                    return new ItemStack(impressedItem.get());
                }
            }
        }
        return new ItemStack(SmitheryBlocks.CASTING_SAND.get());
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        // Same logic as Tinkers' 1.12 CastingRenderer, ported to MC 26.1.x:
        //   - position at the sand slot (centered horizontally, top of slab vertically)
        //   - scale XZ to fit the 14×14 inner area
        //   - non-block items get rotated -90° on X to lay flat
        //   - use the BE's natural light (state.lightCoords) — full-bright was wrong
        //
        // The one new wrinkle in 26.1.x: ItemDisplayContext.FIXED applies its own scale.
        // For block models (extending block/block) FIXED applies scale(0.5), so we
        // double our pose-stack scale to compensate. For item models (extending
        // item/generated) FIXED is identity scale, so we use Tinkers' values directly.

        if (!state.sand.isEmpty()) {
            poseStack.pushPose();
            // The item pipeline centers block models around the pose-stack origin,
            // so we just translate to where we want the sand's center: middle of
            // the block on XZ, half a pixel above the rim's bottom on Y so the
            // slab sits in the rim slot.
            poseStack.translate(0.5f, 15.5f / 16f, 0.5f);
            // Scale to final 14×1×14 (in pixel units). FIXED display context for
            // block models applies its own scale(0.5), so we double our values
            // here to land at the right final dimensions.
            poseStack.scale(14f / 16f * 2f, 1f / 16f * 2f, 14f / 16f * 2f);
            state.sand.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // No separate "impression" pass needed — the sand block itself is rendered with
        // a part-shaped alpha cutout when IMPRESSED, so the hole reveals the table
        // surface underneath. Old wood-silhouette code lived here pre-cutout.
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // Render even if the BE's tight AABB is just off-screen — the dynamic
        // content extends to the rim and can poke beyond the default culling box.
        return true;
    }
}
