package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Shared geometry for the tinted molten cuboids Smithery draws inside its machines — the fluid in a
 * pipe's core and arms, and the pour stream falling from a spout into a casting table or basin.
 *
 * <p>Kept in one place so the stream leaving a pipe and the stream entering the block below sample
 * the sprite the same way and read as one continuous flow rather than two columns that happen to
 * touch.
 */
public final class MoltenColumn {

    private MoltenColumn() {}

    /** Shared greyscale still sprite every molten fluid tints. */
    public static final ResourceLocation MOLTEN_STILL_SPRITE =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "block/molten_still");

    /** Molten metal is its own light source; these cuboids ignore the neighbouring block light. */
    public static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Texels per block, as a multiple of the sprite's native 16/block. At 1.0 the thin arm
     * cuboids sample only ~3 texels and read as flat colour; 2.4 is the densest uniform
     * scale whose longest pipe face (6.5/16 block) still fits inside the sprite.
     *
     * <p>Every face drawn here is short enough to hold that density — streams are tiled in
     * {@link #MAX_SEGMENT} lengths rather than stretched. {@link #face} still scales a longer face
     * down to fit as a backstop, because sampling past the sprite neither clamps nor wraps: it reads
     * whatever sprite the atlas packed next door.
     */
    private static final float UV_SCALE = 2.4f;

    /**
     * Sides of the pipe bore, and so of the stream that leaves it. The pour stream reuses the
     * pipe's own width so the two columns line up exactly where they meet.
     */
    public static final float BORE_MIN = 6.5f / 16f;
    /** Far side of the pipe bore. See {@link #BORE_MIN}. */
    public static final float BORE_MAX = 9.5f / 16f;

    /** Resolves the shared molten sprite from the block atlas. */
    public static TextureAtlasSprite sprite() {
        return Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(MOLTEN_STILL_SPRITE);
    }

    /**
     * Longest run that still fits one copy of the sprite at {@link #UV_SCALE}. A stream is tiled in
     * lengths of this rather than stretched over its whole drop, so its texels stay exactly the size
     * of the pipe's however far the metal falls — including from heights no spout reaches yet.
     */
    private static final float MAX_SEGMENT = 1f / UV_SCALE;

    /**
     * Draws a falling stream in the pipe bore's footprint, spanning the given heights.
     *
     * <p>Only the four sides are emitted: the top is under a spout and the bottom is either the
     * surface it is landing on or the mould it is filling, so a cap at either end would only
     * z-fight with what it meets.
     *
     * <p>Tiled downward from the spout so that any part-length segment lands at the bottom, where
     * the pool it is falling into hides the cut, rather than at the spout where the eye follows the
     * flow out.
     *
     * @param yTop    height the stream leaves the spout at, in block-local units
     * @param yBottom height of the surface it lands on
     * @param color   ARGB tint, alpha included
     */
    public static void drawPourStream(PoseStack.Pose pose, VertexConsumer buf, TextureAtlasSprite sprite,
                                      float yTop, float yBottom, int color) {
        float y = yTop;
        while (y - yBottom > 1.0e-4f) {
            float segmentBottom = Math.max(yBottom, y - MAX_SEGMENT);
            drawSides(pose, buf, sprite, BORE_MIN, segmentBottom, BORE_MIN, BORE_MAX, y, BORE_MAX, color);
            y = segmentBottom;
        }
    }

    /** Draws all six faces of a tinted molten cuboid. */
    public static void drawCuboid(PoseStack.Pose pose, VertexConsumer buf, TextureAtlasSprite sprite,
                                  float x0, float y0, float z0,
                                  float x1, float y1, float z1,
                                  int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        drawSides(pose, buf, sprite, x0, y0, z0, x1, y1, z1, color);
        face(m, pose, buf, sprite, x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1, r, g, b, a, 0f, -1f, 0f);
        face(m, pose, buf, sprite, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0, r, g, b, a, 0f,  1f, 0f);
    }

    /** Draws the four vertical faces of a tinted molten cuboid, leaving the top and bottom open. */
    public static void drawSides(PoseStack.Pose pose, VertexConsumer buf, TextureAtlasSprite sprite,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        face(m, pose, buf, sprite, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0, r, g, b, a, -1f, 0f, 0f);
        face(m, pose, buf, sprite, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1, r, g, b, a,  1f, 0f, 0f);
        face(m, pose, buf, sprite, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, 0f, 0f, -1f);
        face(m, pose, buf, sprite, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, 0f, 0f,  1f);
    }

    /**
     * Emits one quad with sprite UVs anchored at the face's min corner and scaled to fit inside the
     * sprite. Uniform scale in both axes keeps the sprite undistorted; per-face anchoring plus the
     * fit-to-longest-side clamp keeps every sample within the sprite's own atlas region, which is
     * what stops a long face from bleeding into whatever texture the atlas packed beside it.
     */
    private static void face(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf, TextureAtlasSprite sprite,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int r, int g, int b, int a,
                             float nx, float ny, float nz) {
        float[] xs = {x0, x1, x2, x3};
        float[] ys = {y0, y1, y2, y3};
        float[] zs = {z0, z1, z2, z3};
        float[] us = new float[4];
        float[] vs = new float[4];
        float uBase = Float.MAX_VALUE;
        float vBase = Float.MAX_VALUE;
        float uMax = -Float.MAX_VALUE;
        float vMax = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            us[i] = nx != 0f ? zs[i] : xs[i];
            vs[i] = ny != 0f ? zs[i] : ys[i];
            uBase = Math.min(uBase, us[i]);
            vBase = Math.min(vBase, vs[i]);
            uMax = Math.max(uMax, us[i]);
            vMax = Math.max(vMax, vs[i]);
        }

        // One scale for both axes so the sprite stays square, dropped below UV_SCALE only when the
        // face is long enough that the preferred density would run off the end of the sprite.
        float longestSide = Math.max(uMax - uBase, vMax - vBase);
        float scale = longestSide > 0f ? Math.min(UV_SCALE, 1f / longestSide) : UV_SCALE;

        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float dU = sprite.getU1() - u0;
        float dV = sprite.getV1() - v0;
        for (int i = 0; i < 4; i++) {
            float u = u0 + dU * (us[i] - uBase) * scale;
            float v = v0 + dV * (vs[i] - vBase) * scale;
            addVertex(m, pose, buf, xs[i], ys[i], zs[i], r, g, b, a, u, v, nx, ny, nz);
        }
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
