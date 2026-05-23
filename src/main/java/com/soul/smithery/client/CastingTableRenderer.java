package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.CastingTableBlockEntity.State;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
     *
     * The sand slot uses the standard ItemStackRenderState pipeline for the per-PartType
     * "casting_sand_impressed_<part>" block (which itself carries the alpha-cutout silhouette).
     *
     * The part is NOT rendered via ItemStackRenderState — that would lock its tint to the
     * material's partColor at extract time with no way to lerp during cooling. Instead we
     * capture the part's greyscale template texture id + a per-frame tint that lerps from
     * the material's moltenColor (cooling start) to its partColor (cooling end), then draw
     * it ourselves as a single tinted quad in submit().
     */
    public static final class RenderState extends BlockEntityRenderState {
        public State castState = State.EMPTY;
        public final ItemStackRenderState sand = new ItemStackRenderState();
        public boolean hasPart;
        public net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier partTextureLoc;
        public int partTintArgb;
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
        state.hasPart = false;
        state.partTextureLoc = null;
        state.partTintArgb = 0;

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
            case IMPRESSED -> bindSand(state, pickImpressedSandStack(be), be);
            // FILLING now also renders the part quad — with alpha scaled by filledMb/requiredMb
            // so the cast visually fills up as fluid is poured. Useful when a cast is stuck
            // mid-fill waiting for more material to be melted (e.g. forge ran dry mid-pour).
            case FILLING, COOLING, COVERED, READY -> {
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

    /**
     * Captures the part's template texture + per-frame lerped tint instead of using the
     * ItemStackRenderState pipeline — the standard pipeline locks the tint to partColor at
     * extract time with no hook for the cooling fade.
     *
     * Alpha is driven by fill state:
     *   - FILLING            : alpha = filledMb / requiredMb (TiC-style rising fluid level)
     *   - COOLING/COVERED/READY: alpha = 1.0
     * Tint is driven by cooling:
     *   - FILLING / COOLING start (fraction 1.0): moltenColor
     *   - COOLING end / READY     (fraction 0.0): partColor
     *   - Lerped through cooling. Once the cast reaches 100% fill it stays at moltenColor
     *     for an instant then begins fading toward partColor as cooling progresses.
     */
    private static void bindPart(RenderState state, CastingTableBlockEntity be) {
        net.minecraft.resources.Identifier ptId = be.impressedPartTypeId();
        if (ptId == null) return;
        com.soul.smithery.api.part.PartType pt = com.soul.smithery.api.SmitheryAPI.PART_TYPES.get(ptId);
        if (pt == null) return;
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forFluid(be.pouredFluid());
        if (entry == null) return;

        // Two rendering modes:
        //   Smithery PartItem (greyscale template tinted by material partColor):
        //     - texture = PartType.textureTemplate
        //     - baseColor = material's partColor (lerps toward moltenColor during cooling)
        //   Any other item (vanilla ingot, modder ender pearl, etc — has its own coloured texture):
        //     - texture = the result item's own item-texture (minecraft:textures/item/<x>.png etc)
        //     - baseColor = WHITE so the cooled state shows the natural item colours unmodified;
        //       cooling lerp still tints toward moltenColor while hot.
        net.minecraft.world.item.ItemStack resultStack = be.peekPartItem();
        if (resultStack.isEmpty()) return;
        boolean isPartItem = resultStack.getItem() instanceof com.soul.smithery.item.PartItem;

        net.minecraft.resources.Identifier texLoc;
        int baseColor;
        if (isPartItem) {
            net.minecraft.resources.Identifier tmpl = pt.textureTemplate();
            texLoc = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
            baseColor = entry.material.stats().partColor() | 0xFF000000;
        } else {
            net.minecraft.resources.Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(resultStack.getItem());
            if (itemId == null) return;
            texLoc = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            baseColor = 0xFFFFFFFF;
        }
        state.partTextureLoc = texLoc;

        int moltenColor = entry.material.stats().moltenColor() | 0xFF000000;
        float cool      = be.coolingFraction(); // 1.0 → freshly molten, 0.0 → fully cooled
        int rgb         = lerpArgb(baseColor, moltenColor, cool) & 0x00FFFFFF;

        // Alpha = fill fraction during FILLING (rising fluid level), 100% otherwise.
        float fillFraction = 1.0f;
        if (be.state() == State.FILLING && be.requiredMb() > 0) {
            fillFraction = Math.max(0f, Math.min(1f, (float) be.filledMb() / (float) be.requiredMb()));
        }
        int alpha = Math.max(0, Math.min(255, (int) (fillFraction * 255f)));

        state.partTintArgb = (alpha << 24) | rgb;
        state.hasPart = alpha > 0;
    }

    /** ARGB lerp by {@code t} ∈ [0,1]. t=0 returns {@code from}, t=1 returns {@code to}. */
    private static int lerpArgb(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int aF = (from >>> 24) & 0xFF, rF = (from >>> 16) & 0xFF, gF = (from >>> 8) & 0xFF, bF = from & 0xFF;
        int aT = (to   >>> 24) & 0xFF, rT = (to   >>> 16) & 0xFF, gT = (to   >>> 8) & 0xFF, bT = to   & 0xFF;
        int a = aF + Math.round((aT - aF) * t);
        int r = rF + Math.round((rT - rF) * t);
        int g = gF + Math.round((gT - gF) * t);
        int b = bF + Math.round((bT - bF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
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

        // Part rendering as a custom tinted quad. The PartType's greyscale template texture
        // is drawn directly onto the table's top face; vertex colour carries the lerped tint
        // (molten → partColor by coolingFraction) so the part visibly cools from glowing
        // orange to its natural material colour over the cooling window. No ItemStackRenderState
        // for the part — that pipeline can't be re-tinted per-frame.
        if (state.hasPart && state.partTextureLoc != null) {
            final int color = state.partTintArgb;
            final net.minecraft.resources.Identifier tex = state.partTextureLoc;
            collector.submitCustomGeometry(poseStack,
                    RenderTypes.entityTranslucent(tex),
                    (pose, buffer) -> drawPartQuad(pose, buffer, color));
        }
    }

    /**
     * Flat 14×14 quad lying just above the sand surface, UV (0,0) at NW corner / (1,1) at SE
     * — matches the impressed-sand voxelizer's mapping (texture row 0 → block Z=0 / north).
     */
    private static void drawPartQuad(PoseStack.Pose pose, VertexConsumer buf, int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        org.joml.Matrix4f m = pose.pose();
        // 1/256 above 16/16 to avoid z-fighting with the cutout block's top face.
        final float y = 16.0f / 16f + 1.0f / 256f;
        addVertex(m, pose, buf,  1f / 16f, y,  1f / 16f, r, g, b, a, 0f, 0f, 0f, 1f, 0f);
        addVertex(m, pose, buf,  1f / 16f, y, 15f / 16f, r, g, b, a, 0f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, 15f / 16f, y, 15f / 16f, r, g, b, a, 1f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, 15f / 16f, y,  1f / 16f, r, g, b, a, 1f, 0f, 0f, 1f, 0f);
    }

    // ---- Vertex emission helper for the part quad ----

    private static void addVertex(org.joml.Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                                   float x, float y, float z,
                                   int r, int g, int b, int a, float u, float v,
                                   float nx, float ny, float nz) {
        buf.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // Render even if the BE's tight AABB is just off-screen — the dynamic
        // content extends to the rim and can poke beyond the default culling box.
        return true;
    }
}
