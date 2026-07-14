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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Block entity renderer for the part press.
 *
 * <p>Renders the animated geckolib chassis (head, base, legs) and overlays:
 * <ul>
 *   <li>Per-cell tooth voxels filling the 12x12 grid wherever the selected
 *       {@link com.soul.smithery.api.part.PartType} is transparent — top teeth attached to
 *       the animated head bone, bottom teeth attached to the static base.</li>
 *   <li>A 12x12 template quad showing the selected part's greyscale texture above the head,
 *       visible only when the press has fully reopened.</li>
 *   <li>Input item (during press cycle) or finished output part (after the press completes)
 *       on top of the teeth.</li>
 * </ul>
 */
public class PartPressRenderer extends GeoBlockRenderer<PartPressBlockEntity, PartPressRenderer.RenderState> {

    private static final ResourceLocation MODEL_ID =
            new ResourceLocation(Smithery.MODID, "part_press");

    private static final int TOOTH_COLOR = 0xFFFFFFFF;
    private static final int TOOTH_SHADE = 0xFFC0C0C0;

    private static final float TOOTH_H_PX_MAX = 1f;
    private static final float TOOTH_H_MIN_PX = 0.05f;

    private static final ResourceLocation TOOTH_TEXTURE =
            new ResourceLocation("textures/block/iron_block.png");

    private static final float HEAD_CLOSED_Y = -10f;

    private final ItemModelResolver itemModelResolver;

    /**
     * Constructs the renderer with the provider context.
     *
     * @param ctx renderer provider context supplying the shared item model resolver
     */
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
        super.submit(state, poseStack, collector, camera);

        if (state.selectedPartTypeId == null) return;

        float headY = readHeadTranslateY(state);
        boolean fullyClosed = state.closed && headY <= HEAD_CLOSED_Y + 0.05f;

        float[][] alphaGrid = PartSilhouetteCache.forPartTypeId(state.selectedPartTypeId);

        float headOffsetBlocks = headY / 16f;

