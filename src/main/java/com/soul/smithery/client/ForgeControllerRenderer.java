package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders each occupied forge slot as a billboarded 2D sprite of the item's inventory
 * icon — the visual language Tinkers' Construct used for smeltery contents.
 *
 * Rendering pipeline (per occupied slot, per frame):
 *   1. Resolve the ItemStack to its model's first-layer atlas sprite (the inventory icon).
 *   2. Translate the pose stack to the slot's centre + bob offset.
 *   3. Apply camera orientation so subsequent geometry faces the player.
 *   4. Emit a single textured quad (2 triangles, 4 vertices) into the entity-cutout
 *      render type — depth-respecting and atlas-batched.
 *
 * Cost is roughly equivalent to an AE2 terminal sprite per item: one quad per item per
 * frame, batched into the inventory atlas, with no per-item PoseStack child transforms.
 *
 * Tradeoff: items with multi-layer item models (tinted leather, dyed shields, smithery
 * parts with per-slot material tints, etc.) show only their bottom layer's sprite — no
 * 3D structure, no per-layer tint compositing. For the forge use case (mostly raw metals,
 * ores, and ingots) this is the intended TiC-style look; complex modded items will read
 * as a simplified inventory icon rather than their full 3D appearance.
 */
public class ForgeControllerRenderer implements
        BlockEntityRenderer<ForgeControllerBlockEntity, ForgeControllerRenderer.RenderState> {

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Display size of the billboarded sprite in world-blocks (matches TiC smeltery feel). */
    private static final float SPRITE_SIZE = 0.6f;

    private final ItemModelResolver itemModelResolver;
    /** Re-used per extract for ItemStackRenderState resolution. Throwaway; never persisted. */
    private final ItemStackRenderState scratchRenderState = new ItemStackRenderState();
    /** Seeded per slot to keep particle-material selection stable across frames. */
    private final RandomSource scratchRandom = RandomSource.create();

    public ForgeControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    // ---- Render state ----

    public static final class RenderState extends BlockEntityRenderState {
        public final List<SlotEntry> entries = new ArrayList<>();
        public long timeMs;

        public static final class SlotEntry {
            public final Vec3 pos;
            public final TextureAtlasSprite sprite;
            public final float bobPhase;

            public SlotEntry(Vec3 pos, TextureAtlasSprite sprite, float bobPhase) {
                this.pos = pos;
                this.sprite = sprite;
                this.bobPhase = bobPhase;
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
        // Forge interior is enclosed and dark; force full-bright so items read like glowing
        // melting embers rather than silhouettes against the bricks.
        state.lightCoords = FULL_BRIGHT;
        state.entries.clear();

        List<BlockPos> slotPositions = be.slotPositions();
        NonNullList<ItemStack> slots = be.slots();
        if (slotPositions.isEmpty()) return;

        BlockPos origin = be.getBlockPos();
        state.timeMs = System.currentTimeMillis();

        for (int i = 0; i < slotPositions.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;

            // Resolve the item's first-layer sprite. We use ItemDisplayContext.GUI because
            // that's the context whose layer set matches the inventory icon look — the
            // sprite resolution is the same regardless of context, but GUI keeps intent clear.
            scratchRenderState.clear();
            itemModelResolver.updateForTopItem(scratchRenderState, stack,
                    ItemDisplayContext.GUI, be.getLevel(), null, i);
            // Stable per-slot seed so multi-layer items pick the same layer every frame
            // (otherwise pickParticleMaterial flickers between layers each tick).
            scratchRandom.setSeed(i);
            Material.Baked particle = scratchRenderState.pickParticleMaterial(scratchRandom);
            if (particle == null) continue;

            BlockPos sp = slotPositions.get(i);
            double dx = (sp.getX() - origin.getX()) + 0.5;
            double dy = (sp.getY() - origin.getY()) + 0.5;
            double dz = (sp.getZ() - origin.getZ()) + 0.5;
            float bobPhase = (state.timeMs / 1000f) + i * 0.618f;  // golden-ratio spread

            state.entries.add(new RenderState.SlotEntry(
                    new Vec3(dx, dy, dz), particle.sprite(), bobPhase));
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.entries.isEmpty()) return;

        for (RenderState.SlotEntry entry : state.entries) {
            float bobY = Mth.sin(entry.bobPhase * 1.8f) * 0.08f;

            poseStack.pushPose();
            poseStack.translate(entry.pos.x, entry.pos.y + bobY, entry.pos.z);
            // Camera billboard: rotate the pose so subsequent +Z faces the camera. Any
            // geometry drawn in local XY-plane will appear screen-aligned regardless of
            // viewing angle — the AE2-terminal look but in 3D world space.
            poseStack.mulPose(camera.orientation);
            poseStack.scale(SPRITE_SIZE, SPRITE_SIZE, SPRITE_SIZE);

            final TextureAtlasSprite sprite = entry.sprite;
            collector.submitCustomGeometry(poseStack,
                    RenderTypes.entityCutout(sprite.atlasLocation()),
                    (pose, buf) -> drawSpriteQuad(pose, buf, sprite));

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
        // CCW winding when viewed from +Z (camera-facing side after billboard).
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

    @Override
    public int getViewDistance() { return 64; }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
