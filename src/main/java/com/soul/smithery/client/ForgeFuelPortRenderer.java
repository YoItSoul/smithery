package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.ForgeFuelPortBlock;
import com.soul.smithery.block.entity.ForgeFuelPortBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity renderer for the forge fuel port.
 *
 * <p>Draws a fluid column inside the port whose height tracks the fuel fill level, lerped
 * frame-to-frame so adds and drains read as smooth rise/fall. The column samples the fluid's
 * own atlas sprite so vanilla lava and the per-material molten fluids each pick up their
 * correct still texture and animation cycle. Vertical extension toward connected neighbours
 * is lerped on a matching schedule so adjacent ports merge their fluid surfaces visually.
 */
public class ForgeFuelPortRenderer
        implements BlockEntityRenderer<ForgeFuelPortBlockEntity, ForgeFuelPortRenderer.RenderState> {

    private static final int FULL_BRIGHT = 0xF000F0;

    private static final float INSET = 1f / 16f;

    private static final float LERP_FACTOR = 0.20f;
    private static final float LERP_FACTOR_FAST = 0.55f;
    private static final float LERP_FAST_THRESHOLD = 0.05f;
    private static final float LERP_SNAP_THRESHOLD = 0.30f;

    private static float lerpStep(float prev, float target) {
        float diff = target - prev;
        float absD = Math.abs(diff);
        if (absD > LERP_SNAP_THRESHOLD) return target;
        float factor = absD > LERP_FAST_THRESHOLD ? LERP_FACTOR_FAST : LERP_FACTOR;
        return prev + diff * factor;
    }

    private final java.util.Map<Long, Float> displayedByPos = new java.util.HashMap<>();
    private final java.util.Map<Long, Fluid> lastFluidByPos = new java.util.HashMap<>();
    private final java.util.Map<Long, Float> displayedTopExtendByPos = new java.util.HashMap<>();
    private final java.util.Map<Long, Float> displayedBottomExtendByPos = new java.util.HashMap<>();

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public ForgeFuelPortRenderer(BlockEntityRendererProvider.Context context) {}

    /**
     * Per-frame snapshot of the port's fluid column state.
     *
     * <p>Holds the lerped fill fraction, vertical extension toward each neighbour, and the
     * resolved fluid sprite to texture the column with.
     */
    public static final class RenderState extends BlockEntityRenderState {
        public boolean hasFuel;
        public int colorArgb;
        public float fillFraction;
        public float topExtend;
        public float bottomExtend;
        public boolean aboveHasFuel;
        public boolean belowHasFuel;
        /** Resolved still sprite for the current fluid; UVs already track its animation frame. */
        public @Nullable TextureAtlasSprite fluidSprite;
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(ForgeFuelPortBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        lerpFluidConnections(be, state);

        state.hasFuel = false;
        state.fluidSprite = null;
        Fluid fluid = be.fuelFluid();
        long key = be.getBlockPos().asLong();

        if (fluid == null || be.fuelMb() <= 0) {
            float prev = displayedByPos.getOrDefault(key, 0f);
            float lerped = lerpStep(prev, 0f);
            if (lerped < 0.001f) {
                displayedByPos.put(key, 0f);
                lastFluidByPos.remove(key);
                state.fillFraction = 0f;
                return;
            }
            displayedByPos.put(key, lerped);
            state.fillFraction = lerped;
            Fluid lastFluid = lastFluidByPos.get(key);
            if (lastFluid != null) {
                state.colorArgb = tintForFluid(lastFluid);
                state.fluidSprite = spriteForFluid(lastFluid);
            } else {
                state.colorArgb = 0xFFFF6622;
            }
            state.hasFuel = true;
            return;
        }

        float target = Math.max(0f, Math.min(1f,
                (float) be.fuelMb() / (float) ForgeFuelPortBlockEntity.CAPACITY_MB));
        float prev = displayedByPos.getOrDefault(key, 0f);
        float lerped = lerpStep(prev, target);
        displayedByPos.put(key, lerped);
        lastFluidByPos.put(key, fluid);

        state.fillFraction = lerped;
        state.colorArgb = tintForFluid(fluid);
        state.fluidSprite = spriteForFluid(fluid);
        state.hasFuel = true;
    }

    private void lerpFluidConnections(ForgeFuelPortBlockEntity be, RenderState state) {
        boolean targetAbove = false;
        boolean targetBelow = false;
        boolean aboveHasFuel = false;
        boolean belowHasFuel = false;
        var level = be.getLevel();
        net.minecraft.world.level.block.state.BlockState bs = be.getBlockState();
        if (level != null && bs.getBlock() instanceof ForgeFuelPortBlock) {
            if (bs.getValue(ForgeFuelPortBlock.CONNECTED_UP)) {
                targetAbove = true;
                if (level.getBlockEntity(be.getBlockPos().above()) instanceof ForgeFuelPortBlockEntity above) {
                    aboveHasFuel = above.fuelMb() > 0;
                }
            }
            if (bs.getValue(ForgeFuelPortBlock.CONNECTED_DOWN)) {
                targetBelow = true;
                if (level.getBlockEntity(be.getBlockPos().below()) instanceof ForgeFuelPortBlockEntity below) {
                    belowHasFuel = below.fuelMb() > 0;
                }
            }
        }
        long key = be.getBlockPos().asLong();
        state.topExtend    = lerpExtend(displayedTopExtendByPos,    key, targetAbove ? 1f : 0f);
        state.bottomExtend = lerpExtend(displayedBottomExtendByPos, key, targetBelow ? 1f : 0f);
        state.aboveHasFuel = aboveHasFuel;
        state.belowHasFuel = belowHasFuel;
    }

    private static float lerpExtend(java.util.Map<Long, Float> cache, long key, float target) {
        float prev = cache.getOrDefault(key, 0f);
        float lerped = lerpStep(prev, target);
        cache.put(key, lerped);
        return lerped;
    }

    private static final ResourceLocation VANILLA_LAVA_STILL =
            new ResourceLocation("minecraft", "block/lava_still");
    private static final ResourceLocation SMITHERY_MOLTEN_STILL =
            new ResourceLocation(Smithery.MODID, "block/molten_still");

    private static @Nullable TextureAtlasSprite spriteForFluid(Fluid fluid) {
        ResourceLocation path;
        if (fluid == Fluids.LAVA) {
            path = VANILLA_LAVA_STILL;
        } else if (SmitheryFluids.forFluid(fluid) != null) {
            path = SMITHERY_MOLTEN_STILL;
        } else {
            return null;
        }
        return lookupSprite(path);
    }

    private static TextureAtlasSprite lookupSprite(ResourceLocation path) {
        return Minecraft.getInstance()
                .getAtlasManager()
                .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, path));
    }

    private static int tintForFluid(Fluid fluid) {
        if (fluid == Fluids.LAVA) return 0xFFFFFFFF;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        if (entry != null) {
            int rgb = entry.material.stats().moltenColor() & 0xFFFFFF;
            return 0xFF000000 | rgb;
        }
        return 0xFFFFFFFF;
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.fluidSprite;
        if (sprite == null) return;

        final float x0 = INSET;
        final float x1 = 1f - INSET;
        final float z0 = INSET;
        final float z1 = 1f - INSET;
        final float yBottom = INSET * (1f - state.bottomExtend);
        final float yTop    = 1f - INSET * (1f - state.topExtend);
        final float yFluid  = state.hasFuel
                ? yBottom + (yTop - yBottom) * Math.max(0f, Math.min(1f, state.fillFraction))
                : yBottom;
        if (yFluid <= yBottom) return;

        final boolean hideTopCap    = state.aboveHasFuel && state.topExtend > 0.95f
                && state.fillFraction > 0.95f;
        final boolean hideBottomCap = state.belowHasFuel && state.bottomExtend > 0.95f;

        collector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(sprite.atlasLocation()),
                (pose, buffer) -> drawColumn(pose, buffer, sprite,
                        x0, yBottom, z0, x1, yFluid, z1,
                        state.colorArgb,
                        hideTopCap,
                        hideBottomCap));
    }

    private static void drawColumn(PoseStack.Pose pose, VertexConsumer buf,
                                   TextureAtlasSprite sprite,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   int color,
                                   boolean openTop, boolean openBottom) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();

        final float spriteU0 = sprite.getU0();
        final float spriteU1 = sprite.getU1();
        final float spriteV0 = sprite.getV0();
        final float spriteV1 = sprite.getV1();
        final float dU = spriteU1 - spriteU0;
        final float dV = spriteV1 - spriteV0;

        final float dx = x1 - x0;
        final float dy = y1 - y0;
        final float dz = z1 - z0;

        final float uXmax = spriteU0 + dU * dx;
        final float uZmax = spriteU0 + dU * dz;
        final float vYmax = spriteV0 + dV * dy;

        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0,
                r, g, b, a, spriteU0, uZmax, spriteV0, vYmax, -1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1,
                r, g, b, a, spriteU0, uZmax, spriteV0, vYmax,  1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0,
                r, g, b, a, spriteU0, uXmax, spriteV0, vYmax, 0f, 0f, -1f);
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1,
                r, g, b, a, spriteU0, uXmax, spriteV0, vYmax, 0f, 0f,  1f);

        final float vZmax = spriteV0 + dV * dz;
        if (!openBottom) {
            face(m, pose, buf, x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1,
                    r, g, b, a, spriteU0, uXmax, spriteV0, vZmax, 0f, -1f, 0f);
        }
        if (!openTop) {
            face(m, pose, buf, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0,
                    r, g, b, a, spriteU0, uXmax, spriteV0, vZmax, 0f,  1f, 0f);
        }
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
        buf.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
