package com.soul.smithery.event;

import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.Smithery;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent;

/**
 * 3x3 mining for the mining hammer: when a hammer breaks a block, the eight neighbours in the
 * plane of the struck face break too — if the hammer is the correct tool for them and they're
 * no harder than the block actually hit (no free obsidian via a dirt break). Each extra block
 * costs one durability.
 *
 * <p>Recursion guard: the spread uses {@code player.gameMode.destroyBlock}, which re-fires this
 * event per neighbour; a thread-local flag makes those inner breaks single-block.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class AoeMiningHandler {
    private AoeMiningHandler() {}

    private static final ThreadLocal<Boolean> SPREADING = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (SPREADING.get()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem toolItem)) return;
        String toolPath = toolItem.toolTypeId().getPath();

        // Kama: clears crops in a 3x3 around the broken crop.
        if ("kama".equals(toolPath)) {
            if (!event.getState().is(net.minecraft.tags.BlockTags.CROPS)) return;
            SPREADING.set(true);
            try {
                for (BlockPos pos : planeNeighbours(event.getPos(), Direction.UP, 1)) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(net.minecraft.tags.BlockTags.CROPS)) continue;
                    player.gameMode.destroyBlock(pos);
                }
            } finally {
                SPREADING.set(false);
            }
            return;
        }

        // Lumberaxe: breaking a log fells the whole connected tree above and around it.
        if ("lumberaxe".equals(toolPath)) {
            if (!event.getState().is(net.minecraft.tags.BlockTags.LOGS)) return;
            if (!tool.isCorrectToolForDrops(event.getState())) return;
            SPREADING.set(true);
            try {
                fellTree(level, player, tool, event.getPos());
            } finally {
                SPREADING.set(false);
            }
            return;
        }

        if (!"mining_hammer".equals(toolPath) && !"excavator".equals(toolPath)) return;

        BlockPos center = event.getPos();
        BlockState centerState = event.getState();
        if (!tool.isCorrectToolForDrops(centerState)) return;

        Direction face = hitFace(player, center);
        if (face == null) return;

        // Mining hammer / excavator: base 3x3 (radius 1); Excavating widens by one ring per level.
        // Read directly from APPLIED_MODIFIERS — Excavating is a hookless marker like soulbound.
        int radius = 1;
        for (com.soul.smithery.api.modifier.ModifierEffect e : SmitheryToolData.getAppliedModifiers(tool)) {
            if (e.modifierId().equals(com.soul.smithery.content.SmitheryModifiers.EXCAVATING)) {
                radius += Math.max(1, e.paramInt("level", 1));
            }
        }

        float centerHardness = centerState.getDestroySpeed(level, center);
        SPREADING.set(true);
        try {
            for (BlockPos pos : planeNeighbours(center, face, radius)) {
                if (tool.isEmpty()) break;
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                float hardness = state.getDestroySpeed(level, pos);
                if (hardness < 0) continue;
                if (hardness > centerHardness + 0.01f && centerHardness >= 0) continue;
                if (!tool.isCorrectToolForDrops(state)) continue;
                if (player.gameMode.destroyBlock(pos)) {
                    tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                }
            }
        } finally {
            SPREADING.set(false);
        }
    }

    private static final int MAX_TREE_LOGS = 96;

    /**
     * Breadth-first walk over connected logs (26-neighbourhood, so diagonal branches count),
     * breaking each with the lumberaxe at one durability per log. Capped at
     * {@link #MAX_TREE_LOGS} so a wall of fused jungle trees cannot lag the server.
     */
    private static void fellTree(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos origin) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        queue.add(origin);
        seen.add(origin);
        int broken = 0;
        while (!queue.isEmpty() && broken < MAX_TREE_LOGS) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (!seen.add(next)) continue;
                        BlockState state = level.getBlockState(next);
                        if (!state.is(net.minecraft.tags.BlockTags.LOGS)) continue;
                        if (tool.isEmpty()) return;
                        if (player.gameMode.destroyBlock(next)) {
                            tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                            broken++;
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }

    /**
     * All positions within {@code radius} of {@code center} in the plane perpendicular to
     * {@code face}, excluding the center itself — (2r+1)² − 1 blocks.
     */
    private static BlockPos[] planeNeighbours(BlockPos center, Direction face, int radius) {
        Direction right = face.getAxis().isVertical() ? Direction.EAST : face.getClockWise();
        Direction up = face.getAxis().isVertical() ? Direction.SOUTH : Direction.UP;
        int side = 2 * radius + 1;
        BlockPos[] out = new BlockPos[side * side - 1];
        int i = 0;
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                if (u == 0 && v == 0) continue;
                out[i++] = center.relative(right, u).relative(up, v);
            }
        }
        return out;
    }

    /** Ray-traces the player's view to find which face of {@code target} was struck. */
    private static Direction hitFace(ServerPlayer player, BlockPos target) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = player.getBlockReach() + 4.0;
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, eye.add(look.scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getBlockPos().equals(target) ? hit.getDirection() : null;
    }
}
