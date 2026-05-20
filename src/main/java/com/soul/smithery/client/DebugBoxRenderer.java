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
 * Client-side debug renderer for wireframe boxes. Boxes are added with a tick duration; each
 * client tick decrements the counter and removes expired boxes. Each frame, the surviving
 * boxes are drawn with a red wireframe whose alpha fades to zero over the remaining lifetime.
 *
 * Currently only used by the forge multiblock validator to flag leak (hole) positions.
 */
@EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT)
public final class DebugBoxRenderer {
    private DebugBoxRenderer() {}

    /** Mutable in-flight queue. Accessed only on the client thread. */
    private static final List<Box> BOXES = new ArrayList<>();

    /** ARGB-packed full-red. Alpha is overwritten per frame to fade. */
    private static final int BASE_COLOR_RGB = 0x00FF0000;

    /** Line width for renderShape (pixels in 1080p-ish; tune to taste). */
    private static final float LINE_WIDTH = 2.5f;

    public static void queueLeaks(Collection<BlockPos> positions, int durationTicks) {
        for (BlockPos pos : positions) {
            BOXES.add(new Box(pos.immutable(), durationTicks, durationTicks));
        }
    }

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

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentParticles event) {
        if (BOXES.isEmpty()) return;
        PoseStack stack = event.getPoseStack();
        if (stack == null) return;

        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.lines());
        VoxelShape unitCube = Shapes.block(); // 0..1 cube

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
