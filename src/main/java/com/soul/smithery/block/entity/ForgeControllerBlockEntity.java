package com.soul.smithery.block.entity;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    // Heat simulation constants. Tunable — exposed as server config later.
    private static final float HEAT_AMBIENT_C       = 20.0f;
    private static final float HEAT_TARGET_LAVA_C   = 1650.0f;
    private static final float HEAT_RATE_PER_TICK   = 0.0030f; // toward target each tick when fueled
    private static final float COOL_RATE_PER_TICK   = 0.0010f; // toward ambient each tick when unfueled
    /** Closed-top bonus: heats 1.2x faster, cools 1.2x slower. Matches design intent. */
    private static final float CLOSED_TOP_FACTOR    = 1.2f;
    /** Skip setChanged() unless the temperature moved by at least this much, to cut save churn. */
    private static final float TEMP_DIRTY_THRESHOLD = 0.25f;

    /** Lava mB consumed per tick when fueled. ~0.1 mB/tick × 20 tps = 2 mB/sec = 1 bucket / 500s. */
    private static final float FUEL_CONSUMPTION_PER_TICK = 0.1f;

    /** Melt rate constants. melt_rate = BASE × (1 + SCALE × (T_forge - T_melt) / T_melt). */
    private static final float MELT_BASE_RATE_MB_PER_TICK = 1.0f;
    private static final float MELT_TEMP_SCALE            = 2.0f;

    private ValidationResult lastValidation = ValidationResult.invalid("not yet validated");

    // ---- Persistent state ----
    private float temperatureC = HEAT_AMBIENT_C;
    private float lastSavedTemperatureC = HEAT_AMBIENT_C;
    private int   ticksSinceValidation = 0;
    private boolean fueledLastTick = false;
    /** Accumulates sub-mB fuel debt so int-storage ports get drained at the correct rate. */
    private float fuelConsumptionAccumulator = 0f;
    /** Total lava mB across all fuel ports, computed on the last tick — for tooltips/UI. */
    private int totalFuelMb = 0;
    private int totalFuelCapacityMb = 0;

    /**
     * Molten fluid storage. Keyed by material id. The controller is the "sacred block" —
     * destruction of the controller is the only way to lose stored fluid. If the surrounding
     * shell becomes invalid, the storage is locked (no input or output) but the fluid stays
     * in this map until the structure is repaired. Insertion-ordered so display order is
     * stable across saves.
     */
    private final Map<Identifier, Integer> fluidStorage = new LinkedHashMap<>();

    /**
     * Per-ItemEntity melting progress in mB. When progress >= recipe.outputMb, one item is
     * consumed and progress resets. Entries are cleaned up when the entity is gone, the
     * recipe disappears, or the entity leaves the interior.
     */
    private final Map<UUID, Float> meltProgress = new HashMap<>();

    public ForgeControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_CONTROLLER.get(), pos, state);
    }

    public ValidationResult lastValidation() { return lastValidation; }
    public float temperatureC() { return temperatureC; }
    public boolean isFueled() { return fueledLastTick; }
    public int totalFuelMb() { return totalFuelMb; }
    public int totalFuelCapacityMb() { return totalFuelCapacityMb; }
    public float targetTemperatureC() {
        return fueledLastTick ? HEAT_TARGET_LAVA_C : HEAT_AMBIENT_C;
    }

    // ---- Fluid storage ----

    /**
     * True if ports + melting may currently read/write the fluid storage. When the multiblock
     * structure is invalid, the storage is locked but the contents are preserved.
     */
    public boolean canAccessFluids() { return lastValidation.valid; }

    /** Capacity in mB derived from the last validation. Zero while invalid. */
    public int fluidCapacityMb() {
        return lastValidation.valid ? lastValidation.capacityMb() : 0;
    }

    /** Sum of all stored fluid mB. Includes locked contents while structure is invalid. */
    public int totalStoredFluidMb() {
        int sum = 0;
        for (int v : fluidStorage.values()) sum += v;
        return sum;
    }

    public int remainingFluidCapacityMb() {
        return Math.max(0, fluidCapacityMb() - totalStoredFluidMb());
    }

    public int storedFluidMb(Identifier materialId) {
        return fluidStorage.getOrDefault(materialId, 0);
    }

    /** Read-only view of the stored fluids in insertion order. */
    public Map<Identifier, Integer> fluidStorageView() {
        return Collections.unmodifiableMap(fluidStorage);
    }

    /**
     * Add {@code mb} of {@code materialId} to storage. Capped by remaining capacity.
     * Returns the amount actually added. No-op (returns 0) while the structure is invalid.
     */
    public int addFluid(Identifier materialId, int mb) {
        if (!canAccessFluids() || mb <= 0) return 0;
        int toAdd = Math.min(mb, remainingFluidCapacityMb());
        if (toAdd <= 0) return 0;
        fluidStorage.merge(materialId, toAdd, Integer::sum);
        setChanged();
        return toAdd;
    }

    /**
     * Drain up to {@code mb} of {@code materialId} from storage. Returns the amount actually
     * drained. No-op (returns 0) while the structure is invalid.
     */
    public int drainFluid(Identifier materialId, int mb) {
        if (!canAccessFluids() || mb <= 0) return 0;
        int have = fluidStorage.getOrDefault(materialId, 0);
        int toDrain = Math.min(mb, have);
        if (toDrain <= 0) return 0;
        int remaining = have - toDrain;
        if (remaining <= 0) fluidStorage.remove(materialId);
        else                 fluidStorage.put(materialId, remaining);
        setChanged();
        return toDrain;
    }

    /**
     * Server-side tick driver. Re-validates on a cadence, then runs the heat simulation
     * against the validated structure. Fluid melting and alloy resolution hook in here
     * once those subsystems exist.
     */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ticksSinceValidation++;
        if (ticksSinceValidation >= VALIDATION_TICK_INTERVAL) {
            ticksSinceValidation = 0;
            validateStructure();
        }

        if (!lastValidation.valid) {
            fueledLastTick = false;
            // No simulation while broken — but let any leftover heat decay toward ambient
            // anyway so we don't get frozen at 1500°C after a wall is broken mid-burn.
            decayTemperature(false);
            return;
        }

        // Tally fuel across all ports for UI + consumption.
        java.util.List<ForgeFuelPortBlockEntity> ports = collectFuelPorts(level);
        totalFuelMb = 0;
        totalFuelCapacityMb = 0;
        for (ForgeFuelPortBlockEntity p : ports) {
            totalFuelMb += p.lavaMb();
            totalFuelCapacityMb += ForgeFuelPortBlockEntity.CAPACITY_MB;
        }
        fueledLastTick = totalFuelMb > 0;

        // Consume fuel while heating or holding temp. Accumulate sub-mB consumption since
        // ports only store ints, then drain the first non-empty port when the debt rolls over.
        if (fueledLastTick) {
            fuelConsumptionAccumulator += FUEL_CONSUMPTION_PER_TICK;
            while (fuelConsumptionAccumulator >= 1f && !ports.isEmpty()) {
                fuelConsumptionAccumulator -= 1f;
                for (ForgeFuelPortBlockEntity p : ports) {
                    if (p.lavaMb() > 0) { p.drainLava(1); totalFuelMb--; break; }
                }
            }
        }

        float target = fueledLastTick ? HEAT_TARGET_LAVA_C : HEAT_AMBIENT_C;
        boolean heating = target > temperatureC;
        float rate = heating ? HEAT_RATE_PER_TICK : COOL_RATE_PER_TICK;
        if (!lastValidation.openTop) {
            // Closed top: 1.2x heating rate, cooling at 1/1.2 the open-top rate.
            rate *= heating ? CLOSED_TOP_FACTOR : (1f / CLOSED_TOP_FACTOR);
        }
        temperatureC += (target - temperatureC) * rate;
        if (temperatureC < HEAT_AMBIENT_C) temperatureC = HEAT_AMBIENT_C;

        markIfTemperatureMoved();

        // ---- Item melting ----
        meltItemsInInterior(level);
    }

    /**
     * Find ItemEntities inside the validated interior, attempt to melt them per their
     * registered MeltingRecipe. The temperature-excess-ratio formula in the design doc
     * scales per-tick progress; once enough mB has accumulated, one item is consumed and
     * its molten contents land in fluid storage.
     */
    private void meltItemsInInterior(ServerLevel level) {
        if (SmitheryAPI.MELTING_RECIPES.isEmpty()) return;

        AABB interiorBox = computeInteriorAabb();
        if (interiorBox == null) return;
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, interiorBox);
        if (entities.isEmpty()) {
            if (!meltProgress.isEmpty()) meltProgress.clear();
            return;
        }

        // Track which entities we still see this tick so we can prune progress for any that
        // left the interior or were destroyed elsewhere.
        Set<UUID> live = new HashSet<>(entities.size());

        for (ItemEntity entity : entities) {
            // Confirm the entity's footprint actually overlaps an interior block (AABB query
            // may include positions adjacent to the interior in non-rectangular forges).
            BlockPos foot = BlockPos.containing(entity.position());
            if (!lastValidation.interior.contains(foot)) continue;

            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;

            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            MeltingRecipe recipe = SmitheryAPI.MELTING_RECIPES.get(itemId);
            if (recipe == null) continue;

            Material material = SmitheryAPI.MATERIALS.get(recipe.outputMaterialId());
            if (material == null) continue;
            MaterialStats stats = material.stats();
            if (temperatureC < stats.meltingTemp()) continue; // too cold for this material

            // No room in the tank for this material? Don't make progress (don't waste heat).
            if (remainingFluidCapacityMb() <= 0) continue;

            live.add(entity.getUUID());
            float meltRate = MELT_BASE_RATE_MB_PER_TICK
                    * (1f + MELT_TEMP_SCALE * (temperatureC - stats.meltingTemp()) / stats.meltingTemp());

            float progress = meltProgress.getOrDefault(entity.getUUID(), 0f) + meltRate;
            while (progress >= recipe.outputMb()) {
                int added = addFluid(recipe.outputMaterialId(), recipe.outputMb());
                if (added <= 0) break; // tank filled mid-batch — stash the rest for next tick
                progress -= recipe.outputMb();
                consumeOneItem(entity);
                if (entity.getItem().isEmpty()) break;
            }
            meltProgress.put(entity.getUUID(), progress);
        }

        // Prune stale entries.
        if (meltProgress.size() != live.size()) {
            Iterator<UUID> it = meltProgress.keySet().iterator();
            while (it.hasNext()) {
                if (!live.contains(it.next())) it.remove();
            }
        }
    }

    private AABB computeInteriorAabb() {
        if (lastValidation.interior.isEmpty()) return null;
        int xMin = Integer.MAX_VALUE, xMax = Integer.MIN_VALUE;
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        int zMin = Integer.MAX_VALUE, zMax = Integer.MIN_VALUE;
        for (BlockPos p : lastValidation.interior) {
            if (p.getX() < xMin) xMin = p.getX(); if (p.getX() > xMax) xMax = p.getX();
            if (p.getY() < yMin) yMin = p.getY(); if (p.getY() > yMax) yMax = p.getY();
            if (p.getZ() < zMin) zMin = p.getZ(); if (p.getZ() > zMax) zMax = p.getZ();
        }
        return new AABB(xMin, yMin, zMin, xMax + 1, yMax + 1, zMax + 1);
    }

    private static void consumeOneItem(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(stack); // re-sync (no-op on most builds but safe)
        }
    }

    private java.util.List<ForgeFuelPortBlockEntity> collectFuelPorts(ServerLevel level) {
        java.util.List<ForgeFuelPortBlockEntity> out = new java.util.ArrayList<>();
        for (BlockPos s : lastValidation.shell) {
            if (level.getBlockState(s).is(SmitheryBlocks.FORGE_FUEL_PORT.get())
                    && level.getBlockEntity(s) instanceof ForgeFuelPortBlockEntity fp) {
                out.add(fp);
            }
        }
        return out;
    }

    /**
     * Force a passive cool-down regardless of validation state. Called when the structure is
     * invalid so any stored heat dissipates instead of being held forever.
     */
    private void decayTemperature(boolean closedTop) {
        float rate = COOL_RATE_PER_TICK;
        if (closedTop) rate /= CLOSED_TOP_FACTOR;
        temperatureC += (HEAT_AMBIENT_C - temperatureC) * rate;
        if (temperatureC < HEAT_AMBIENT_C) temperatureC = HEAT_AMBIENT_C;
        markIfTemperatureMoved();
    }

    private void markIfTemperatureMoved() {
        if (Math.abs(temperatureC - lastSavedTemperatureC) >= TEMP_DIRTY_THRESHOLD) {
            lastSavedTemperatureC = temperatureC;
            setChanged();
        }
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
        return state.is(SmitheryBlocks.FURNACE_BRICKS.get())
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
        temperatureC = input.getFloatOr("temperatureC", HEAT_AMBIENT_C);

        fluidStorage.clear();
        Optional<ValueInput.ValueInputList> fluids = input.childrenList("fluids");
        if (fluids.isPresent()) {
            for (ValueInput entry : fluids.get()) {
                Optional<String> idStr = entry.getString("id");
                int mb = entry.getInt("mb").orElse(0);
                if (idStr.isEmpty() || mb <= 0) continue;
                Identifier id = Identifier.tryParse(idStr.get());
                if (id != null) fluidStorage.put(id, mb);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("temperatureC", temperatureC);

        ValueOutput.ValueOutputList fluids = output.childrenList("fluids");
        for (Map.Entry<Identifier, Integer> e : fluidStorage.entrySet()) {
            if (e.getValue() <= 0) continue;
            ValueOutput entry = fluids.addChild();
            entry.putString("id", e.getKey().toString());
            entry.putInt("mb", e.getValue());
        }
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
