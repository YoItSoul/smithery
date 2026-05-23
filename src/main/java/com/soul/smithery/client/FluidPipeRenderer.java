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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * BER for fluid pipes. Reads the BE's transient flow state and draws a tinted cube in
 * the centre of the pipe whenever the drain has recently pumped through this pipe.
 *
 * Render type is {@code entityTranslucent} bound to {@code smithery:textures/block/molten_still.png}
 * — that texture is greyscale, so tinting the vertex colours by the material's molten
 * colour gives a proper "molten metal" look. Alpha fades with the BE's intensityTicks,
 * so when the drain stops pumping the cube smoothly fades to invisible over ~1 second.
 *
 * Cube extent: 6.5/16 to 9.5/16 in block coords, sitting just inside the pipe's 6..10
 * centre geometry. The pipe block model needs render_type=cutout (set in the model JSONs)
 * for its alpha-zero centre pixels to actually punch through and reveal the cube.
 */
public class FluidPipeRenderer
        implements BlockEntityRenderer<FluidPipeBlockEntity, FluidPipeRenderer.RenderState> {

    private static final Identifier MOLTEN_STILL_TEXTURE =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "textures/block/molten_still.png");

    /**
     * The molten_still.png is a 16-frame animation strip (32×512). We sample one frame at a
     * time by limiting V to (frame/16)..(frame+1)/16, then advance the frame on a 150 ms
     * cadence (matches the .mcmeta animation rate). Without this clamp/animation, UV (0..1)
     * stretches all 16 frames across each face — the "hundreds of layers squished" artifact.
     */
    private static final int FRAME_COUNT = 16;
    private static final long FRAME_DURATION_MS = 150L;
    private static final float FRAME_V_STEP = 1f / FRAME_COUNT;

    private static final int FULL_BRIGHT = 0xF000F0;

    public static final class RenderState extends BlockEntityRenderState {
        public boolean hasFlow;
        public int colorArgb;
        /** Per-face flag (index = Direction.get3DDataValue()): true if that arm geometry is rendered. */
        public final boolean[] armConnected = new boolean[6];
    }

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

        Identifier fluidId = be.transientFluidId();
        if (fluidId == null) return;

        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId).<Fluid>map(r -> r.value()).orElse(null);
        if (fluid == null) return;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        if (entry == null) return;

        // Alpha follows the marker's decay timer: full alpha while the drain is refreshing
        // us, fading out smoothly once the wave has moved past / drain has stopped.
        int rgb = entry.material.stats().moltenColor() & 0xFFFFFF;
        float fade = (float) be.intensityTicks() / (float) FluidPipeBlockEntity.FLOW_PERSIST_TICKS;
        int alpha = Math.max(0, Math.min(255, (int) (fade * 255f)));
        state.colorArgb = (alpha << 24) | rgb;
        state.hasFlow = alpha > 0;

        // Capture per-face connection so we know which arms should also have fluid drawn.
        // Any non-NONE face has arm geometry; we render fluid inside.
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
        // Centre cuboid: 3×3×3 just inside the pipe's 6..10 core. The pipe model uses
        // cutout render type so the alpha-zero centre texture pixels reveal this cube.
        // Each connected arm gets a matching 3×3×6 cuboid extending toward the block edge
        // so molten flow appears to travel down the pipe arms instead of stopping at the core.
        final float lo = 6.5f / 16f;
        final float hi = 9.5f / 16f;
        // Arm cuboids extend from 0..6 (or 10..16) for the connected axis, kept at
        // [lo, hi] cross-section. We push them just past 6 / pull just before 10 so they
        // overlap the core cube and read as one continuous flow.
        final float armNear = 0f;
        final float armFar  = 6.5f / 16f;
        final float armFar2 = 9.5f / 16f;
        final float armEnd  = 16f / 16f;

        // Active animation frame from the molten_still strip — wraps every 16 × 150 ms = 2.4 s.
        final int frame = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % FRAME_COUNT);
        final float vMin = frame * FRAME_V_STEP;
        final float vMax = (frame + 1) * FRAME_V_STEP;

        collector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(MOLTEN_STILL_TEXTURE),
                (pose, buffer) -> {
                    drawCuboid(pose, buffer, lo, lo, lo,  hi, hi, hi, color, vMin, vMax); // core

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

    // ---- Cuboid emission ----

    /** Draws a 6-face cuboid with corners at (x0,y0,z0) and (x1,y1,z1), tinted, with UV V clamped
     *  to {@code [vMin, vMax]} — the caller picks which animation frame of the strip to sample. */
    private static void drawCuboid(PoseStack.Pose pose, VertexConsumer buf,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    int color, float vMin, float vMax) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        // 6 faces, vertices in CCW winding when viewed from outside the cuboid.
        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0, r, g, b, a, vMin, vMax, -1f, 0f, 0f); // -X
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1, r, g, b, a, vMin, vMax,  1f, 0f, 0f); // +X
        face(m, pose, buf, x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1, r, g, b, a, vMin, vMax, 0f, -1f, 0f); // -Y
        face(m, pose, buf, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0, r, g, b, a, vMin, vMax, 0f,  1f, 0f); // +Y
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, vMin, vMax, 0f, 0f, -1f); // -Z
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, vMin, vMax, 0f, 0f,  1f); // +Z
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
