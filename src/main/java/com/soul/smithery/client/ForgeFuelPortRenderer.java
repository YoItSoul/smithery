package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.ForgeFuelPortBlock;
import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * BER for the Forge Fuel Port (fuel storage block). Draws a tinted fluid column inside the
 * block whose height tracks the current {@code fuelMb / CAPACITY_MB} fraction, lerped per
 * frame so adding/removing fuel reads as a smooth rise/fall rather than a hard step.
 *
 * <p>Smoothing pipeline:
 * <ol>
 *   <li>Each frame, {@link #extractRenderState} captures the BE's target fraction.</li>
 *   <li>An eased lerp (~12% per frame, framerate-independent enough for player perception)
 *       walks the displayed fraction toward the target. Lasts roughly 0.5s to settle.</li>
 *   <li>Lerped fraction → cuboid height; cuboid is drawn with the fluid's still atlas
 *       texture + per-frame V clamp matching the {@link FluidPipeRenderer} approach.</li>
 * </ol>
 *
 * <p>The renderer keeps a tiny per-BE displayed-fraction cache so each block animates
 * independently — necessary because RenderState is recreated each frame and can't itself
 * carry persistent state between frames.
 */
public class ForgeFuelPortRenderer
        implements BlockEntityRenderer<ForgeFuelPortBlockEntity, ForgeFuelPortRenderer.RenderState> {

    private static final Identifier MOLTEN_STILL_TEXTURE =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "textures/block/molten_still.png");

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Fluid column horizontal inset (one pixel from each side of the block). */
    private static final float INSET = 1f / 16f;

    /** matches FluidPipeRenderer's frame cadence so flows look consistent across blocks. */
    private static final int  FRAME_COUNT       = 16;
    private static final long FRAME_DURATION_MS = 150L;
    private static final float FRAME_V_STEP     = 1f / FRAME_COUNT;

    /** Eased lerp factor per frame for fill-level smoothing. ~0.5s to settle. */
    private static final float LERP_FACTOR = 0.12f;

    /** Per-BE displayed fraction cache, keyed by hashed BlockPos. Tiny working set in practice. */
    private final java.util.Map<Long, Float> displayedByPos = new java.util.HashMap<>();

    public ForgeFuelPortRenderer(BlockEntityRendererProvider.Context context) {}

    public static final class RenderState extends BlockEntityRenderState {
        public boolean hasFuel;
        public int colorArgb;
        public float fillFraction;   // 0..1, lerped
        // Open-cap flags drive whether the top/bottom face of the fluid column is drawn.
        // When a fuel port sits above/below, those faces are hidden so the fluid reads as
        // a continuous column across the stack.
        public boolean openTop;
        public boolean openBottom;
    }

    /** Dark gray for the empty portion of the tank's interior. Tinted over the molten_still
     *  texture; the subtle grayscale animation reads as residual heat shimmering inside a
     *  metal tank, while keeping the interior opaque so the cutout side textures don't reveal
     *  the world behind the block. */
    private static final int EMPTY_INTERIOR_ARGB = 0xFF221A14;

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(ForgeFuelPortBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        captureCapFlags(be, state);

        // The renderer always draws the dark tank interior so the cutout side textures
        // don't let the player see straight through the block. hasFuel here governs only
        // whether the fluid column overlay is also drawn this frame.
        state.hasFuel = false;
        Fluid fluid = be.fuelFluid();
        long key = be.getBlockPos().asLong();

        if (fluid == null || be.fuelMb() <= 0) {
            float prev = displayedByPos.getOrDefault(key, 0f);
            float lerped = prev + (0f - prev) * LERP_FACTOR;
            if (lerped < 0.001f) {
                displayedByPos.remove(key);
                state.fillFraction = 0f;
                return;
            }
            displayedByPos.put(key, lerped);
            state.fillFraction = lerped;
            state.colorArgb = 0xFFFF6622; // lava-ish fallback while decay animates down
            state.hasFuel = true;
            return;
        }

        float target = Math.max(0f, Math.min(1f,
                (float) be.fuelMb() / (float) ForgeFuelPortBlockEntity.CAPACITY_MB));
        float prev = displayedByPos.getOrDefault(key, target);
        float lerped = prev + (target - prev) * LERP_FACTOR;
        displayedByPos.put(key, lerped);

        state.fillFraction = lerped;
        state.colorArgb = colorForFluid(fluid);
        state.hasFuel = true;
    }

    private static void captureCapFlags(ForgeFuelPortBlockEntity be, RenderState state) {
        net.minecraft.world.level.block.state.BlockState bs = be.getBlockState();
        if (bs.getBlock() instanceof ForgeFuelPortBlock) {
            state.openTop    = bs.getValue(ForgeFuelPortBlock.CONNECTED_UP);
            state.openBottom = bs.getValue(ForgeFuelPortBlock.CONNECTED_DOWN);
        }
    }

    /** Picks an ARGB tint for the given fuel fluid. Lava + smithery molten metals supported;
     *  any other fluid falls back to a generic orange so unknown fuels still read as "hot".
     *  Full alpha — the fluid overlay needs to fully cover the dark interior backdrop. */
    private static int colorForFluid(Fluid fluid) {
        if (fluid == Fluids.LAVA) return 0xFFFF6622;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        if (entry != null) {
            int rgb = entry.material.stats().moltenColor() & 0xFFFFFF;
            return 0xFF000000 | rgb;
        }
        return 0xFFFF8844;
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        final float x0 = INSET;
        final float x1 = 1f - INSET;
        final float z0 = INSET;
        final float z1 = 1f - INSET;
        final float yBottom = INSET;
        final float yTop    = 1f - INSET;
        final float yFluid  = state.hasFuel
                ? yBottom + (yTop - yBottom) * Math.max(0f, Math.min(1f, state.fillFraction))
                : yBottom;

        final int frame = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % FRAME_COUNT);
        final float vMin = frame * FRAME_V_STEP;
        final float vMax = (frame + 1) * FRAME_V_STEP;
        final int fluidColor = state.colorArgb;
        final boolean openTop    = state.openTop;
        final boolean openBottom = state.openBottom;

        // One submit, two stacked cuboids. Single render type (entitySolid) means the GPU
        // batches both into one draw call. The dark backdrop fills above the fluid level
        // (or the whole interior when empty), so the cutout side textures always see an
        // opaque "tank interior" instead of revealing the world through the block.
        collector.submitCustomGeometry(poseStack,
                RenderTypes.entitySolid(MOLTEN_STILL_TEXTURE),
                (pose, buffer) -> {
                    // Empty/upper portion — dark tank interior.
                    if (yFluid < yTop) {
                        drawColumn(pose, buffer,
                                x0, yFluid, z0, x1, yTop, z1,
                                EMPTY_INTERIOR_ARGB, vMin, vMax, openTop, false);
                    }
                    // Fluid portion — drawn below the empty portion. Bottom face of fluid
                    // is omitted when stacked downward (continuous flow into block below).
                    if (yFluid > yBottom) {
                        drawColumn(pose, buffer,
                                x0, yBottom, z0, x1, yFluid, z1,
                                fluidColor, vMin, vMax,
                                /* openTop  = */ false,  // top face of fluid is the surface
                                /* openBottom = */ openBottom);
                    }
                });
    }

    /** 4-sided fluid column. Top/bottom faces are emitted only when the corresponding cap is
     *  closed (no neighboring fuel port); otherwise the column reads as continuous. */
    private static void drawColumn(PoseStack.Pose pose, VertexConsumer buf,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   int color, float vMin, float vMax,
                                   boolean openTop, boolean openBottom) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        // 4 side faces — always rendered.
        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0, r, g, b, a, vMin, vMax, -1f, 0f, 0f); // -X
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1, r, g, b, a, vMin, vMax,  1f, 0f, 0f); // +X
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, vMin, vMax, 0f, 0f, -1f); // -Z
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, vMin, vMax, 0f, 0f,  1f); // +Z

        // Bottom face only when sealed (no fuel port below).
        if (!openBottom) {
            face(m, pose, buf, x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1,
                    r, g, b, a, vMin, vMax, 0f, -1f, 0f);
        }
        // Top face only when sealed (no fuel port above) — so vertical stacks merge visually.
        if (!openTop) {
            face(m, pose, buf, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0,
                    r, g, b, a, vMin, vMax, 0f,  1f, 0f);
        }
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
