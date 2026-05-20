package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * State + behavior for the Forge multiblock. For this first cut we just hold the validated
 * structure shape — temperature, fluid storage, and tick logic land in the next sub-step.
 *
 * Multiblock rules (initial):
 *   - Shell: deepslate_bricks, cracked_deepslate_bricks, or polished_deepslate.
 *   - Active blocks (count toward shell coverage): the 3 Smithery forge blocks
 *     (controller, fuel port, drain). Exactly 1 controller; at least 1 of each port.
 *   - Interior: at least 1 air block, fully enclosed.
 *   - Detection seeds from the controller, flood-fills the connected interior air, then
 *     checks every interior block's 6-neighborhood for shell coverage.
 */
public class ForgeControllerBlockEntity extends BlockEntity {

    /** Bounding box face limit for the interior flood-fill, to keep validation cheap. */
    private static final int MAX_INTERIOR_BLOCKS = 1024;

    /** Sanity bound on the shell BFS to keep validation cheap when there's a lot of nearby deepslate. */
    private static final int MAX_SHELL_BLOCKS = 2048;

    /** How often (in server ticks) we passively re-validate the structure as a catch-all. */
    private static final int VALIDATION_TICK_INTERVAL = 40; // 2 seconds

    private ValidationResult lastValidation = ValidationResult.invalid("not yet validated");

    // ---- Persistent state (placeholders until simulation lands) ----
    private float temperatureC = 20f;       // Ambient °C; will be driven by fuel/RF later.
    private int   ticksSinceValidation = 0;

