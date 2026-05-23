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
     * Render state captured each frame from the BE. Both ItemStackRenderState slots are
     * pre-allocated and cleared per frame; the sand slot is filled for any state with
     * a sand layer, the part slot is filled when a cooled cast is visible (COOLING & READY).
     */
    public static final class RenderState extends BlockEntityRenderState {
        public State castState = State.EMPTY;
        public final ItemStackRenderState sand = new ItemStackRenderState();
        public final ItemStackRenderState part = new ItemStackRenderState();
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
        state.sand.clear();
        state.part.clear();

        // Per-state visual mix:
        //   EMPTY                          → nothing
        //   SAND                           → regular casting sand
        //   IMPRESSED, FILLING             → sand-with-part-cutout (only the silhouette)
        //   COOLING, COVERED, READY        → sand-with-cutout + cooled PartItem in the cutout
        //
        // The mould is reusable — retrieving the part at READY drops state back to IMPRESSED
        // (sand intact), so READY shares the same visual as COOLING. The player's cue to
        // right-click is "I poured and waited; the part is sitting in the cutout."
        //
        // COVERED is a legacy state kept for save compatibility; new casts skip straight from
        // COOLING → READY.
        switch (state.castState) {
            case EMPTY -> { /* nothing rendered */ }
            case SAND -> bindSand(state, new ItemStack(SmitheryBlocks.CASTING_SAND.get()), be);
            case IMPRESSED, FILLING -> bindSand(state, pickImpressedSandStack(be), be);
            case COOLING, COVERED, READY -> {
                bindSand(state, pickImpressedSandStack(be), be);
                bindPart(state, be);
            }
        }
    }

    private void bindSand(RenderState state, ItemStack stack, CastingTableBlockEntity be) {
        if (stack.isEmpty()) return;
        itemModelResolver.updateForTopItem(state.sand, stack, ItemDisplayContext.FIXED,
                be.getLevel(), null, 0);
    }

    private void bindPart(RenderState state, CastingTableBlockEntity be) {
        ItemStack partStack = be.peekPartItem();
        if (partStack.isEmpty()) return;
        itemModelResolver.updateForTopItem(state.part, partStack, ItemDisplayContext.FIXED,
                be.getLevel(), null, 0);
    }

    /** Returns the per-PartType sand-with-cutout block stack, or plain sand if the variant is missing. */
    private static ItemStack pickImpressedSandStack(CastingTableBlockEntity be) {
        Identifier ptId = be.impressedPartTypeId();
        if (ptId == null) return new ItemStack(SmitheryBlocks.CASTING_SAND.get());
        DeferredItem<BlockItem> impressedItem = SmitheryBlocks.getImpressedSandItem(ptId);
        return impressedItem == null
                ? new ItemStack(SmitheryBlocks.CASTING_SAND.get())
                : new ItemStack(impressedItem.get());
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

        if (!state.part.isEmpty()) {
            // Laying a 2D item sprite flat onto the table:
            //   X-axis rotation +90°: maps the sprite's "up" (item-Y+) to world Z+ (south),
            //     which aligns with how the impressed-sand voxelizer reads the template texture
            //     (texture row 0 = north, row 15 = south). Using -90° put the sprite's up
            //     at world-north — *opposite* to the impression — making the cooled part
            //     appear 180°-flipped relative to the imprint above. +90° matches.
            //   Y rotation 180°: a second flip is required because the item rendering pipeline
            //     creates the voxel-extruded mesh from the texture mirror-imaged left/right
            //     relative to the BufferedImage column order used by the impression voxelizer.
            //   Z scale = 2/16: gives 2px thickness after the X rotation (item-local depth →
            //     world Y), so the part reads visibly without poking out the top of the rim.
            poseStack.pushPose();
            // Raised 1 px from the previous y=15.5/16 so the part's top edge lines up
            // with the top of the sand layer instead of sitting one pixel below it.
            poseStack.translate(0.5f, 16.5f / 16f, 0.5f);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
            poseStack.scale(14f / 16f, 14f / 16f, 2f / 16f);
            state.part.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // Render even if the BE's tight AABB is just off-screen — the dynamic
        // content extends to the rim and can poke beyond the default culling box.
        return true;
    }
}
