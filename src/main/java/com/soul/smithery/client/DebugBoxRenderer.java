package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side debug wireframe overlay for time-limited block positions.
 *
 * <p>Boxes are queued with a tick duration; each client tick decrements remaining ticks
 * and expired entries are dropped. Currently driven by the forge multiblock validator
 * to highlight leak positions when a structure fails to close.
 */
@EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT)
public final class DebugBoxRenderer {
    private DebugBoxRenderer() {}

    private static final List<Box> BOXES = new ArrayList<>();

    private static final int BASE_COLOR_RGB = 0x00FF0000;

    private static final float LINE_WIDTH = 2.5f;

    /**
     * Adds wireframe boxes at the given positions with a shared lifetime.
     *
     * @param positions block positions to outline; copied as immutables so callers may mutate freely
     * @param durationTicks how many client ticks each box should remain visible before fading out
     */
    public static void queueLeaks(Collection<BlockPos> positions, int durationTicks) {
        for (BlockPos pos : positions) {
            BOXES.add(new Box(pos.immutable(), durationTicks, durationTicks));
        }
    }

    /**
     * Ticks down each pending box and removes the ones that have expired.
     *
     * @param event the post client-tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (BOXES.isEmpty()) return;
        Iterator<Box> it = BOXES.iterator();
        while (it.hasNext()) {
            Box b = it.next();
            b.remainingTicks--;
            if (b.remainingTicks <= 0) it.remove();
        }
    }

    /**
     * Draws each surviving box as a red wireframe cube, with alpha fading over its remaining lifetime.
     *
     * @param event the after-translucent-particles render-level event
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentParticles event) {
        if (BOXES.isEmpty()) return;
        PoseStack stack = event.getPoseStack();
        if (stack == null) return;

        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.lines());
        VoxelShape unitCube = Shapes.block();

        stack.pushPose();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);
        for (Box b : BOXES) {
            float fade = Math.max(0f, (float) b.remainingTicks / b.totalTicks);
            int alpha = (int) (fade * 255f) & 0xFF;
            int color = (alpha << 24) | BASE_COLOR_RGB;
            ShapeRenderer.renderShape(stack, consumer, unitCube,
                    b.pos.getX(), b.pos.getY(), b.pos.getZ(), color, LINE_WIDTH);
        }
        stack.popPose();
        buffers.endBatch(RenderTypes.lines());
    }

    private static final class Box {
        final BlockPos pos;
        int remainingTicks;
        final int totalTicks;
        Box(BlockPos pos, int remainingTicks, int totalTicks) {
            this.pos = pos;
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
        }
    }
}