    public ForgeControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_CONTROLLER.get(), pos, state);
    }

    public ValidationResult lastValidation() { return lastValidation; }
    public float temperatureC() { return temperatureC; }

    /**
     * Server-side tick driver. Currently only re-validates the structure on a cadence; the
     * heat / fluid / alloy simulation hooks in here once those subsystems exist.
     */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ticksSinceValidation++;
        if (ticksSinceValidation >= VALIDATION_TICK_INTERVAL) {
            ticksSinceValidation = 0;
            validateStructure();
        }
        // TODO: heat update, fluid melting, alloy resolution
    }

    /**
     * Re-validate the structure. Called on placement, neighbor change, and on load.
     * Returns the new result and caches it.
     *
     * Algorithm:
     *   1. BFS the connected shell from the controller; compute the shell's AABB and the
     *      top-of-shell Y (highest shell block).
     *   2. Flood-fill the interior from the controller's air-adjacent faces, CAPPED BY
     *      THE SHELL AABB. Even if a wall has a hole, the fill can't escape outside the
     *      bounding box of the shell — so partial structures find their largest enclosed
     *      pocket naturally.
     *   3. Classify each interior face neighbor:
     *        - interior  → skip
     *        - shell     → add to shell set
     *        - UP and above yTopShell → mark open top (not a hole)
     *        - anything else (air at a shell position, etc.) → count as a HOLE
     *   4. Ports check: exactly 1 controller, >=1 fuel port, >=1 drain.
     *   5. The forge is "valid" even with holes. Hole count is exposed in the result so
     *      thermal performance can later be penalized (per design notes).
     */
    public ValidationResult validateStructure() {
        if (level == null) {
            lastValidation = ValidationResult.invalid("no level");
            return lastValidation;
        }

        // Phase 1: BFS shell from the controller. 26-connected (faces + edges + corners) so
        // perpendicular walls that "should" share a missing corner block still register as
        // connected. This matches Tinkers'-style smelteries where corner bricks are optional.
        Set<BlockPos> shellPool = new HashSet<>();
        List<BlockPos> shellQueue = new ArrayList<>();
        shellQueue.add(worldPosition);
        while (!shellQueue.isEmpty()) {
            BlockPos p = shellQueue.remove(shellQueue.size() - 1);
            if (!shellPool.add(p)) continue;
            if (shellPool.size() > MAX_SHELL_BLOCKS) {
                lastValidation = ValidationResult.invalid("connected shell too large (>" + MAX_SHELL_BLOCKS + ")");
                return lastValidation;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (shellPool.contains(n)) continue;
                        if (isShellBlock(level.getBlockState(n))) shellQueue.add(n);
                    }
                }
            }
        }

        if (shellPool.size() < 2) {
            lastValidation = ValidationResult.invalid("controller has no adjacent shell — build walls first");
            return lastValidation;
        }

        // Phase 2: shell AABB + yTopShell.
        int xMin = Integer.MAX_VALUE, xMax = Integer.MIN_VALUE;
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        int zMin = Integer.MAX_VALUE, zMax = Integer.MIN_VALUE;
        for (BlockPos s : shellPool) {
            if (s.getX() < xMin) xMin = s.getX(); if (s.getX() > xMax) xMax = s.getX();
            if (s.getY() < yMin) yMin = s.getY(); if (s.getY() > yMax) yMax = s.getY();
            if (s.getZ() < zMin) zMin = s.getZ(); if (s.getZ() > zMax) zMax = s.getZ();
        }
        final int yTopShell = yMax;
        final int bboxXMin = xMin, bboxXMax = xMax;
        final int bboxYMin = yMin, bboxYMax = yMax;
        final int bboxZMin = zMin, bboxZMax = zMax;

        // Phase 3: flood-fill the interior, union over every air-adjacent face of the
        // controller. Capped within the shell AABB so any leak through a wall hole only
        // includes the hole cell itself (the air immediately outside is out-of-bbox).
        Set<BlockPos> interior = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos seed = worldPosition.relative(d);
            if (inBbox(seed, bboxXMin, bboxXMax, bboxYMin, bboxYMax, bboxZMin, bboxZMax)
                    && isInteriorCandidate(seed)) {
                queue.add(seed);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.remove(queue.size() - 1);
            if (!inBbox(p, bboxXMin, bboxXMax, bboxYMin, bboxYMax, bboxZMin, bboxZMax)) continue;
            if (!interior.add(p)) continue;
            if (interior.size() > MAX_INTERIOR_BLOCKS) {
                lastValidation = ValidationResult.invalid("interior too large (>" + MAX_INTERIOR_BLOCKS + ")");
                return lastValidation;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!interior.contains(n)
                        && inBbox(n, bboxXMin, bboxXMax, bboxYMin, bboxYMax, bboxZMin, bboxZMax)
                        && isInteriorCandidate(n)) {
                    queue.add(n);
                }
            }
        }
        if (interior.isEmpty()) {
            lastValidation = ValidationResult.invalid(
                    "no interior air adjacent to controller (shell=" + shellPool.size()
                    + " blocks, bbox x[" + bboxXMin + ".." + bboxXMax + "] y[" + bboxYMin
                    + ".." + bboxYMax + "] z[" + bboxZMin + ".." + bboxZMax + "])");
            return lastValidation;
        }

        // Phase 4: classify face neighbors.
        Set<BlockPos> shell = new HashSet<>();
        Set<BlockPos> holePositions = new HashSet<>();
        boolean openTop = false;
        for (BlockPos inside : interior) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = inside.relative(dir);
                if (interior.contains(neighbor)) continue;

                // Open top: a +Y neighbor strictly above the wall top is sky, not a hole.
                if (dir == Direction.UP && neighbor.getY() > yTopShell) {
                    openTop = true;
                    continue;
                }

                BlockState bs = level.getBlockState(neighbor);
                if (isShellBlock(bs)) {
                    shell.add(neighbor);
                } else {
                    holePositions.add(neighbor);
                }
            }
        }

        // Phase 5: port counts (from the deduped shell set).
        int controllerCount = 0, fuelPortCount = 0, drainCount = 0;
        for (BlockPos s : shell) {
            BlockState bs = level.getBlockState(s);
            if (bs.is(SmitheryBlocks.FORGE_CONTROLLER.get())) controllerCount++;
            else if (bs.is(SmitheryBlocks.FORGE_FUEL_PORT.get())) fuelPortCount++;
            else if (bs.is(SmitheryBlocks.FORGE_DRAIN.get())) drainCount++;
        }
        if (controllerCount != 1) {
            lastValidation = ValidationResult.invalid("must have exactly 1 controller (found " + controllerCount + ")");
            return lastValidation;
        }
        if (fuelPortCount < 1) {
            lastValidation = ValidationResult.invalid("missing fuel port");
            return lastValidation;
        }
        if (drainCount < 1) {
            lastValidation = ValidationResult.invalid("missing drain");
            return lastValidation;
        }

        lastValidation = ValidationResult.valid(Collections.unmodifiableSet(interior),
                                                Collections.unmodifiableSet(shell),
                                                openTop, Collections.unmodifiableSet(holePositions));
        return lastValidation;
    }

    private static boolean inBbox(BlockPos p, int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        return p.getX() >= xMin && p.getX() <= xMax
            && p.getY() >= yMin && p.getY() <= yMax
            && p.getZ() >= zMin && p.getZ() <= zMax;
    }

    private boolean isInteriorCandidate(BlockPos pos) {
        if (level == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir();
    }

    /** True if the block at this state may participate as a forge shell. */
    public static boolean isShellBlock(BlockState state) {
        return state.is(Blocks.DEEPSLATE_BRICKS)
                || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS)
                || state.is(Blocks.POLISHED_DEEPSLATE)
                || state.is(SmitheryBlocks.FORGE_CONTROLLER.get())
                || state.is(SmitheryBlocks.FORGE_FUEL_PORT.get())
                || state.is(SmitheryBlocks.FORGE_DRAIN.get());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            validateStructure();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        temperatureC = input.getFloatOr("temperatureC", 20f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("temperatureC", temperatureC);
    }

    /**
     * Result of a multiblock validation pass. A valid result carries the full interior +
     * shell positions; an invalid one carries a human-readable reason.
     */
    public static final class ValidationResult {
        public final boolean valid;
        public final String reason;
        public final Set<BlockPos> interior;
        public final Set<BlockPos> shell;
        public final boolean openTop;
        public final Set<BlockPos> holePositions;

        private ValidationResult(boolean valid, String reason, Set<BlockPos> interior, Set<BlockPos> shell, boolean openTop, Set<BlockPos> holePositions) {
            this.valid = valid;
            this.reason = reason;
            this.interior = interior;
            this.shell = shell;
            this.openTop = openTop;
            this.holePositions = holePositions;
        }

        /** 1 bucket per interior air block (1000 mB each). */
        public int capacityBuckets() { return interior.size(); }
        public int capacityMb() { return interior.size() * 1000; }
        public int holes() { return holePositions.size(); }

        static ValidationResult valid(Set<BlockPos> interior, Set<BlockPos> shell, boolean openTop, Set<BlockPos> holePositions) {
            return new ValidationResult(true, "", interior, shell, openTop, holePositions);
        }

        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, Set.of(), Set.of(), false, Set.of());
        }
    }
}
