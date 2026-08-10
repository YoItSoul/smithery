package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.block.entity.CastingBasinBlockEntity;
import com.soul.smithery.block.entity.CastingBasinBlockEntity.State;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Block entity renderer for the casting basin.
 *
 * <p>Draws the pool inside the basin while it fills and cools — the fluid's own atlas sprite,
 * tinted from the material's molten colour toward its solid part colour as the cast sets — then
 * swaps to the finished block item, shrunk to sit inside the rim, once the cast is READY.
 *
 * <p>While metal is actually arriving it also draws the stream falling from the spout above down
 * onto the rising surface, so the flow reads as continuous from the pipe into the pool instead of
 * the level climbing on its own.
 */
public class CastingBasinRenderer implements BlockEntityRenderer<CastingBasinBlockEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Inner face of the basin walls. */
    private static final float INNER_MIN = 1f / 16f;
    private static final float INNER_MAX = 15f / 16f;
    /** Top of the basin floor — where the pool starts. */
    private static final float POOL_BOTTOM = 3f / 16f;
    /** Pool surface at a full fill, one pixel below the rim so the lip stays visible. */
    private static final float POOL_TOP = 15f / 16f;

    /** Edge length of the finished block sitting in the basin. */
    private static final float CAST_BLOCK_SIZE = 12f / 16f;

    private static final ResourceLocation MOLTEN_STILL =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "block/molten_still");
    private static final ResourceLocation WATER_STILL =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public CastingBasinRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CastingBasinBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        State castState = be.state();
        if (castState == State.EMPTY) return;

        if (castState == State.READY) {
            renderFinishedBlock(be, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        renderPool(be, poseStack, bufferSource);
    }

    private static void renderFinishedBlock(CastingBasinBlockEntity be, PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            int packedLight, int packedOverlay) {
        ItemStack cast = be.peekBlockItem();
        if (cast.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5f, POOL_BOTTOM + CAST_BLOCK_SIZE / 2f, 0.5f);
        // The FIXED display transform already halves a block model, so the scale that yields an
        // edge of CAST_BLOCK_SIZE is twice it — the same compensation CastingTableRenderer makes.
        poseStack.scale(CAST_BLOCK_SIZE * 2f, CAST_BLOCK_SIZE * 2f, CAST_BLOCK_SIZE * 2f);
        Minecraft.getInstance().getItemRenderer().renderStatic(cast, ItemDisplayContext.FIXED,
                packedLight, packedOverlay, poseStack, bufferSource, be.getLevel(), 0);
        poseStack.popPose();
    }

    private static void renderPool(CastingBasinBlockEntity be, PoseStack poseStack,
                                   MultiBufferSource bufferSource) {
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(be.pouredFluid());
        if (entry == null) return;

        float fill = be.fillFraction();
        if (fill <= 0f) return;
        float ySurface = POOL_BOTTOM + (POOL_TOP - POOL_BOTTOM) * fill;

        MaterialStats stats = entry.material.stats();
        boolean waterBase = stats.fluidBase() == MaterialStats.FluidBase.WATER;
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(waterBase ? WATER_STILL : MOLTEN_STILL);

        int moltenArgb = stats.moltenColor() | 0xFF000000;
        // Molten while filling, fading to the solid part colour over the cooling countdown.
        int color = lerpArgb(stats.partColor() | 0xFF000000, moltenArgb, be.coolingFraction());

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(sprite.atlasLocation()));
        drawColumn(poseStack.last(), buffer,
                sprite, INNER_MIN, POOL_BOTTOM, INNER_MIN, INNER_MAX, ySurface, INNER_MAX, color);

        // The falling stream, from the spout in the block above down to the surface it is raising.
        // Always fully molten — this metal has not had a chance to cool yet, whatever the pool below
        // is doing — and drawn in the pipe's own bore footprint so the two columns line up.
        if (be.isPouring()) {
            MoltenColumn.drawPourStream(poseStack.last(), buffer, MoltenColumn.sprite(),
                    1.0f, ySurface, moltenArgb);
        }
    }

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

    /**
     * Draws the pool as a box, UV-mapped so the animated sprite tiles at world scale rather than
     * stretching with the fill level. The bottom face is skipped — the basin floor is under it.
     */
    private static void drawColumn(PoseStack.Pose pose, VertexConsumer buf,
                                   TextureAtlasSprite sprite,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        final float spriteU0 = sprite.getU0();
        final float spriteV0 = sprite.getV0();
        final float dU = sprite.getU1() - spriteU0;
        final float dV = sprite.getV1() - spriteV0;

        final float uXmax = spriteU0 + dU * (x1 - x0);
        final float uZmax = spriteU0 + dU * (z1 - z0);
        final float vYmax = spriteV0 + dV * (y1 - y0);
        final float vZmax = spriteV0 + dV * (z1 - z0);

        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0,
                r, g, b, a, spriteU0, uZmax, spriteV0, vYmax, -1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1,
                r, g, b, a, spriteU0, uZmax, spriteV0, vYmax,  1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0,
                r, g, b, a, spriteU0, uXmax, spriteV0, vYmax, 0f, 0f, -1f);
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1,
                r, g, b, a, spriteU0, uXmax, spriteV0, vYmax, 0f, 0f,  1f);
        face(m, pose, buf, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0,
                r, g, b, a, spriteU0, uXmax, spriteV0, vZmax, 0f,  1f, 0f);
    }

    private static void face(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int r, int g, int b, int a,
                             float uMin, float uMax, float vMin, float vMax,
                             float nx, float ny, float nz) {
        addVertex(m, pose, buf, x0, y0, z0, r, g, b, a, uMin, vMin, nx, ny, nz);
        addVertex(m, pose, buf, x1, y1, z1, r, g, b, a, uMax, vMin, nx, ny, nz);
        addVertex(m, pose, buf, x2, y2, z2, r, g, b, a, uMax, vMax, nx, ny, nz);
        addVertex(m, pose, buf, x3, y3, z3, r, g, b, a, uMin, vMax, nx, ny, nz);
    }

    private static void addVertex(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                                  float x, float y, float z,
                                  int r, int g, int b, int a,
                                  float u, float v,
                                  float nx, float ny, float nz) {
        buf.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }
}
