package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders each occupied forge slot. BlockItems render as actual blocks (so cobblestone in
 * the forge looks like a cobblestone block bobbing in place), while non-block items keep the
 * TiC-style billboarded sprite of their inventory icon.
 *
 * Rendering pipeline (per occupied slot, per frame):
 *   - BlockItem: resolve via ItemStackRenderState in {@link ItemDisplayContext#GROUND} and
 *     submit the baked model at the slot centre + bob offset. No camera billboard — the
 *     player sees the block from whichever angle they're standing.
 *   - Other items: resolve the first-layer atlas sprite and emit a single camera-facing
 *     textured quad — original Tinkers' smeltery look for ingots/nuggets/ores.
 *
 * Cost stays roughly equivalent to AE2 terminal sprites for non-blocks; blocks cost one
 * baked-model submit per slot per frame, batched into the block atlas by NeoForge.
 */
public class ForgeControllerRenderer implements
        BlockEntityRenderer<ForgeControllerBlockEntity, ForgeControllerRenderer.RenderState> {

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Display size of the billboarded sprite (non-block items) in world-blocks. */
    private static final float SPRITE_SIZE = 0.6f;
    /** Block-item display size — slightly smaller than a full cube so it visibly floats. */
    private static final float BLOCK_SIZE  = 0.55f;

    /** Molten-fluid still texture, shared with the fuel-port renderer. */
    private static final Identifier MOLTEN_STILL_TEXTURE =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "textures/block/molten_still.png");

    /** matches molten_still.png.mcmeta animation rate. */
    private static final int  FRAME_COUNT       = 16;
    private static final long FRAME_DURATION_MS = 150L;
    private static final float FRAME_V_STEP     = 1f / FRAME_COUNT;

    /** Eased lerp factor per frame for fluid-pool level smoothing. */
    private static final float POOL_LERP_FACTOR = 0.10f;

    /** Per-(controller-pos × fluid-id) displayed mB cache, so additions/drains animate smoothly. */
    private final Map<Long, Map<Identifier, Float>> displayedFluidByCtrl = new HashMap<>();

    private final ItemModelResolver itemModelResolver;
    /** Per-extract scratch. Throwaway. */
    private final ItemStackRenderState scratchRenderState = new ItemStackRenderState();
    private final RandomSource scratchRandom = RandomSource.create();

    public ForgeControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    // ---- Render state ----

    public static final class RenderState extends BlockEntityRenderState {
        public final List<SlotEntry> entries = new ArrayList<>();
        /** One {@link FluidCubeEntry} per interior position fully or partially submerged. */
        public final List<FluidCubeEntry> fluidCubes = new ArrayList<>();
        public long timeMs;

        /** Discriminated entry: either a baked-model block render or a billboard sprite. */
        public static final class SlotEntry {
            public final Vec3 pos;
            public final float bobPhase;
            /** non-null when this entry should be rendered as a baked block model. */
            public final ItemStackRenderState blockState;
            /** non-null when this entry should be rendered as a camera-facing sprite quad. */
            public final TextureAtlasSprite sprite;

            public SlotEntry(Vec3 pos, float bobPhase,
                             ItemStackRenderState blockState, TextureAtlasSprite sprite) {
                this.pos = pos;
                this.bobPhase = bobPhase;
                this.blockState = blockState;
                this.sprite = sprite;
            }
        }

        /**
         * One submerged interior cell. {@code fillFraction} is 1.0 for fully-submerged cells
         * and less for the topmost partially-filled cell of a given fluid layer; the cube is
         * drawn from y=0 up to y=fillFraction in local block coords.
         */
        public static final class FluidCubeEntry {
            public final Vec3 pos;
            public final int colorArgb;
            public final float fillFraction;
            public FluidCubeEntry(Vec3 pos, int colorArgb, float fillFraction) {
                this.pos = pos;
                this.colorArgb = colorArgb;
                this.fillFraction = fillFraction;
            }
        }
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(ForgeControllerBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        // Forge interior is enclosed; force full-bright so items glow.
        state.lightCoords = FULL_BRIGHT;
        state.entries.clear();
        state.fluidCubes.clear();

        List<BlockPos> slotPositions = be.slotPositions();
        NonNullList<ItemStack> slots = be.slots();
        BlockPos origin = be.getBlockPos();
        state.timeMs = System.currentTimeMillis();

        // Molten-pool extract runs first so it's still emitted even when no items are present
        // (the forge can hold pooled fluid while empty of solids).
        extractFluidPool(be, state, slotPositions, origin);

        if (slotPositions.isEmpty()) return;

        for (int i = 0; i < slotPositions.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;

            BlockPos sp = slotPositions.get(i);
            double dx = (sp.getX() - origin.getX()) + 0.5;
            double dy = (sp.getY() - origin.getY()) + 0.5;
            double dz = (sp.getZ() - origin.getZ()) + 0.5;
            float bobPhase = (state.timeMs / 1000f) + i * 0.618f;
            Vec3 pos = new Vec3(dx, dy, dz);

            if (stack.getItem() instanceof BlockItem) {
                // Block path: resolve a fresh ItemStackRenderState (must clone since entries
                // outlive this method) and tag the slot for baked-model rendering.
                ItemStackRenderState perSlotState = new ItemStackRenderState();
                itemModelResolver.updateForTopItem(perSlotState, stack,
                        ItemDisplayContext.GROUND, be.getLevel(), null, i);
                state.entries.add(new RenderState.SlotEntry(pos, bobPhase, perSlotState, null));
                continue;
            }

            // Non-block path: resolve first-layer sprite for the camera-billboard quad.
            scratchRenderState.clear();
            itemModelResolver.updateForTopItem(scratchRenderState, stack,
                    ItemDisplayContext.GUI, be.getLevel(), null, i);
            scratchRandom.setSeed(i);
            Material.Baked particle = scratchRenderState.pickParticleMaterial(scratchRandom);
            if (particle == null) continue;
            state.entries.add(new RenderState.SlotEntry(pos, bobPhase, null, particle.sprite()));
        }
    }

    /**
     * Distributes stored fluid mB across the interior block list (sorted Y-ascending) and
     * records one render entry per fully or partially submerged interior cell. Per-fluid
     * mB are lerped against the previous frame's displayed amount so adds/drains animate.
     */
    private void extractFluidPool(ForgeControllerBlockEntity be, RenderState state,
                                  List<BlockPos> slotPositions, BlockPos origin) {
        if (slotPositions.isEmpty()) return;
        Map<Identifier, Integer> stored = be.fluidStorageView();
        if (stored.isEmpty()) {
            // Decay any cached entries for this controller toward 0 so emptied forges
            // ease to invisible rather than snapping.
            Map<Identifier, Float> cached = displayedFluidByCtrl.get(origin.asLong());
            if (cached == null || cached.isEmpty()) return;
            // Walk the cache, lerping each entry toward 0 and dropping the cache when empty.
            Map<Identifier, Integer> phantom = new java.util.LinkedHashMap<>();
            for (Map.Entry<Identifier, Float> e : cached.entrySet()) {
                int dec = (int) (e.getValue() * (1f - POOL_LERP_FACTOR));
                if (dec > 0) phantom.put(e.getKey(), dec);
            }
            stored = phantom;
            if (stored.isEmpty()) {
                displayedFluidByCtrl.remove(origin.asLong());
                return;
            }
        }

        // Lerp displayed mB per fluid.
        Map<Identifier, Float> displayed = displayedFluidByCtrl
                .computeIfAbsent(origin.asLong(), k -> new HashMap<>());
        // Drop entries no longer present so the cache doesn't grow.
        displayed.keySet().retainAll(stored.keySet());
        Map<Identifier, Integer> displayedMb = new java.util.LinkedHashMap<>();
        for (Map.Entry<Identifier, Integer> e : stored.entrySet()) {
            float target = e.getValue();
            float prev = displayed.getOrDefault(e.getKey(), target);
            float lerped = prev + (target - prev) * POOL_LERP_FACTOR;
            displayed.put(e.getKey(), lerped);
            displayedMb.put(e.getKey(), Math.max(0, Math.round(lerped)));
        }

        // Sort interior positions Y-ascending — fluids settle at the bottom first.
        List<BlockPos> orderedInterior = new ArrayList<>(slotPositions);
        orderedInterior.sort((a, b) -> {
            int c = Integer.compare(a.getY(), b.getY());
            if (c != 0) return c;
            c = Integer.compare(a.getX(), b.getX());
            if (c != 0) return c;
            return Integer.compare(a.getZ(), b.getZ());
        });

        int cursor = 0;
        for (Map.Entry<Identifier, Integer> e : displayedMb.entrySet()) {
            int mb = e.getValue();
            if (mb <= 0) continue;
            int color = colorForFluidId(e.getKey());
            int fullBlocks = mb / 1000;
            int remainder = mb % 1000;
            // Full-fill cells.
            for (int n = 0; n < fullBlocks && cursor < orderedInterior.size(); n++, cursor++) {
                BlockPos ip = orderedInterior.get(cursor);
                state.fluidCubes.add(new RenderState.FluidCubeEntry(
                        new Vec3(ip.getX() - origin.getX(),
                                 ip.getY() - origin.getY(),
                                 ip.getZ() - origin.getZ()),
                        color, 1.0f));
            }
            // Partial top cell.
            if (remainder > 0 && cursor < orderedInterior.size()) {
                BlockPos ip = orderedInterior.get(cursor++);
                state.fluidCubes.add(new RenderState.FluidCubeEntry(
                        new Vec3(ip.getX() - origin.getX(),
                                 ip.getY() - origin.getY(),
                                 ip.getZ() - origin.getZ()),
                        color, remainder / 1000f));
            }
            if (cursor >= orderedInterior.size()) break;
        }
    }

    private static int colorForFluidId(Identifier fluidId) {
        Fluid f = BuiltInRegistries.FLUID.get(fluidId).<Fluid>map(h -> h.value()).orElse(null);
        if (f == null) return 0xDDFF8844;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(f);
        if (entry == null) return 0xDDFF8844;
        return 0xDD000000 | (entry.material.stats().moltenColor() & 0xFFFFFF);
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        // Fluid pool first (drawn under floating items so they appear on top).
        if (!state.fluidCubes.isEmpty()) {
            final int frame = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % FRAME_COUNT);
            final float vMin = frame * FRAME_V_STEP;
            final float vMax = (frame + 1) * FRAME_V_STEP;
            collector.submitCustomGeometry(poseStack,
                    RenderTypes.entityTranslucent(MOLTEN_STILL_TEXTURE),
                    (pose, buf) -> {
                        for (RenderState.FluidCubeEntry cube : state.fluidCubes) {
                            drawFluidCell(pose, buf, cube, vMin, vMax);
                        }
                    });
        }

        if (state.entries.isEmpty()) return;

        for (RenderState.SlotEntry entry : state.entries) {
            float bobY = Mth.sin(entry.bobPhase * 1.8f) * 0.08f;

            poseStack.pushPose();
            poseStack.translate(entry.pos.x, entry.pos.y + bobY, entry.pos.z);

            if (entry.blockState != null) {
                // Baked-block path. Center-origin scaling, no billboard rotation — the player
                // sees the block face-on from wherever they stand. ItemStackRenderState's
                // submit() centers the block model around the pose origin.
                poseStack.scale(BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                entry.blockState.submit(poseStack, collector,
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            } else if (entry.sprite != null) {
                // Sprite path — same as before.
                poseStack.mulPose(camera.orientation);
                poseStack.scale(SPRITE_SIZE, SPRITE_SIZE, SPRITE_SIZE);

                final TextureAtlasSprite sprite = entry.sprite;
                collector.submitCustomGeometry(poseStack,
                        RenderTypes.entityCutout(sprite.atlasLocation()),
                        (pose, buf) -> drawSpriteQuad(pose, buf, sprite));
            }

            poseStack.popPose();
        }
    }

    /**
     * Emits a unit XY-plane quad textured with the given atlas sprite. Origin-centred so
     * the parent pose's translate lands the sprite's centre at the slot.
     */
    private static void drawSpriteQuad(PoseStack.Pose pose, VertexConsumer buf,
                                        TextureAtlasSprite sprite) {
        Matrix4f m = pose.pose();
        Vector3f n = pose.transformNormal(0f, 0f, 1f, new Vector3f());
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        addVertex(buf, m, n, -0.5f, -0.5f, u0, v1);
        addVertex(buf, m, n,  0.5f, -0.5f, u1, v1);
        addVertex(buf, m, n,  0.5f,  0.5f, u1, v0);
        addVertex(buf, m, n, -0.5f,  0.5f, u0, v0);
    }

    private static void addVertex(VertexConsumer buf, Matrix4f m, Vector3f n,
                                   float x, float y, float u, float v) {
        buf.addVertex(m, x, y, 0f)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(n.x, n.y, n.z);
    }

    /** Draws the 6-faced fluid cube for one submerged interior cell, with the top quad
     *  shifted down by (1 - fillFraction) so partially-filled cells reveal a surface. */
    private static void drawFluidCell(PoseStack.Pose pose, VertexConsumer buf,
                                      RenderState.FluidCubeEntry cube, float vMin, float vMax) {
        int color = cube.colorArgb;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;

        // Cell local coords — pose is at controller origin; cube.pos is the interior cell's
        // delta. So the cell occupies (px..px+1, py..py+fill, pz..pz+1).
        final float px = (float) cube.pos.x;
        final float py = (float) cube.pos.y;
        final float pz = (float) cube.pos.z;
        final float x0 = px;
        final float x1 = px + 1f;
        final float z0 = pz;
        final float z1 = pz + 1f;
        final float y0 = py;
        final float y1 = py + cube.fillFraction;

        Matrix4f m = pose.pose();
        // 4 side faces
        face(m, pose, buf, x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0, r, g, b, a, vMin, vMax, -1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1, r, g, b, a, vMin, vMax,  1f, 0f, 0f);
        face(m, pose, buf, x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0, r, g, b, a, vMin, vMax, 0f, 0f, -1f);
        face(m, pose, buf, x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1, r, g, b, a, vMin, vMax, 0f, 0f,  1f);
        // Top quad — the surface
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
        buf.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public int getViewDistance() { return 64; }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
