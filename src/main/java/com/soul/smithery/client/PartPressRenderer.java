package com.soul.smithery.client;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.block.entity.PartPressBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * BER for the Part Press. Renders the Geckolib animated chassis (head + base + legs),
 * then paints two custom overlays on top:
 *
 * <ol>
 *   <li><b>Teeth</b> — 1×1 voxel grey blocks filling the 12×12 grid wherever the
 *       selected part type does NOT occupy a cell. Two sets:
 *       <ul>
 *         <li>Top teeth attached to the {@code head} bone (translate with the closing
 *             animation), hanging from the underside of the {@code main} cube.</li>
 *         <li>Bottom teeth attached to the static {@code base}, rising from its top face.</li>
 *       </ul>
 *       Both are hidden when the press is fully closed.</li>
 *   <li><b>Template part</b> — a 12×12 quad of the selected PartType's greyscale
 *       template texture, painted on top of the head's {@code main} cube. Translates
 *       with the head as it animates.</li>
 * </ol>
 */
public class PartPressRenderer extends GeoBlockRenderer<PartPressBlockEntity, PartPressRenderer.RenderState> {

    /** Identifier used by {@link DefaultedBlockGeoModel} to resolve geo/anim/texture paths. */
    private static final Identifier MODEL_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "part_press");

    /** Tint applied to each tooth voxel. White = show the iron-block texture unmodified. */
    private static final int TOOTH_COLOR = 0xFFFFFFFF;
    /** Tint for the top/bottom faces of each tooth. Slightly darker to fake depth. */
    private static final int TOOTH_SHADE = 0xFFC0C0C0;

    /**
     * Maximum tooth voxel height in pixels (each tooth is a 1 × H × 1 cuboid). Per-cell
     * tooth height ramps from this max (alpha=0, fully transparent → full tooth) down to
     * 0 (alpha=1, fully opaque → no tooth). Sub-pixel values are allowed; the renderer
     * skips cells where the height would be visually negligible.
     */
    private static final float TOOTH_H_PX_MAX = 1f;
    /** Cells whose computed tooth height falls below this fraction of a pixel are skipped. */
    private static final float TOOTH_H_MIN_PX = 0.05f;

    /** Vanilla iron-block texture, used as tooth surface until bespoke art lands. */
    private static final Identifier TOOTH_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/iron_block.png");

    /** Head bone's y-translation at fully-closed pose, in geckolib units (matches animation.json). */
    private static final float HEAD_CLOSED_Y = -10f;

    private final ItemModelResolver itemModelResolver;

    public PartPressRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedBlockGeoModel<>(MODEL_ID));
        this.itemModelResolver = ctx.itemModelResolver();
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(PartPressBlockEntity be, RenderState state, float partialTick,
                                    Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        super.extractRenderState(be, state, partialTick, camPos, crumbling);
        state.closed = be.isClosed();
        PartType pt = be.selectedPartType();
        state.selectedPartTypeId = pt != null ? pt.id() : null;

        // Item-on-the-teeth visuals.
        //   - Raw input (any item): GROUND context, rendered like a dropped item entity.
        //   - Finished output (always a PartItem): flat 12×12 tinted quad like the template
        //     on top, so it lies flat above the teeth instead of standing up.
        state.inputItem.clear();
        state.hasInputItem  = false;
        state.hasOutputPart = false;
        state.outputPartTypeId = null;
        if (!be.outputItem().isEmpty() && be.outputItem().getItem()
                instanceof com.soul.smithery.item.PartItem pi) {
            state.outputPartTypeId = pi.partTypeId();
            var mat = SmitheryAPI.MATERIALS.get(pi.materialId());
            state.outputPartColorArgb = (mat != null ? mat.stats().partColor() : 0xFFFFFF) | 0xFF000000;
            state.hasOutputPart = true;
        } else if (!be.inputItem().isEmpty()) {
            itemModelResolver.updateForTopItem(state.inputItem, be.inputItem(),
                    ItemDisplayContext.GROUND, be.getLevel(), null, 0);
            state.hasInputItem = true;
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        // Let geckolib draw the animated chassis first — animation ticks happen inside this call,
        // so reading head.frameSnapshot afterwards yields the post-animation translation.
        super.submit(state, poseStack, collector, camera);

        if (state.selectedPartTypeId == null) return;

        // Pull the head bone's current Y translation. Used to:
        //   1. position the top-teeth + template-part overlay (they move with the head),
        //   2. decide whether the press is "fully closed" so we can skip teeth rendering
        //      (user-requested perf optimization).
        float headY = readHeadTranslateY(state);
        boolean fullyClosed = state.closed && headY <= HEAD_CLOSED_Y + 0.05f;

        float[][] alphaGrid = PartSilhouetteCache.forPartTypeId(state.selectedPartTypeId);

        // Convert head Y from geckolib units (pixels) → block units. Geckolib applies a
        // 1/16 scale to the model already; super.submit's pose has model-space transforms
        // popped, so we work in block units (1 = full block) again here. The head offset
        // is therefore headY / 16 blocks.
        float headOffsetBlocks = headY / 16f;

        // Teeth — separate submit so we use the iron-block texture (vanilla, no missing-tex
        // surprises). Positioned in block-space where (0,0,0) is the NW-bottom corner and
        // y=1 is the top face.
        if (!fullyClosed) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TOOTH_TEXTURE),
                    (pose, buffer) -> drawTeeth(pose, buffer, alphaGrid, TOOTH_COLOR, TOOTH_SHADE));
        }

        // Item-on-the-teeth render.
        //   - Output (finished part): only shown once the press has fully descended (fullyClosed)
        //     or has fully reopened (!state.closed). Hidden during the closing animation so the
        //     player doesn't see the part materialize mid-stroke — they see empty teeth while the
        //     press is mashing down, then the part appears as the "press is done" cue.
        //   - Input (raw material): shows as a dropped-style item (GROUND display context).
        //     Hidden when fully closed for the same teeth-hiding perf reason.
        final float itemY = (1f / 16f) + (TOOTH_H_PX_MAX / 16f) + (1f / 64f);
        boolean midClosingAnim = state.closed && !fullyClosed;
        if (state.hasOutputPart && state.outputPartTypeId != null && !midClosingAnim) {
            PartType outPt = SmitheryAPI.PART_TYPES.get(state.outputPartTypeId);
            if (outPt != null) {
                Identifier tmpl = outPt.textureTemplate();
                if (tmpl != null) {
                    Identifier texLoc = Identifier.fromNamespaceAndPath(
                            tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
                    final int tint = state.outputPartColorArgb;
                    collector.submitCustomGeometry(poseStack,
                            RenderTypes.entityTranslucent(texLoc),
                            (pose, buffer) -> drawTemplateQuad(pose, buffer, itemY, tint));
                }
            }
        } else if (state.hasInputItem && !fullyClosed) {
            poseStack.pushPose();
            poseStack.translate(0.5f, itemY, 0.5f);
            state.inputItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // Template part — 12×12 quad on top of head, visible only when the press is FULLY
        // open: redstone-off AND the open animation has finished (head Y returned to ~0).
        // During the closing OR opening transitions, the head is mid-flight and the template
        // would visibly slide with it — hiding it until the animation settles avoids that
        // distracting in-between state.
        boolean fullyOpen = !state.closed && headY >= -0.05f;
        if (fullyOpen) {
            PartType pt = SmitheryAPI.PART_TYPES.get(state.selectedPartTypeId);
            if (pt != null) {
                Identifier tmpl = pt.textureTemplate();
                if (tmpl != null) {
                    Identifier texLoc = Identifier.fromNamespaceAndPath(
                            tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
                    final float topY = (15f / 16f) + headOffsetBlocks + (1f / 256f);
                    collector.submitCustomGeometry(poseStack,
                            RenderTypes.entityTranslucent(texLoc),
                            (pose, buffer) -> drawTemplateQuad(pose, buffer, topY, 0xFFFFFFFF));
                }
            }
        }
    }

    /**
     * Reads the {@code head} bone's animated Y translation from the baked geo model. Returns 0
     * if the model isn't available yet (first frame after place, etc).
     *
     * Cast to {@link com.geckolib.renderer.base.GeoRenderState} is safe — Geckolib mixes the
     * interface into vanilla's {@code BlockEntityRenderState} at load time, so the runtime type
     * always implements it even though the Java compiler can't see that statically.
     */
    private float readHeadTranslateY(RenderState state) {
        com.geckolib.renderer.base.GeoRenderState geoState =
                (com.geckolib.renderer.base.GeoRenderState) (Object) state;
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(geoState));
        if (model == null) return 0f;
        Optional<GeoBone> head = model.getBone("head");
        if (head.isEmpty() || head.get().frameSnapshot == null) return 0f;
        return head.get().frameSnapshot.getTranslateY();
    }

    /**
     * Emit one tooth cuboid per grid cell, height scaled by the per-cell alpha:
     * <ul>
     *   <li>alpha = 0 (fully transparent) → full {@link #TOOTH_H_PX_MAX} tooth.</li>
     *   <li>alpha = 1 (fully opaque)      → no tooth (part fully blocks the press at this cell).</li>
     *   <li>in-between alphas → linearly scaled height, sub-pixel allowed.</li>
     * </ul>
     * Cells whose height falls below {@link #TOOTH_H_MIN_PX} are skipped to avoid invisible nubs.
     */
    private static void drawTeeth(PoseStack.Pose pose, VertexConsumer buf, float[][] alphaGrid,
                                  int color, int colorShade) {
        final float cell = 1f / 16f;
        // 12-wide footprint centered in the block: x=2/16 to 14/16 (Blockbench origin -6 size 12).
        final float gridOrigin = 2f / 16f;
        // Bottom teeth grow upward from the bottomplate's top face.
        final float botTeethBot = (1f / 16f);

        for (int z = 0; z < PartSilhouetteCache.GRID; z++) {
            for (int x = 0; x < PartSilhouetteCache.GRID; x++) {
                float alpha = alphaGrid[x][z];                  // 0 = transparent, 1 = opaque
                float heightPx = TOOTH_H_PX_MAX * (1f - alpha); // inverse: transparent → tall
                if (heightPx < TOOTH_H_MIN_PX) continue;
                float xMin = gridOrigin + x * cell;
                float xMax = xMin + cell;
                float zMin = gridOrigin + z * cell;
                float zMax = zMin + cell;
                float botTeethTop = botTeethBot + heightPx * cell;
                drawCuboid(pose, buf, xMin, botTeethBot, zMin, xMax, botTeethTop, zMax, color, colorShade);
            }
        }
    }

    /**
     * Flat 12×12 quad lying just above the head's {@code main} top face. UV maps (0,0) → NW
     * and (1,1) → SE so the part texture orientation matches the impressed sand voxelizer.
     */
    private static void drawTemplateQuad(PoseStack.Pose pose, VertexConsumer buf, float y, int colorArgb) {
        int r = (colorArgb >>> 16) & 0xFF;
        int g = (colorArgb >>> 8)  & 0xFF;
        int b = (colorArgb)        & 0xFF;
        int a = (colorArgb >>> 24) & 0xFF;
        // Centered 12×12 quad matching the head's main-cube footprint: x=2..14, z=2..14
        // in block-space. (Earlier 4..16 was offset 2px east+south of where the head sits.)
        final float min = 2f / 16f;
        final float max = (2f + 12f) / 16f;
        Matrix4f m = pose.pose();
        addVertex(m, pose, buf, min, y, min, r, g, b, a, 0f, 0f, 0f, 1f, 0f);
        addVertex(m, pose, buf, min, y, max, r, g, b, a, 0f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, max, y, max, r, g, b, a, 1f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, max, y, min, r, g, b, a, 1f, 0f, 0f, 1f, 0f);
    }

    /**
     * Submit a 6-face axis-aligned cuboid. The top/bottom faces use {@code colorShade}; the
     * four sides use {@code color}. UV is fixed at (0,0)-(1,1) covering the entire pure-white
     * fallback texture — vertex colour drives the visible shade.
     */
    private static void drawCuboid(PoseStack.Pose pose, VertexConsumer buf,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   int color, int colorShade) {
        int r  = (color      >>> 16) & 0xFF;
        int g  = (color      >>> 8)  & 0xFF;
        int b  = (color)             & 0xFF;
        int a  = (color      >>> 24) & 0xFF;
        int rT = (colorShade >>> 16) & 0xFF;
        int gT = (colorShade >>> 8)  & 0xFF;
        int bT = (colorShade)        & 0xFF;
        int aT = (colorShade >>> 24) & 0xFF;
        Matrix4f m = pose.pose();
        // Up
        addVertex(m, pose, buf, x0, y1, z0, rT, gT, bT, aT, 0, 0, 0,  1, 0);
        addVertex(m, pose, buf, x0, y1, z1, rT, gT, bT, aT, 0, 1, 0,  1, 0);
        addVertex(m, pose, buf, x1, y1, z1, rT, gT, bT, aT, 1, 1, 0,  1, 0);
        addVertex(m, pose, buf, x1, y1, z0, rT, gT, bT, aT, 1, 0, 0,  1, 0);
        // Down
        addVertex(m, pose, buf, x0, y0, z1, rT, gT, bT, aT, 0, 0, 0, -1, 0);
        addVertex(m, pose, buf, x0, y0, z0, rT, gT, bT, aT, 0, 1, 0, -1, 0);
        addVertex(m, pose, buf, x1, y0, z0, rT, gT, bT, aT, 1, 1, 0, -1, 0);
        addVertex(m, pose, buf, x1, y0, z1, rT, gT, bT, aT, 1, 0, 0, -1, 0);
        // North (-Z)
        addVertex(m, pose, buf, x1, y1, z0, r, g, b, a, 0, 0, 0, 0, -1);
        addVertex(m, pose, buf, x1, y0, z0, r, g, b, a, 0, 1, 0, 0, -1);
        addVertex(m, pose, buf, x0, y0, z0, r, g, b, a, 1, 1, 0, 0, -1);
        addVertex(m, pose, buf, x0, y1, z0, r, g, b, a, 1, 0, 0, 0, -1);
        // South (+Z)
        addVertex(m, pose, buf, x0, y1, z1, r, g, b, a, 0, 0, 0, 0,  1);
        addVertex(m, pose, buf, x0, y0, z1, r, g, b, a, 0, 1, 0, 0,  1);
        addVertex(m, pose, buf, x1, y0, z1, r, g, b, a, 1, 1, 0, 0,  1);
        addVertex(m, pose, buf, x1, y1, z1, r, g, b, a, 1, 0, 0, 0,  1);
        // West (-X)
        addVertex(m, pose, buf, x0, y1, z0, r, g, b, a, 0, 0, -1, 0, 0);
        addVertex(m, pose, buf, x0, y0, z0, r, g, b, a, 0, 1, -1, 0, 0);
        addVertex(m, pose, buf, x0, y0, z1, r, g, b, a, 1, 1, -1, 0, 0);
        addVertex(m, pose, buf, x0, y1, z1, r, g, b, a, 1, 0, -1, 0, 0);
        // East (+X)
        addVertex(m, pose, buf, x1, y1, z1, r, g, b, a, 0, 0,  1, 0, 0);
        addVertex(m, pose, buf, x1, y0, z1, r, g, b, a, 0, 1,  1, 0, 0);
        addVertex(m, pose, buf, x1, y0, z0, r, g, b, a, 1, 1,  1, 0, 0);
        addVertex(m, pose, buf, x1, y1, z0, r, g, b, a, 1, 0,  1, 0, 0);
    }

    private static void addVertex(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
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
        // Dynamic overlays can extend past the tight model AABB during animation.
        return true;
    }

    /** Custom render state — extends BlockEntityRenderState so Geckolib's super.extractRenderState fills the base fields. */
    public static final class RenderState extends BlockEntityRenderState {
        public boolean closed = false;
        public @Nullable Identifier selectedPartTypeId;
        public boolean hasInputItem = false;
        public final ItemStackRenderState inputItem  = new ItemStackRenderState();
        /** Output part rendering — drawn as a flat tinted quad (not as a dropped item) so it
         *  lays flat above the teeth, matching the template-on-top look. */
        public boolean hasOutputPart = false;
        public @Nullable Identifier outputPartTypeId;
        public int outputPartColorArgb = 0xFFFFFFFF;
    }
}
