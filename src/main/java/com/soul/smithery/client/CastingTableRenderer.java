package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.CastingTableBlockEntity.State;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block entity renderer for the casting table.
 *
 * <p>Draws the impressed sand slab (per-PartType variant block, with the silhouette baked
 * into its texture) and, once filling/cooling/ready, a tinted quad of the part's greyscale
 * template lerped from molten colour to material part colour as cooling progresses.
 */
public class CastingTableRenderer
        implements BlockEntityRenderer<CastingTableBlockEntity, CastingTableRenderer.RenderState> {

    /**
     * Per-frame render snapshot captured from the casting table block entity.
     *
     * <p>The sand layer uses the standard item-render pipeline. The part is drawn as a custom
     * tinted quad rather than through the item pipeline so the tint can lerp per-frame from
     * the material's molten colour to its part colour while the cast cools.
     */
    public static final class RenderState extends BlockEntityRenderState {
        public State castState = State.EMPTY;
        public final ItemStackRenderState sand = new ItemStackRenderState();
        public boolean hasPart;
        public net.minecraft.resources.@org.jspecify.annotations.Nullable ResourceLocation partTextureLoc;
        public int partTintArgb;
    }

    private final ItemModelResolver itemModelResolver;

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context supplying the shared item model resolver
     */
    public CastingTableRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(CastingTableBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        state.castState = be.state();
        state.sand.clear();
        state.hasPart = false;
        state.partTextureLoc = null;
        state.partTintArgb = 0;

        switch (state.castState) {
            case EMPTY -> {  }
            case SAND -> bindSand(state, new ItemStack(SmitheryBlocks.CASTING_SAND.get()), be);
            case IMPRESSED -> bindSand(state, pickImpressedSandStack(be), be);
            case FILLING, COOLING, COVERED, READY -> {
                bindSand(state, pickImpressedSandStack(be), be);
                bindPart(state, be);
            }
        }
    }

    private void bindSand(RenderState state, ItemStack stack, CastingTableBlockEntity be) {
        if (stack.isEmpty()) return;
        itemModelResolver.updateForTopItem(state.sand, stack, ItemDisplayContext.FIXED,
                be.getLevel(), null, 0);
    }

    private static void bindPart(RenderState state, CastingTableBlockEntity be) {
        net.minecraft.resources.ResourceLocation ptId = be.impressedPartTypeId();
        if (ptId == null) return;
        com.soul.smithery.api.part.PartType pt = com.soul.smithery.api.SmitheryAPI.PART_TYPES.get(ptId);
        if (pt == null) return;
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forFluid(be.pouredFluid());
        if (entry == null) return;

        net.minecraft.world.item.ItemStack resultStack = be.peekPartItem();
        if (resultStack.isEmpty()) return;
        boolean isPartItem = resultStack.getItem() instanceof com.soul.smithery.item.PartItem;

        net.minecraft.resources.ResourceLocation texLoc;
        int baseColor;
        if (isPartItem) {
            net.minecraft.resources.ResourceLocation tmpl = pt.textureTemplate();
            texLoc = net.minecraft.resources.new ResourceLocation(
                    tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
            baseColor = entry.material.stats().partColor() | 0xFF000000;
        } else {
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(resultStack.getItem());
            if (itemId == null) return;
            texLoc = net.minecraft.resources.new ResourceLocation(
                    itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            baseColor = 0xFFFFFFFF;
        }
        state.partTextureLoc = texLoc;

        int moltenColor = entry.material.stats().moltenColor() | 0xFF000000;
        float cool      = be.coolingFraction();
        int rgb         = lerpArgb(baseColor, moltenColor, cool) & 0x00FFFFFF;

        float fillFraction = 1.0f;
        if (be.state() == State.FILLING && be.requiredMb() > 0) {
            fillFraction = Math.max(0f, Math.min(1f, (float) be.filledMb() / (float) be.requiredMb()));
        }
        int alpha = Math.max(0, Math.min(255, (int) (fillFraction * 255f)));

        state.partTintArgb = (alpha << 24) | rgb;
        state.hasPart = alpha > 0;
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

    private static ItemStack pickImpressedSandStack(CastingTableBlockEntity be) {
        ResourceLocation ptId = be.impressedPartTypeId();
        if (ptId == null) return new ItemStack(SmitheryBlocks.CASTING_SAND.get());
        RegistryObject<BlockItem> impressedItem = SmitheryBlocks.getImpressedSandItem(ptId);
        return impressedItem == null
                ? new ItemStack(SmitheryBlocks.CASTING_SAND.get())
                : new ItemStack(impressedItem.get());
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.sand.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 15.5f / 16f, 0.5f);
            poseStack.scale(14f / 16f * 2f, 1f / 16f * 2f, 14f / 16f * 2f);
            state.sand.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.hasPart && state.partTextureLoc != null) {
            final int color = state.partTintArgb;
            final net.minecraft.resources.ResourceLocation tex = state.partTextureLoc;
            collector.submitCustomGeometry(poseStack,
                    RenderTypes.entityTranslucent(tex),
                    (pose, buffer) -> drawPartQuad(pose, buffer, color));
        }
    }

    private static void drawPartQuad(PoseStack.Pose pose, VertexConsumer buf, int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        org.joml.Matrix4f m = pose.pose();
        final float y = 16.0f / 16f + 1.0f / 256f;
        addVertex(m, pose, buf,  1f / 16f, y,  1f / 16f, r, g, b, a, 0f, 0f, 0f, 1f, 0f);
        addVertex(m, pose, buf,  1f / 16f, y, 15f / 16f, r, g, b, a, 0f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, 15f / 16f, y, 15f / 16f, r, g, b, a, 1f, 1f, 0f, 1f, 0f);
        addVertex(m, pose, buf, 15f / 16f, y,  1f / 16f, r, g, b, a, 1f, 0f, 0f, 1f, 0f);
    }

    private static void addVertex(org.joml.Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                                   float x, float y, float z,
                                   int r, int g, int b, int a, float u, float v,
                                   float nx, float ny, float nz) {
        buf.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
