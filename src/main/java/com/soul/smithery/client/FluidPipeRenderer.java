package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.block.FluidPipeFaceVisual;
import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Block entity renderer for fluid pipes.
 *
 * <p>Draws a tinted molten cube inside the pipe's central hollow whenever the pipe has
 * recently transported fluid. The cube tints the shared molten block-atlas sprite by the
 * material's molten colour, fading via the pipe's intensity ticks; arm cuboids extend
 * into each connected face so the flow reads as continuous along the network.
 */
public class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeBlockEntity> {

    private static final ResourceLocation MOLTEN_STILL_SPRITE =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "block/molten_still");

    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Texels per block, as a multiple of the sprite's native 16/block. At 1.0 the thin arm
     * cuboids sample only ~3 texels and read as flat colour; 2.4 is the densest uniform
     * scale whose longest pipe face (6.5/16 block) still fits inside the sprite.
     */
    private static final float UV_SCALE = 2.4f;

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public FluidPipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FluidPipeBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ResourceLocation fluidId = be.transientFluidId();
        if (fluidId == null) return;

        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) return;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        if (entry == null) return;

        int rgb = entry.material.stats().moltenColor() & 0xFFFFFF;
        float fade = (float) be.intensityTicks() / (float) FluidPipeBlockEntity.FLOW_PERSIST_TICKS;
        int alpha = Math.max(0, Math.min(255, (int) (fade * 255f)));
        if (alpha <= 0) return;
        int color = (alpha << 24) | rgb;

        boolean[] armConnected = new boolean[6];
        BlockState blockState = be.getBlockState();
        if (blockState.getBlock() instanceof FluidPipeBlock) {
            for (Direction dir : Direction.values()) {
                FluidPipeFaceVisual v = blockState.getValue(FluidPipeBlock.propertyFor(dir));
                armConnected[dir.get3DDataValue()] = v != FluidPipeFaceVisual.NONE;
            }
        }

        final float lo = 6.5f / 16f;
        final float hi = 9.5f / 16f;
        final float armNear = 0f;
        final float armFar  = 6.5f / 16f;
        final float armFar2 = 9.5f / 16f;
        final float armEnd  = 16f / 16f;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(MOLTEN_STILL_SPRITE);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(sprite.atlasLocation()));
        PoseStack.Pose pose = poseStack.last();

        drawCuboid(pose, buffer, sprite, lo, lo, lo,  hi, hi, hi, color);

        if (armConnected[Direction.NORTH.get3DDataValue()])
            drawCuboid(pose, buffer, sprite, lo, lo, armNear,  hi, hi, armFar, color);
        if (armConnected[Direction.SOUTH.get3DDataValue()])
            drawCuboid(pose, buffer, sprite, lo, lo, armFar2,  hi, hi, armEnd, color);
        if (armConnected[Direction.WEST.get3DDataValue()])
            drawCuboid(pose, buffer, sprite, armNear, lo, lo,  armFar, hi, hi, color);
        if (armConnected[Direction.EAST.get3DDataValue()])
            drawCuboid(pose, buffer, sprite, armFar2, lo, lo,  armEnd, hi, hi, color);
        if (armConnected[Direction.DOWN.get3DDataValue()])
            drawCuboid(pose, buffer, sprite, lo, armNear, lo,  hi, armFar, hi, color);
        if (armConnected[Direction.UP.get3DDataValue()])
            drawCuboid(pose, buffer, sprite, lo, armFar2, lo,  hi, armEnd, hi, color);
    }

    private static void drawCuboid(PoseStack.Pose pose, VertexConsumer buf, TextureAtlasSprite sprite,
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
        face(m, pose, buf, sprite, x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1, r, g, b, a, 0f, -1f, 0f);
        face(m, pose, buf, sprite, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0, r, g, b, a, 0f,  1f, 0f);
        face(m, pose, buf, sprite, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, 0f, 0f, -1f);
        face(m, pose, buf, sprite, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, 0f, 0f,  1f);
    }

    /**
     * Emits one quad with sprite UVs anchored at the face's min corner and scaled by
     * {@link #UV_SCALE}. Uniform scale in both axes keeps the sprite undistorted on the
     * narrow arm cuboids; per-face anchoring keeps every sample inside the sprite.
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
        for (int i = 0; i < 4; i++) {
            us[i] = nx != 0f ? zs[i] : xs[i];
            vs[i] = ny != 0f ? zs[i] : ys[i];
            uBase = Math.min(uBase, us[i]);
            vBase = Math.min(vBase, vs[i]);
        }
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float dU = sprite.getU1() - u0;
        float dV = sprite.getV1() - v0;
        for (int i = 0; i < 4; i++) {
            float u = u0 + dU * (us[i] - uBase) * UV_SCALE;
            float v = v0 + dV * (vs[i] - vBase) * UV_SCALE;
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
