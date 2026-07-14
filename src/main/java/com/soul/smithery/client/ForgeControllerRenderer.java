package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block entity renderer for the forge controller.
 *
 * <p>Renders each occupied interior slot — block items as baked models, other items as
 * camera-billboarded sprite quads — and additionally draws a stacked fluid pool that fills
 * interior cells from the bottom up, with per-fluid mB amounts lerped frame-to-frame so
 * additions and drains animate smoothly.
 */
public class ForgeControllerRenderer implements BlockEntityRenderer<ForgeControllerBlockEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;

    private static final float SPRITE_SIZE = 0.6f;
    private static final float BLOCK_SIZE  = 0.55f;

    private static final ResourceLocation MOLTEN_STILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "textures/block/molten_still.png");

    private static final int  FRAME_COUNT       = 16;
    private static final long FRAME_DURATION_MS = 150L;
    private static final float FRAME_V_STEP     = 1f / FRAME_COUNT;

    private static final float POOL_LERP_FACTOR = 0.10f;

    private final Map<Long, Map<ResourceLocation, Float>> displayedFluidByCtrl = new HashMap<>();

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public ForgeControllerRenderer(BlockEntityRendererProvider.Context context) {}

    /** A submerged interior cell of the fluid pool. */
    private record FluidCubeEntry(Vec3 pos, int colorArgb, float fillFraction) {}

    @Override
    public void render(ForgeControllerBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        List<BlockPos> slotPositions = be.slotPositions();
        BlockPos origin = be.getBlockPos();

        List<FluidCubeEntry> fluidCubes = computeFluidPool(be, slotPositions, origin);
        if (!fluidCubes.isEmpty()) {
            final int frame = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % FRAME_COUNT);
            final float vMin = frame * FRAME_V_STEP;
            final float vMax = (frame + 1) * FRAME_V_STEP;
            VertexConsumer buf = bufferSource.getBuffer(RenderType.entityTranslucent(MOLTEN_STILL_TEXTURE));
            PoseStack.Pose pose = poseStack.last();
            for (FluidCubeEntry cube : fluidCubes) {
                drawFluidCell(pose, buf, cube, vMin, vMax);
            }
        }

        if (slotPositions.isEmpty()) return;
        NonNullList<ItemStack> slots = be.slots();
        long timeMs = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();

        for (int i = 0; i < slotPositions.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;

            BlockPos sp = slotPositions.get(i);
            double dx = (sp.getX() - origin.getX()) + 0.5;
            double dy = (sp.getY() - origin.getY()) + 0.5;
            double dz = (sp.getZ() - origin.getZ()) + 0.5;
            float bobPhase = (timeMs / 1000f) + i * 0.618f;
            float bobY = Mth.sin(bobPhase * 1.8f) * 0.08f;

            poseStack.pushPose();
            poseStack.translate(dx, dy + bobY, dz);

            if (stack.getItem() instanceof BlockItem) {
                poseStack.scale(BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                        be.getLevel(), i);
            } else {
                TextureAtlasSprite sprite = mc.getItemRenderer()
                        .getModel(stack, be.getLevel(), null, i)
                        .getParticleIcon();
                poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
                poseStack.scale(SPRITE_SIZE, SPRITE_SIZE, SPRITE_SIZE);
                VertexConsumer buf = bufferSource.getBuffer(
                        RenderType.entityCutout(sprite.atlasLocation()));
                drawSpriteQuad(poseStack.last(), buf, sprite);
            }

            poseStack.popPose();
        }
    }

    private List<FluidCubeEntry> computeFluidPool(ForgeControllerBlockEntity be,
                                                  List<BlockPos> slotPositions, BlockPos origin) {
        List<FluidCubeEntry> cubes = new ArrayList<>();
        if (slotPositions.isEmpty()) return cubes;
        Map<ResourceLocation, Integer> stored = be.fluidStorageView();
        if (stored.isEmpty()) {
            Map<ResourceLocation, Float> cached = displayedFluidByCtrl.get(origin.asLong());
            if (cached == null || cached.isEmpty()) return cubes;
            Map<ResourceLocation, Integer> phantom = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Float> e : cached.entrySet()) {
                int dec = (int) (e.getValue() * (1f - POOL_LERP_FACTOR));
                if (dec > 0) phantom.put(e.getKey(), dec);
            }
            stored = phantom;
            if (stored.isEmpty()) {
                displayedFluidByCtrl.remove(origin.asLong());
                return cubes;
            }
        }

        Map<ResourceLocation, Float> displayed = displayedFluidByCtrl
                .computeIfAbsent(origin.asLong(), k -> new HashMap<>());
        displayed.keySet().retainAll(stored.keySet());
        Map<ResourceLocation, Integer> displayedMb = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Integer> e : stored.entrySet()) {
            float target = e.getValue();
            float prev = displayed.getOrDefault(e.getKey(), target);
            float lerped = prev + (target - prev) * POOL_LERP_FACTOR;
            displayed.put(e.getKey(), lerped);
            displayedMb.put(e.getKey(), Math.max(0, Math.round(lerped)));
        }

        List<BlockPos> orderedInterior = new ArrayList<>(slotPositions);
        orderedInterior.sort((a, b) -> {
            int c = Integer.compare(a.getY(), b.getY());
            if (c != 0) return c;
            c = Integer.compare(a.getX(), b.getX());
            if (c != 0) return c;
            return Integer.compare(a.getZ(), b.getZ());
        });

        int cursor = 0;
        for (Map.Entry<ResourceLocation, Integer> e : displayedMb.entrySet()) {
            int mb = e.getValue();
            if (mb <= 0) continue;
            int color = colorForFluidId(e.getKey());
            int fullBlocks = mb / 1000;
            int remainder = mb % 1000;
            for (int n = 0; n < fullBlocks && cursor < orderedInterior.size(); n++, cursor++) {
                BlockPos ip = orderedInterior.get(cursor);
                cubes.add(new FluidCubeEntry(
                        new Vec3(ip.getX() - origin.getX(),
                                 ip.getY() - origin.getY(),
                                 ip.getZ() - origin.getZ()),
                        color, 1.0f));
            }
            if (remainder > 0 && cursor < orderedInterior.size()) {
                BlockPos ip = orderedInterior.get(cursor++);
                cubes.add(new FluidCubeEntry(
                        new Vec3(ip.getX() - origin.getX(),
                                 ip.getY() - origin.getY(),
                                 ip.getZ() - origin.getZ()),
                        color, remainder / 1000f));
            }
            if (cursor >= orderedInterior.size()) break;
        }
        return cubes;
    }

    private static int colorForFluidId(ResourceLocation fluidId) {
        Fluid f = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (f == null || f == Fluids.EMPTY) return 0xDDFF8844;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(f);
        if (entry == null) return 0xDDFF8844;
        return 0xDD000000 | (entry.material.stats().moltenColor() & 0xFFFFFF);
    }

    private static void drawSpriteQuad(PoseStack.Pose pose, VertexConsumer buf,
                                        TextureAtlasSprite sprite) {
        Matrix4f m = pose.pose();
        Vector3f n = pose.normal().transform(new Vector3f(0f, 0f, 1f));
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        addVertex(buf, m, n, -0.5f, -0.5f, u0, v1);
        addVertex(buf, m, n,  0.5f, -0.5f, u1, v1);
        addVertex(buf, m, n,  0.5f,  0.5f, u1, v0);
        addVertex(buf, m, n, -0.5f,  0.5f, u0, v0);
    }

    private static void addVertex(VertexConsumer buf, Matrix4f m, Vector3f n,
                                   float x, float y, float u, float v) {
        buf.vertex(m, x, y, 0f)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(n.x, n.y, n.z)
                .endVertex();
    }

    private static void drawFluidCell(PoseStack.Pose pose, VertexConsumer buf,
                                      FluidCubeEntry cube, float vMin, float vMax) {
        int color = cube.colorArgb();
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;

        final float px = (float) cube.pos().x;
        final float py = (float) cube.pos().y;
        final float pz = (float) cube.pos().z;
        final float x0 = px;
        final float x1 = px + 1f;
        final float z0 = pz;
        final float z1 = pz + 1f;
        final float y0 = py;
        final float y1 = py + cube.fillFraction();

        Matrix4f m = pose.pose();
        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0, r, g, b, a, vMin, vMax, -1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1, r, g, b, a, vMin, vMax,  1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, vMin, vMax, 0f, 0f, -1f);
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, vMin, vMax, 0f, 0f,  1f);
        face(m, pose, buf, x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0, r, g, b, a, vMin, vMax, 0f, 1f, 0f);
    }

    private static void face(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int r, int g, int b, int a,
                             float vMin, float vMax,
                             float nx, float ny, float nz) {
        addFluidVertex(m, pose, buf, x0, y0, z0, r, g, b, a, 0f, vMin, nx, ny, nz);
        addFluidVertex(m, pose, buf, x1, y1, z1, r, g, b, a, 1f, vMin, nx, ny, nz);
        addFluidVertex(m, pose, buf, x2, y2, z2, r, g, b, a, 1f, vMax, nx, ny, nz);
        addFluidVertex(m, pose, buf, x3, y3, z3, r, g, b, a, 0f, vMax, nx, ny, nz);
    }

    private static void addFluidVertex(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
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

    @Override
    public int getViewDistance() { return 64; }

    @Override
    public boolean shouldRenderOffScreen(ForgeControllerBlockEntity be) {
        return true;
    }
}
