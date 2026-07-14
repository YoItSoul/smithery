package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.block.FluidPipeFaceVisual;
import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Block entity renderer for fluid pipes.
 *
 * <p>Draws a tinted molten cube inside the pipe's central hollow whenever the pipe has
 * recently transported fluid. The cube tints a shared greyscale strip texture by the
 * material's molten colour, fading via the pipe's intensity ticks; arm cuboids extend
 * into each connected face so the flow reads as continuous along the network.
 */
public class FluidPipeRenderer
        implements BlockEntityRenderer<FluidPipeBlockEntity, FluidPipeRenderer.RenderState> {

    private static final ResourceLocation MOLTEN_STILL_TEXTURE =
            new ResourceLocation(Smithery.MODID, "textures/block/molten_still.png");

    private static final int FRAME_COUNT = 16;
    private static final long FRAME_DURATION_MS = 150L;
    private static final float FRAME_V_STEP = 1f / FRAME_COUNT;

    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Snapshot of pipe flow state captured per frame.
     *
     * <p>Carries the lerped molten tint plus a per-face flag array indicating which arms
     * have geometry so matching fluid cuboids can be emitted alongside the core.
     */
    public static final class RenderState extends BlockEntityRenderState {
        public boolean hasFlow;
        public int colorArgb;
        /** Per-face connection flags indexed by {@link Direction#get3DDataValue()}. */
        public final boolean[] armConnected = new boolean[6];
    }

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public FluidPipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(FluidPipeBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        state.hasFlow = false;

        ResourceLocation fluidId = be.transientFluidId();
        if (fluidId == null) return;

        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId).<Fluid>map(r -> r.value()).orElse(null);
        if (fluid == null) return;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        if (entry == null) return;

        int rgb = entry.material.stats().moltenColor() & 0xFFFFFF;
        float fade = (float) be.intensityTicks() / (float) FluidPipeBlockEntity.FLOW_PERSIST_TICKS;
        int alpha = Math.max(0, Math.min(255, (int) (fade * 255f)));
        state.colorArgb = (alpha << 24) | rgb;
        state.hasFlow = alpha > 0;

        net.minecraft.world.level.block.state.BlockState blockState = be.getBlockState();
        if (blockState.getBlock() instanceof FluidPipeBlock) {
            for (Direction dir : Direction.values()) {
                FluidPipeFaceVisual v = blockState.getValue(FluidPipeBlock.propertyFor(dir));
                state.armConnected[dir.get3DDataValue()] = v != FluidPipeFaceVisual.NONE;
            }
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.hasFlow) return;

        final int color = state.colorArgb;
        final float lo = 6.5f / 16f;
        final float hi = 9.5f / 16f;
        final float armNear = 0f;
        final float armFar  = 6.5f / 16f;
        final float armFar2 = 9.5f / 16f;
        final float armEnd  = 16f / 16f;

        final int frame = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % FRAME_COUNT);
        final float vMin = frame * FRAME_V_STEP;
        final float vMax = (frame + 1) * FRAME_V_STEP;

        collector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(MOLTEN_STILL_TEXTURE),
                (pose, buffer) -> {
                    drawCuboid(pose, buffer, lo, lo, lo,  hi, hi, hi, color, vMin, vMax);

                    boolean[] arm = state.armConnected;
                    if (arm[Direction.NORTH.get3DDataValue()])
                        drawCuboid(pose, buffer, lo, lo, armNear,  hi, hi, armFar, color, vMin, vMax);
                    if (arm[Direction.SOUTH.get3DDataValue()])
                        drawCuboid(pose, buffer, lo, lo, armFar2,  hi, hi, armEnd, color, vMin, vMax);
                    if (arm[Direction.WEST.get3DDataValue()])
                        drawCuboid(pose, buffer, armNear, lo, lo,  armFar, hi, hi, color, vMin, vMax);
                    if (arm[Direction.EAST.get3DDataValue()])
                        drawCuboid(pose, buffer, armFar2, lo, lo,  armEnd, hi, hi, color, vMin, vMax);
                    if (arm[Direction.DOWN.get3DDataValue()])
                        drawCuboid(pose, buffer, lo, armNear, lo,  hi, armFar, hi, color, vMin, vMax);
                    if (arm[Direction.UP.get3DDataValue()])
                        drawCuboid(pose, buffer, lo, armFar2, lo,  hi, armEnd, hi, color, vMin, vMax);
                });
    }

    private static void drawCuboid(PoseStack.Pose pose, VertexConsumer buf,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    int color, float vMin, float vMax) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0, r, g, b, a, vMin, vMax, -1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1, r, g, b, a, vMin, vMax,  1f, 0f, 0f);
        face(m, pose, buf, x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1, r, g, b, a, vMin, vMax, 0f, -1f, 0f);
        face(m, pose, buf, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0, r, g, b, a, vMin, vMax, 0f,  1f, 0f);
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, vMin, vMax, 0f, 0f, -1f);
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, vMin, vMax, 0f, 0f,  1f);
    }

    private static void face(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int r, int g, int b, int a,
                             float vMin, float vMax,
                             float nx, float ny, float nz) {
        addVertex(m, pose, buf, x0, y0, z0, r, g, b, a, 0f, vMin, nx, ny, nz);
        addVertex(m, pose, buf, x1, y1, z1, r, g, b, a, 1f, vMin, nx, ny, nz);
        addVertex(m, pose, buf, x2, y2, z2, r, g, b, a, 1f, vMax, nx, ny, nz);
        addVertex(m, pose, buf, x3, y3, z3, r, g, b, a, 0f, vMax, nx, ny, nz);
    }

    private static void addVertex(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                                   float x, float y, float z,
                                   int r, int g, int b, int a,
                                   float u, float v,
                                   float nx, float ny, float nz) {
        buf.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
