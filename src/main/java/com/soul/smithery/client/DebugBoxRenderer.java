package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.Smithery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT)
public final class DebugBoxRenderer {
    private DebugBoxRenderer() {}

    private static final List<Box> BOXES = new ArrayList<>();

    private static final float BASE_R = 1.0f;
    private static final float BASE_G = 0.0f;
    private static final float BASE_B = 0.0f;

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
     * @param event the client-tick event; only the END phase advances timers
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BOXES.isEmpty()) return;
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
     * @param event the render-level event; only the after-particles stage draws
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (BOXES.isEmpty()) return;
        PoseStack stack = event.getPoseStack();

        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        stack.pushPose();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);
        for (Box b : BOXES) {
            float alpha = Math.max(0f, (float) b.remainingTicks / b.totalTicks);
            LevelRenderer.renderLineBox(stack, consumer, new AABB(b.pos),
                    BASE_R, BASE_G, BASE_B, alpha);
        }
        stack.popPose();
        buffers.endBatch(RenderType.lines());
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
