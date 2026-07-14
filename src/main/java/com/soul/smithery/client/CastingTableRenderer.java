package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import com.soul.smithery.block.entity.CastingTableBlockEntity.State;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Block entity renderer for the casting table.
 *
 * <p>Draws the impressed sand slab (per-PartType variant block, with the silhouette baked
 * into its texture) and, once filling/cooling/ready, a tinted quad of the part's greyscale
 * template lerped from molten colour to material part colour as cooling progresses.
 */
public class CastingTableRenderer implements BlockEntityRenderer<CastingTableBlockEntity> {

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public CastingTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CastingTableBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        State castState = be.state();

        ItemStack sand = switch (castState) {
            case EMPTY -> ItemStack.EMPTY;
            case SAND -> new ItemStack(SmitheryBlocks.CASTING_SAND.get());
            default -> pickImpressedSandStack(be);
        };
        if (!sand.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 15.5f / 16f, 0.5f);
            poseStack.scale(14f / 16f * 2f, 1f / 16f * 2f, 14f / 16f * 2f);
            Minecraft.getInstance().getItemRenderer().renderStatic(sand, ItemDisplayContext.FIXED,
                    packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                    be.getLevel(), 0);
            poseStack.popPose();
        }

        if (castState == State.FILLING || castState == State.COOLING
                || castState == State.COVERED || castState == State.READY) {
            renderPart(be, poseStack, bufferSource);
        }
    }

    private static void renderPart(CastingTableBlockEntity be, PoseStack poseStack,
                                   MultiBufferSource bufferSource) {
        ResourceLocation ptId = be.impressedPartTypeId();
        if (ptId == null) return;
        PartType pt = SmitheryAPI.PART_TYPES.get(ptId);
        if (pt == null) return;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(be.pouredFluid());
        if (entry == null) return;

        ItemStack resultStack = be.peekPartItem();
        if (resultStack.isEmpty()) return;
        boolean isPartItem = resultStack.getItem() instanceof PartItem;

        ResourceLocation texLoc;
        int baseColor;
        if (isPartItem) {
            ResourceLocation tmpl = pt.textureTemplate();
            texLoc = ResourceLocation.fromNamespaceAndPath(tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
            baseColor = entry.material.stats().partColor() | 0xFF000000;
        } else {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(resultStack.getItem());
            if (itemId == null) return;
            texLoc = ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            baseColor = 0xFFFFFFFF;
        }

        int moltenColor = entry.material.stats().moltenColor() | 0xFF000000;
        float cool      = be.coolingFraction();
        int rgb         = lerpArgb(baseColor, moltenColor, cool) & 0x00FFFFFF;

        float fillFraction = 1.0f;
        if (be.state() == State.FILLING && be.requiredMb() > 0) {
            fillFraction = Math.max(0f, Math.min(1f, (float) be.filledMb() / (float) be.requiredMb()));
        }
        int alpha = Math.max(0, Math.min(255, (int) (fillFraction * 255f)));
        if (alpha <= 0) return;

        int color = (alpha << 24) | rgb;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(texLoc));
        drawPartQuad(poseStack.last(), buffer, color);
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

    private static void drawPartQuad(PoseStack.Pose pose, VertexConsumer buf, int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = (color)        & 0xFF;
        int a = (color >>> 24) & 0xFF;
        Matrix4f m = pose.pose();
        final float y = 16.0f / 16f + 1.0f / 256f;
        addVertex(m, pose, buf,  1f / 16f, y,  1f / 16f, r, g, b, a, 0f, 0f);
        addVertex(m, pose, buf,  1f / 16f, y, 15f / 16f, r, g, b, a, 0f, 1f);
        addVertex(m, pose, buf, 15f / 16f, y, 15f / 16f, r, g, b, a, 1f, 1f);
        addVertex(m, pose, buf, 15f / 16f, y,  1f / 16f, r, g, b, a, 1f, 0f);
    }

    private static void addVertex(Matrix4f m, PoseStack.Pose pose, VertexConsumer buf,
                                   float x, float y, float z,
                                   int r, int g, int b, int a, float u, float v) {
        buf.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(pose.normal(), 0f, 1f, 0f)
                .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(CastingTableBlockEntity be) {
        return true;
    }
}