        if (!fullyClosed) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TOOTH_TEXTURE),
                    (pose, buffer) -> drawTeeth(pose, buffer, alphaGrid, TOOTH_COLOR, TOOTH_SHADE));
        }

        final float itemY = (1f / 16f) + (TOOTH_H_PX_MAX / 16f) + (1f / 64f);
        boolean midClosingAnim = state.closed && !fullyClosed;
        if (state.hasOutputPart && state.outputPartTypeId != null && !midClosingAnim) {
            PartType outPt = SmitheryAPI.PART_TYPES.get(state.outputPartTypeId);
            if (outPt != null) {
                ResourceLocation tmpl = outPt.textureTemplate();
                if (tmpl != null) {
                    ResourceLocation texLoc = new ResourceLocation(
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

        boolean fullyOpen = !state.closed && headY >= -0.05f;
        if (fullyOpen) {
            PartType pt = SmitheryAPI.PART_TYPES.get(state.selectedPartTypeId);
            if (pt != null) {
                ResourceLocation tmpl = pt.textureTemplate();
                if (tmpl != null) {
                    ResourceLocation texLoc = new ResourceLocation(
                            tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
                    final float topY = (15f / 16f) + headOffsetBlocks + (1f / 256f);
                    collector.submitCustomGeometry(poseStack,
                            RenderTypes.entityTranslucent(texLoc),
                            (pose, buffer) -> drawTemplateQuad(pose, buffer, topY, 0xFFFFFFFF));
                }
            }
        }
    }

    private float readHeadTranslateY(RenderState state) {
        com.geckolib.renderer.base.GeoRenderState geoState =
                (com.geckolib.renderer.base.GeoRenderState) (Object) state;
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(geoState));
        if (model == null) return 0f;
        Optional<GeoBone> head = model.getBone("head");
        if (head.isEmpty() || head.get().frameSnapshot == null) return 0f;
        return head.get().frameSnapshot.getTranslateY();
    }

    private static void drawTeeth(PoseStack.Pose pose, VertexConsumer buf, float[][] alphaGrid,
                                  int color, int colorShade) {
        final float cell = 1f / 16f;
        final float gridOrigin = 2f / 16f;
        final float botTeethBot = (1f / 16f);

        for (int z = 0; z < PartSilhouetteCache.GRID; z++) {
            for (int x = 0; x < PartSilhouetteCache.GRID; x++) {
                float alpha = alphaGrid[x][z];
                float heightPx = TOOTH_H_PX_MAX * (1f - alpha);
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

    private static void drawTemplateQuad(PoseStack.Pose pose, VertexConsumer buf, float y, int colorArgb) {
        int r = (colorArgb >>> 16) & 0xFF;
        int g = (colorArgb >>> 8)  & 0xFF;
        int b = (colorArgb)        & 0xFF;
        int a = (colorArgb >>> 24) & 0xFF;
        final float min = 2f / 16f;
        final float max = (2f + 12f) / 16f;
        Matrix4f m = pose.pose();
        addVertex(m, pose, buf, min, y, min, r, g, b, a, 0f, 0f, 0f, 1f, 0f);
        addVertex(m, pose, buf, min, y, max, r, g, b, a, 0f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, max, y, max, r, g, b, a, 1f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, max, y, min, r, g, b, a, 1f, 0f, 0f, 1f, 0f);
    }

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
        addVertex(m, pose, buf, x0, y1, z0, rT, gT, bT, aT, 0, 0, 0,  1, 0);
        addVertex(m, pose, buf, x0, y1, z1, rT, gT, bT, aT, 0, 1, 0,  1, 0);
        addVertex(m, pose, buf, x1, y1, z1, rT, gT, bT, aT, 1, 1, 0,  1, 0);
        addVertex(m, pose, buf, x1, y1, z0, rT, gT, bT, aT, 1, 0, 0,  1, 0);
        addVertex(m, pose, buf, x0, y0, z1, rT, gT, bT, aT, 0, 0, 0, -1, 0);
        addVertex(m, pose, buf, x0, y0, z0, rT, gT, bT, aT, 0, 1, 0, -1, 0);
        addVertex(m, pose, buf, x1, y0, z0, rT, gT, bT, aT, 1, 1, 0, -1, 0);
        addVertex(m, pose, buf, x1, y0, z1, rT, gT, bT, aT, 1, 0, 0, -1, 0);
        addVertex(m, pose, buf, x1, y1, z0, r, g, b, a, 0, 0, 0, 0, -1);
        addVertex(m, pose, buf, x1, y0, z0, r, g, b, a, 0, 1, 0, 0, -1);
        addVertex(m, pose, buf, x0, y0, z0, r, g, b, a, 1, 1, 0, 0, -1);
        addVertex(m, pose, buf, x0, y1, z0, r, g, b, a, 1, 0, 0, 0, -1);
        addVertex(m, pose, buf, x0, y1, z1, r, g, b, a, 0, 0, 0, 0,  1);
        addVertex(m, pose, buf, x0, y0, z1, r, g, b, a, 0, 1, 0, 0,  1);
        addVertex(m, pose, buf, x1, y0, z1, r, g, b, a, 1, 1, 0, 0,  1);
        addVertex(m, pose, buf, x1, y1, z1, r, g, b, a, 1, 0, 0, 0,  1);
        addVertex(m, pose, buf, x0, y1, z0, r, g, b, a, 0, 0, -1, 0, 0);
        addVertex(m, pose, buf, x0, y0, z0, r, g, b, a, 0, 1, -1, 0, 0);
        addVertex(m, pose, buf, x0, y0, z1, r, g, b, a, 1, 1, -1, 0, 0);
        addVertex(m, pose, buf, x0, y1, z1, r, g, b, a, 1, 0, -1, 0, 0);
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
        return true;
    }

    /**
     * Per-frame snapshot of the press's animated overlay state.
     *
     * <p>Carries the selected part type, the closed/open animation flag, and either a resolved
     * input item render state or the output part's tinted-quad metadata.
     */
    public static final class RenderState extends BlockEntityRenderState {
        public boolean closed = false;
        public @Nullable ResourceLocation selectedPartTypeId;
        public boolean hasInputItem = false;
        public final ItemStackRenderState inputItem  = new ItemStackRenderState();
        /** True when the finished output part should be drawn as a flat tinted quad. */
        public boolean hasOutputPart = false;
        public @Nullable ResourceLocation outputPartTypeId;
        public int outputPartColorArgb = 0xFFFFFFFF;
    }
}
