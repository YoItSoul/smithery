package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.block.entity.PartPressBlockEntity;
import com.soul.smithery.item.PartItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Optional;

/**
 * Block entity renderer for the part press.
 *
 * <p>Renders the animated geckolib chassis (head, base, legs) and overlays:
 * <ul>
 *   <li>Per-cell tooth voxels filling the 12x12 grid wherever the selected
 *       {@link PartType} is transparent — top teeth attached to the animated head bone,
 *       bottom teeth attached to the static base.</li>
 *   <li>A 12x12 template quad showing the selected part's greyscale texture above the head,
 *       visible only when the press has fully reopened.</li>
 *   <li>Input item (during press cycle) or finished output part (after the press completes)
 *       on top of the teeth.</li>
 * </ul>
 */
public class PartPressRenderer extends GeoBlockRenderer<PartPressBlockEntity> {

    private static final ResourceLocation MODEL_ID =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "part_press");

    private static final int TOOTH_COLOR = 0xFFFFFFFF;
    private static final int TOOTH_SHADE = 0xFFC0C0C0;

    private static final float TOOTH_H_PX_MAX = 1f;
    private static final float TOOTH_H_MIN_PX = 0.05f;

    private static final ResourceLocation TOOTH_TEXTURE =
            ResourceLocation.parse("textures/block/iron_block.png");

    private static final float HEAD_CLOSED_Y = -10f;

    /**
     * Constructs the renderer with the provider context.
     *
     * @param ctx renderer provider context (unused; geckolib drives the model)
     */
    public PartPressRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new DefaultedBlockGeoModel<>(MODEL_ID));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Draws the tooth grid, template quad and slot items after the chassis. Geometry is
     * authored in block-local [0,1] coordinates, so the GeoBlockRenderer's center translation
     * is undone first.
     */
    @Override
    public void renderFinal(PoseStack poseStack, PartPressBlockEntity be, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, float red, float green, float blue,
                            float alpha) {
        super.renderFinal(poseStack, be, model, bufferSource, buffer, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);

        PartType selected = be.selectedPartType();
        if (selected == null) return;

        poseStack.pushPose();
        poseStack.translate(-0.5f, -0.01f, -0.5f);

        boolean closed = be.isClosed();
        float headY = readHeadTranslateY(model);
        boolean fullyClosed = closed && headY <= HEAD_CLOSED_Y + 0.05f;

        float[][] alphaGrid = PartSilhouetteCache.forPartTypeId(selected.id());

        float headOffsetBlocks = headY / 16f;

        if (!fullyClosed) {
            VertexConsumer teeth = bufferSource.getBuffer(RenderType.entitySolid(TOOTH_TEXTURE));
            drawTeeth(poseStack.last(), teeth, alphaGrid, TOOTH_COLOR, TOOTH_SHADE);
        }

        final float itemY = (1f / 16f) + (TOOTH_H_PX_MAX / 16f) + (1f / 64f);
        boolean midClosingAnim = closed && !fullyClosed;
        ItemStack output = be.outputItem();
        if (!output.isEmpty() && output.getItem() instanceof PartItem pi && !midClosingAnim) {
            PartType outPt = SmitheryAPI.PART_TYPES.get(pi.partTypeId());
            if (outPt != null && outPt.textureTemplate() != null) {
                ResourceLocation tmpl = outPt.textureTemplate();
                ResourceLocation texLoc = ResourceLocation.fromNamespaceAndPath(
                        tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
                Material mat = SmitheryAPI.MATERIALS.get(pi.materialId());
                int tint = (mat != null ? mat.stats().partColor() : 0xFFFFFF) | 0xFF000000;
                VertexConsumer quad = bufferSource.getBuffer(RenderType.entityTranslucent(texLoc));
                drawTemplateQuad(poseStack.last(), quad, itemY, tint);
            }
        } else if (!be.inputItem().isEmpty() && !fullyClosed) {
            poseStack.pushPose();
            poseStack.translate(0.5f, itemY, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.inputItem(),
                    ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, bufferSource, be.getLevel(), 0);
            poseStack.popPose();
        }

        boolean fullyOpen = !closed && headY >= -0.05f;
        if (fullyOpen && selected.textureTemplate() != null) {
            ResourceLocation tmpl = selected.textureTemplate();
            ResourceLocation texLoc = ResourceLocation.fromNamespaceAndPath(
                    tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
            final float topY = (15f / 16f) + headOffsetBlocks + (1f / 256f);
            VertexConsumer quad = bufferSource.getBuffer(RenderType.entityTranslucent(texLoc));
            drawTemplateQuad(poseStack.last(), quad, topY, 0xFFFFFFFF);
        }

        poseStack.popPose();
    }

    private static float readHeadTranslateY(BakedGeoModel model) {
        Optional<GeoBone> head = model.getBone("head");
        return head.map(GeoBone::getPosY).orElse(0f);
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
        buf.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(PartPressBlockEntity be) {
        return true;
    }
}
