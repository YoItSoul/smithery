package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders each occupied forge slot as a floating, slowly-rotating item in the
 * center of its air block — identical visual language to Tinkers' smeltery.
 *
 * Uses the MC 26.1.2 render-state extraction pattern:
 *   extractRenderState() → reads BE data into ForgeControllerRenderState
 *   submit()             → uses the pre-extracted state to emit draw calls
 */
public class ForgeControllerRenderer implements
        BlockEntityRenderer<ForgeControllerBlockEntity, ForgeControllerRenderer.RenderState> {

    // Full-bright packed light (no light falloff inside the dark forge).
    private static final int FULL_BRIGHT = 0xF000F0;

    private final ItemModelResolver itemModelResolver;

    public ForgeControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    // ---- Render state ----

    public static final class RenderState extends BlockEntityRenderState {
        public final List<Vec3>               positions = new ArrayList<>();
        public final List<ItemStackRenderState> items   = new ArrayList<>();
        public long timeMs;
    }

    // ---- BlockEntityRenderer interface ----

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(ForgeControllerBlockEntity be, RenderState state, float partialTick,
                                   Vec3 camPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        state.positions.clear();
        state.items.clear();

        List<BlockPos> slotPositions = be.slotPositions();
        NonNullList<ItemStack> slots = be.slots();
        if (slotPositions.isEmpty()) return;

        BlockPos origin = be.getBlockPos();
        state.timeMs = System.currentTimeMillis();

        for (int i = 0; i < slotPositions.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;

            BlockPos sp = slotPositions.get(i);
            float phase = (state.timeMs / 1000f) + i * 0.618f; // golden-ratio spread
            float bobY  = (float) Math.sin(phase * 1.8) * 0.08f;

            double dx = (sp.getX() - origin.getX()) + 0.5;
            double dy = (sp.getY() - origin.getY()) + 0.5 + bobY;
            double dz = (sp.getZ() - origin.getZ()) + 0.5;
            state.positions.add(new Vec3(dx, dy, dz));

            ItemStackRenderState renderState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(renderState, stack,
                    ItemDisplayContext.GROUND, be.getLevel(), null, i);
            state.items.add(renderState);
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        int count = state.positions.size();
        if (count == 0) return;

        long timeMs = state.timeMs;

        for (int i = 0; i < count; i++) {
            Vec3 pos   = state.positions.get(i);
            ItemStackRenderState item = state.items.get(i);

            float phase    = (timeMs / 1000f) + i * 0.618f;
            float rotation = (phase * 40f) % 360f;

            poseStack.pushPose();
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.scale(0.4f, 0.4f, 0.4f);

            item.submit(poseStack, collector, FULL_BRIGHT, 0, i);

            poseStack.popPose();
        }
    }

    @Override
    public int getViewDistance() { return 64; }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
