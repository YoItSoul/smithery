package com.soul.smithery.block.entity;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * State + behavior for the Forge multiblock.
 *
 * Interior air blocks serve as inventory slots: items dropped into the forge are
 * absorbed from the world and stored per-slot. The slot list is rebuilt whenever
 * the structure re-validates. Items are melted in place; the client renderer
 * draws each occupied slot's item floating in its air block.
 */
public class ForgeControllerBlockEntity extends BlockEntity implements MenuProvider {

    // Forge size is unlimited by design — there is intentionally no hard cap on shell or
    // interior block count. The validation BFS terminates naturally when no more connected
    // brick/air blocks exist, and the per-tick melting loop only does meaningful work for
    // slots with items in them. The BE renderer is view-distance culled at 64 blocks, and
    // realistic item population is capped by how much source material the player feeds in.
    //
    // The one practical risk of "limitless" is that a player who accidentally connects their
    // forge wall to an existing brick mega-structure will validate the entire connected blob.
    // If that becomes a problem we can revisit with a time-bounded BFS rather than a count cap.
    private static final int VALIDATION_TICK_INTERVAL = 40;

    private static final float HEAT_AMBIENT_C        = 20.0f;
    private static final float HEAT_TARGET_LAVA_C    = 1650.0f;
    private static final float HEAT_RATE_PER_TICK    = 0.0030f;
    private static final float COOL_RATE_PER_TICK    = 0.0010f;
    private static final float CLOSED_TOP_FACTOR     = 1.2f;
    private static final float TEMP_DIRTY_THRESHOLD  = 0.25f;
    private static final float FUEL_CONSUMPTION_PER_TICK = 0.1f;
    private static final float MELT_BASE_RATE_MB_PER_TICK = 1.0f;
    private static final float MELT_TEMP_SCALE             = 2.0f;

    private ValidationResult lastValidation = ValidationResult.invalid("not yet validated");

    // ---- Persistent heat + fuel state ----
    private float temperatureC = HEAT_AMBIENT_C;
    private float lastSavedTemperatureC = HEAT_AMBIENT_C;
    private int   ticksSinceValidation = 0;
    private boolean fueledLastTick = false;
    private float fuelConsumptionAccumulator = 0f;
    private int totalFuelMb = 0;
    private int totalFuelCapacityMb = 0;

    // ---- Player toggle: auto-alloying ----
    // When false, processAlloys() skips firing recipes — useful when the player has all the
    // inputs for a "more-specific" alloy in the forge but actually wants the less-specific
    // result (silver+gold+iron present but they want electrum, not constantan). Toggle via
    // the controller GUI button. Persisted and synced.
    private boolean alloyEnabled = true;

    // ---- Fluid storage ----
    private final Map<Identifier, Integer> fluidStorage = new LinkedHashMap<>();
    /**
     * Player-selected fluid id to route out through drains/pipes. {@code null} until the
     * player clicks one in the GUI. The GUI also re-anchors this entry to the END of
     * {@link #fluidStorage} (LinkedHashMap iteration order) so it renders at the bottom
     * of the molten-metal stack.
     */
    private @Nullable Identifier outputFluidId;

    // ---- Slot-based inventory ----
    // One slot per interior air block. Positions are in deterministic order (Y asc, X asc, Z asc).
    // Rebuilt by validateStructure(); items remapped by absolute position so they survive re-validates.
    private List<BlockPos> slotPositions = List.of();
    private NonNullList<ItemStack> slots = NonNullList.create();
    private float[] meltProgressPerSlot = new float[0];

    // Temp: populated by loadAdditional(), consumed by the next validateStructure() call.
    private final Map<BlockPos, ItemStack> pendingSlotItems = new HashMap<>();
    private final Map<BlockPos, Float>     pendingProgress  = new HashMap<>();

    public ForgeControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_CONTROLLER.get(), pos, state);
    }

    // ---- Accessors ----
    public ValidationResult lastValidation()    { return lastValidation; }
    public float temperatureC()                 { return temperatureC; }
    public boolean isFueled()                   { return fueledLastTick; }
    public boolean isAlloyEnabled()             { return alloyEnabled; }
    public void setAlloyEnabled(boolean v) {
        if (alloyEnabled == v) return;
        alloyEnabled = v;
        setChanged();
    }
    public int totalFuelMb()                    { return totalFuelMb; }
    public int totalFuelCapacityMb()            { return totalFuelCapacityMb; }
    public float targetTemperatureC()           { return fueledLastTick ? HEAT_TARGET_LAVA_C : HEAT_AMBIENT_C; }
    public List<BlockPos> slotPositions()       { return slotPositions; }
    public NonNullList<ItemStack> slots()       { return slots; }

    /** mB of melt progress accrued for slot {@code i}; 0 if invalid index. */
    public int meltProgressMb(int i) {
        return (i >= 0 && i < meltProgressPerSlot.length) ? (int) meltProgressPerSlot[i] : 0;
    }

    // ---- Fluid storage ----
    //
    // Keys are Fluid IDs (e.g. "smithery:molten_iron") — not Material IDs.
    // This matches the FluidStack identity used by IFluidHandler and lets the
    // drain/casting plumbing speak Fluid natively. Translation from Material ID
    // (used by melting recipes) happens at the callsite via SmitheryFluids#forMaterial.

    public boolean canAccessFluids() { return lastValidation.valid; }

    public int fluidCapacityMb() {
        return lastValidation.valid ? lastValidation.capacityMb() : 0;
    }

    public int totalStoredFluidMb() {
        int sum = 0;
        for (int v : fluidStorage.values()) sum += v;
        return sum;
    }

    public int remainingFluidCapacityMb() {
        return Math.max(0, fluidCapacityMb() - totalStoredFluidMb());
    }

    /** Stored mB of the given fluid; 0 if absent or the fluid has no registry key. */
    public int storedFluidMb(net.minecraft.world.level.material.Fluid fluid) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        return id == null ? 0 : fluidStorage.getOrDefault(id, 0);
    }

    /** Read-only view of the storage map keyed by Fluid ID. */
    public Map<Identifier, Integer> fluidStorageView() {
        return Collections.unmodifiableMap(fluidStorage);
    }

    /** Currently-selected output fluid id, or {@code null} if the player hasn't picked one. */
    public @Nullable Identifier outputFluidId() { return outputFluidId; }

    /** mB stored for the currently-selected output fluid; 0 if none selected or none in stock. */
    public int outputFluidMb() {
        return outputFluidId == null ? 0 : fluidStorage.getOrDefault(outputFluidId, 0);
    }

    /**
     * Player picked a fluid in the GUI. Validates it's actually stored, then re-anchors
     * the LinkedHashMap entry so it iterates LAST — that's what positions it at the
     * bottom of the molten-metal stack in the renderer (which draws top-to-bottom in
     * insertion order). Returns true if the selection changed.
     */
    public boolean setOutputFluid(@Nullable Identifier fluidId) {
        if (fluidId != null && !fluidStorage.containsKey(fluidId)) {
            return false;
        }
        boolean changed = !Objects.equals(this.outputFluidId, fluidId);
        this.outputFluidId = fluidId;
        if (fluidId != null) {
            // Move the selected fluid to the end of the LinkedHashMap iteration order.
            // remove + put re-inserts at tail, leaving other entries' relative order intact.
            Integer amount = fluidStorage.remove(fluidId);
            if (amount != null) {
                fluidStorage.put(fluidId, amount);
            }
        }
        if (changed) {
            setChanged();
            if (level instanceof ServerLevel sl) {
                sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return changed;
    }

    /** Adds mB of the given fluid up to remaining capacity. Returns how many mB were actually added. */
    public int addFluid(net.minecraft.world.level.material.Fluid fluid, int mb) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null || !canAccessFluids() || mb <= 0) return 0;
        int toAdd = Math.min(mb, remainingFluidCapacityMb());
        if (toAdd <= 0) return 0;
        fluidStorage.merge(id, toAdd, Integer::sum);
        setChanged();
        return toAdd;
    }

    /**
     * Maps a save-file identifier to the canonical Fluid ID we now key the storage by.
     * Handles both the new format (already a registered Fluid) and the pre-migration
     * format (a Material ID that needs translating to its molten fluid). Returns null
     * if the id can't be resolved either way.
     */
    private static @Nullable Identifier resolveSavedFluidId(Identifier saved) {
        if (net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(saved)) {
            return saved;
        }
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forMaterial(saved);
        if (entry == null) return null;
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
    }

    /** Drains up to {@code mb} of the given fluid. Returns how many mB were actually drained. */
    public int drainFluid(net.minecraft.world.level.material.Fluid fluid, int mb) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null || !canAccessFluids() || mb <= 0) return 0;
        int have = fluidStorage.getOrDefault(id, 0);
        int toDrain = Math.min(mb, have);
        if (toDrain <= 0) return 0;
        int remaining = have - toDrain;
        if (remaining <= 0) fluidStorage.remove(id);
        else                 fluidStorage.put(id, remaining);
        setChanged();
        return toDrain;
    }

    // ---- Server tick ----

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ticksSinceValidation++;
        if (ticksSinceValidation >= VALIDATION_TICK_INTERVAL) {
            ticksSinceValidation = 0;
            validateStructure();
        }

        if (!lastValidation.valid) {
            fueledLastTick = false;
            decayTemperature(false);
            return;
        }

        // Tally fuel across all ports + determine target temperature from the highest-temp
        // fuel present (lava → 1650, molten blaze → 3500, etc — see ForgeFuels registry).
        // A forge with mixed lava+blaze ports climbs to blaze's temperature; once blaze is
        // exhausted it falls back to lava's lower target.
        List<ForgeFuelPortBlockEntity> ports = collectFuelPorts(level);
        totalFuelMb = 0;
        totalFuelCapacityMb = 0;
        float fuelTarget = HEAT_AMBIENT_C;
        for (ForgeFuelPortBlockEntity p : ports) {
            totalFuelMb += p.fuelMb();
            totalFuelCapacityMb += ForgeFuelPortBlockEntity.CAPACITY_MB;
            if (p.fuelMb() <= 0 || p.fuelFluid() == null) continue;
            com.soul.smithery.api.forge.ForgeFuels.Profile profile =
                    com.soul.smithery.api.forge.ForgeFuels.get(p.fuelFluid());
            if (profile != null && profile.targetTemperatureC() > fuelTarget) {
                fuelTarget = profile.targetTemperatureC();
            }
        }
        fueledLastTick = totalFuelMb > 0;

        // Consume fuel from the port currently driving the temperature target (highest-temp
        // fuel runs first; once it's gone the next-hottest takes over for subsequent ticks).
        if (fueledLastTick) {
            fuelConsumptionAccumulator += FUEL_CONSUMPTION_PER_TICK;
            while (fuelConsumptionAccumulator >= 1f && !ports.isEmpty()) {
                fuelConsumptionAccumulator -= 1f;
                // Pick the port with the highest-temp fuel that still has any to burn.
                ForgeFuelPortBlockEntity hot = null;
                float hotTarget = -1f;
                for (ForgeFuelPortBlockEntity p : ports) {
                    if (p.fuelMb() <= 0 || p.fuelFluid() == null) continue;
                    var prof = com.soul.smithery.api.forge.ForgeFuels.get(p.fuelFluid());
                    if (prof == null) continue;
                    if (prof.targetTemperatureC() > hotTarget) {
                        hotTarget = prof.targetTemperatureC();
                        hot = p;
                    }
                }
                if (hot == null) break;
                hot.drainFuel(1);
                totalFuelMb--;
            }
        }

        // Temperature simulation.
        float target = fueledLastTick ? fuelTarget : HEAT_AMBIENT_C;
        boolean heating = target > temperatureC;
        float rate = heating ? HEAT_RATE_PER_TICK : COOL_RATE_PER_TICK;
        if (!lastValidation.openTop) {
            rate *= heating ? CLOSED_TOP_FACTOR : (1f / CLOSED_TOP_FACTOR);
        }
        temperatureC += (target - temperatureC) * rate;
        if (temperatureC < HEAT_AMBIENT_C) temperatureC = HEAT_AMBIENT_C;
        markIfTemperatureMoved();

        // Absorb any item entities that fell into the interior, then melt from slots.
        absorbItemEntities(level);
        meltFromSlots(level);

        // Auto-alloy: scan registered recipes in priority order (more inputs first) and
        // fire any whose preconditions are met. See AlloyRecipes.all() for sort details.
        processAlloys();
    }

    /**
     * Fires every applicable alloy this tick. Recipes execute in priority order (more inputs
     * first); a higher-priority recipe that consumes a shared input prevents a lower-priority
     * one from firing the same tick — the lower-priority recipe's preconditions become unmet
     * after the deduction. Players control conflicts by managing what they put into the forge.
     */
    private void processAlloys() {
        if (!alloyEnabled) return;
        for (com.soul.smithery.api.alloy.AlloyRecipe recipe : com.soul.smithery.api.alloy.AlloyRecipes.all()) {
            if (!recipe.canFire(temperatureC, fluidStorage)) continue;

            // Deduct each input.
            for (com.soul.smithery.api.alloy.AlloyRecipe.Input in : recipe.inputs()) {
                int current = fluidStorage.getOrDefault(in.material(), 0);
                int remaining = current - in.mb();
                if (remaining <= 0) fluidStorage.remove(in.material());
                else fluidStorage.put(in.material(), remaining);
            }
            // Add the output. fluidStorage's LinkedHashMap iteration order is the GUI display
            // order, so newly-introduced alloy outputs appear at the bottom (after the inputs
            // they replaced). Players notice the new entry.
            fluidStorage.merge(recipe.result().material(), recipe.result().mb(), Integer::sum);
            setChanged();
        }
    }

    // ---- Item absorption ----

    /**
     * Pulls ItemEntities inside the forge interior into the nearest empty slot.
     * Once absorbed, the entity is removed from the world.
     */
    private void absorbItemEntities(ServerLevel level) {
        if (slotPositions.isEmpty()) return;
        AABB box = computeInteriorAabb();
        if (box == null) return;

        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, box);
        if (entities.isEmpty()) return;

        boolean changed = false;
        for (ItemEntity entity : entities) {
            if (entity.isRemoved()) continue;
            BlockPos foot = BlockPos.containing(entity.position());
            if (!lastValidation.interior.contains(foot)) continue;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;

            // Forge slots hold at most 1 item each. Pull items off the entity
            // one at a time and into empty slots; if the entity has more than
            // we have room for, the remainder stays as a world entity.
            while (!stack.isEmpty()) {
                int slot = nearestEmptySlot(entity.position());
                if (slot < 0) break; // no empty slots — leave the rest in world
                slots.set(slot, stack.copyWithCount(1));
                stack.shrink(1);
                changed = true;
            }
            if (stack.isEmpty()) entity.discard();
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** True iff the forge is currently valid AND has at least one empty interior slot. */
    public boolean hasEmptyInteriorSlot() {
        if (!lastValidation.valid) return false;
        for (ItemStack s : slots) if (s.isEmpty()) return true;
        return false;
    }

    /**
     * Inserts up to {@code amount} of {@code stack} into the nearest empty interior slot(s).
     * Returns the number actually inserted. Used by the {@link ForgeItemPortBlockEntity} input
     * port for both right-click and hopper insertion paths.
     *
     * <p>Each interior slot holds at most 1 item, so a stack of size N fills up to N empty
     * slots in nearest-to-controller order before bailing.
     */
    public int tryInsertItem(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0 || !lastValidation.valid) return 0;
        int inserted = 0;
        int limit = Math.min(amount, stack.getCount());
        Vec3 origin = Vec3.atCenterOf(worldPosition);
        while (inserted < limit) {
            int slot = nearestEmptySlot(origin);
            if (slot < 0) break;
            slots.set(slot, stack.copyWithCount(1));
            inserted++;
        }
        if (inserted > 0) {
            setChanged();
            if (level instanceof ServerLevel sl) {
                sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return inserted;
    }

    private int nearestEmptySlot(Vec3 pos) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < slotPositions.size(); i++) {
            if (!slots.get(i).isEmpty()) continue;
            BlockPos sp = slotPositions.get(i);
            double d = pos.distanceToSqr(sp.getX() + 0.5, sp.getY() + 0.5, sp.getZ() + 0.5);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }

    // ---- Slot melting ----

    private void meltFromSlots(ServerLevel level) {
        if (slots.isEmpty() || SmitheryAPI.MELTING_RECIPES.isEmpty()) return;

        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) { meltProgressPerSlot[i] = 0f; continue; }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            MeltingRecipe recipe = SmitheryAPI.MELTING_RECIPES.get(itemId);
            if (recipe == null) continue;

            Material material = SmitheryAPI.MATERIALS.get(recipe.outputMaterialId());
            if (material == null) continue;
            MaterialStats stats = material.stats();
            if (temperatureC < stats.meltingTemp()) continue;
            if (remainingFluidCapacityMb() <= 0) continue;

            // Translate the recipe's output Material ID to the registered Fluid for storage.
            // If a material has a melting recipe but no registered fluid, skip — shouldn't
            // happen for built-in materials but is defensive.
            com.soul.smithery.registry.SmitheryFluids.Entry fluidEntry =
                    com.soul.smithery.registry.SmitheryFluids.forMaterial(recipe.outputMaterialId());
            if (fluidEntry == null) continue;
            net.minecraft.world.level.material.Fluid outputFluid = fluidEntry.source.get();

            float meltRate = MELT_BASE_RATE_MB_PER_TICK
                    * (1f + MELT_TEMP_SCALE * (temperatureC - stats.meltingTemp()) / stats.meltingTemp());
            meltProgressPerSlot[i] += meltRate;

            while (meltProgressPerSlot[i] >= recipe.outputMb()) {
                int added = addFluid(outputFluid, recipe.outputMb());
                if (added <= 0) break;
                meltProgressPerSlot[i] -= recipe.outputMb();
                stack.shrink(1);
                changed = true;
                if (stack.isEmpty()) {
                    slots.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- Structure validation ----

    /**
     * Re-validates the multiblock structure. Rebuilds slot positions from the interior
     * whenever the structure is valid. Always call on server side only.
     */
    public ValidationResult validateStructure() {
        if (level == null) {
            lastValidation = ValidationResult.invalid("no level");
            return lastValidation;
        }

        // Phase 1: BFS the connected shell 26-connectedly from the controller.
        Set<BlockPos> shellPool = new HashSet<>();
        List<BlockPos> shellQueue = new ArrayList<>();
        shellQueue.add(worldPosition);
        while (!shellQueue.isEmpty()) {
            BlockPos p = shellQueue.remove(shellQueue.size() - 1);
            if (!shellPool.add(p)) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos n = p.offset(dx, dy, dz);
                if (!shellPool.contains(n) && isShellBlock(level.getBlockState(n))) shellQueue.add(n);
            }
        }
        if (shellPool.size() < 2) {
            lastValidation = ValidationResult.invalid("controller has no adjacent shell — build walls first");
            return lastValidation;
        }

        // Phase 2: shell AABB.
        int xMin = Integer.MAX_VALUE, xMax = Integer.MIN_VALUE;
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        int zMin = Integer.MAX_VALUE, zMax = Integer.MIN_VALUE;
        for (BlockPos s : shellPool) {
            if (s.getX() < xMin) xMin = s.getX(); if (s.getX() > xMax) xMax = s.getX();
            if (s.getY() < yMin) yMin = s.getY(); if (s.getY() > yMax) yMax = s.getY();
            if (s.getZ() < zMin) zMin = s.getZ(); if (s.getZ() > zMax) zMax = s.getZ();
        }
        final int yTopShell = yMax;
        final int bxMin = xMin, bxMax = xMax, byMin = yMin, byMax = yMax, bzMin = zMin, bzMax = zMax;

        // Phase 3: flood-fill the interior, capped by the shell AABB.
        Set<BlockPos> interior = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos seed = worldPosition.relative(d);
            if (inBbox(seed, bxMin, bxMax, byMin, byMax, bzMin, bzMax) && isInteriorCandidate(seed))
                queue.add(seed);
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.remove(queue.size() - 1);
            if (!inBbox(p, bxMin, bxMax, byMin, byMax, bzMin, bzMax)) continue;
            if (!interior.add(p)) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!interior.contains(n) && inBbox(n, bxMin, bxMax, byMin, byMax, bzMin, bzMax) && isInteriorCandidate(n))
                    queue.add(n);
            }
        }
        if (interior.isEmpty()) {
            lastValidation = ValidationResult.invalid(
                    "no interior air adjacent to controller (shell=" + shellPool.size() + " blocks)");
            return lastValidation;
        }

        // Phase 4: classify face neighbors — shell, holes, open top.
        Set<BlockPos> shell = new HashSet<>();
        Set<BlockPos> holePositions = new HashSet<>();
        boolean openTop = false;
        for (BlockPos inside : interior) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = inside.relative(dir);
                if (interior.contains(neighbor)) continue;
                if (dir == Direction.UP && neighbor.getY() > yTopShell) { openTop = true; continue; }
                if (isShellBlock(level.getBlockState(neighbor))) shell.add(neighbor);
                else holePositions.add(neighbor);
            }
        }

        // Phase 5: required port counts.
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

        // Rebuild slot list from the new interior.
        rebuildSlots(interior);

        // Link each drain BE in the shell back to this controller so the drain's fluid
        // capability can passthrough to our storage. Runs every validation pass — cheap,
        // and keeps the link fresh if drains were added/replaced since the last cycle.
        for (BlockPos s : shell) {
            if (level.getBlockState(s).is(SmitheryBlocks.FORGE_DRAIN.get())
                    && level.getBlockEntity(s) instanceof ForgeDrainBlockEntity drain) {
                drain.setControllerPos(worldPosition);
            }
            if (level.getBlockState(s).is(SmitheryBlocks.FORGE_ITEM_PORT.get())
                    && level.getBlockEntity(s) instanceof ForgeItemPortBlockEntity itemPort) {
                itemPort.setControllerPos(worldPosition);
            }
        }

        lastValidation = ValidationResult.valid(
                Collections.unmodifiableSet(interior), Collections.unmodifiableSet(shell),
                openTop, Collections.unmodifiableSet(holePositions));
        return lastValidation;
    }

    /**
     * Sorts interior blocks into a deterministic order and resizes the slot list.
     * Items from the previous slot list (or from pendingSlotItems on load) are remapped
     * by their absolute block position so they survive structure changes — and so is
     * the in-progress melt amount for each slot, since validateStructure() runs every
     * 40 ticks even when the structure is unchanged. Without remapping the float[] of
     * progress, all melts would silently reset to 0 every 2 seconds.
     */
    private void rebuildSlots(Set<BlockPos> interior) {
        List<BlockPos> newPositions = new ArrayList<>(interior);
        newPositions.sort((a, b) -> {
            int c = Long.compare(a.getY(), b.getY());
            if (c != 0) return c;
            c = Long.compare(a.getX(), b.getX());
            return c != 0 ? c : Long.compare(a.getZ(), b.getZ());
        });

        // Snapshot existing items + their melt progress keyed by BlockPos.
        Map<BlockPos, ItemStack> itemByPos     = new HashMap<>();
        Map<BlockPos, Float>     progressByPos = new HashMap<>();
        for (int i = 0; i < slotPositions.size(); i++) {
            ItemStack s = slots.get(i);
            if (s.isEmpty()) continue;
            BlockPos pos = slotPositions.get(i);
            itemByPos.put(pos, s.copy());
            if (i < meltProgressPerSlot.length && meltProgressPerSlot[i] > 0f) {
                progressByPos.put(pos, meltProgressPerSlot[i]);
            }
        }
        // Merge items + progress that were loaded from NBT but not yet assigned.
        itemByPos.putAll(pendingSlotItems);
        progressByPos.putAll(pendingProgress);
        pendingSlotItems.clear();
        pendingProgress.clear();

        NonNullList<ItemStack> newSlots    = NonNullList.withSize(newPositions.size(), ItemStack.EMPTY);
        float[]                newProgress = new float[newPositions.size()];
        for (int i = 0; i < newPositions.size(); i++) {
            BlockPos pos = newPositions.get(i);
            ItemStack item = itemByPos.get(pos);
            if (item == null) continue;
            newSlots.set(i, item);
            Float p = progressByPos.get(pos);
            if (p != null) newProgress[i] = p;
        }

        slotPositions = List.copyOf(newPositions);
        slots = newSlots;
        meltProgressPerSlot = newProgress;
    }

    // ---- Helpers ----

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

    private List<ForgeFuelPortBlockEntity> collectFuelPorts(ServerLevel level) {
        List<ForgeFuelPortBlockEntity> out = new ArrayList<>();
        for (BlockPos s : lastValidation.shell) {
            if (level.getBlockState(s).is(SmitheryBlocks.FORGE_FUEL_PORT.get())
                    && level.getBlockEntity(s) instanceof ForgeFuelPortBlockEntity fp) {
                out.add(fp);
            }
        }
        return out;
    }

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

    private static boolean inBbox(BlockPos p, int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        return p.getX() >= xMin && p.getX() <= xMax
                && p.getY() >= yMin && p.getY() <= yMax
                && p.getZ() >= zMin && p.getZ() <= zMax;
    }

    private boolean isInteriorCandidate(BlockPos pos) {
        return level != null && level.getBlockState(pos).isAir();
    }

    public static boolean isShellBlock(BlockState state) {
        return state.is(SmitheryBlocks.FURNACE_BRICKS.get())
                || state.is(SmitheryBlocks.FORGE_CONTROLLER.get())
                || state.is(SmitheryBlocks.FORGE_FUEL_PORT.get())
                || state.is(SmitheryBlocks.FORGE_DRAIN.get())
                || state.is(SmitheryBlocks.FORGE_ITEM_PORT.get());
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.smithery.forge_controller");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Import resolved at runtime — avoids a circular dependency between BE and GUI packages.
        return new com.soul.smithery.gui.ForgeControllerMenu(containerId, playerInventory, this);
    }

    /**
     * Returns a live {@link Container} view of the forge's item slots. Changes made through this
     * view are written directly into {@link #slots} and trigger {@link #setChanged()}.
     * The slot count equals {@link #slotPositions}{@code .size()} at the time of the call; if the
     * structure is later re-validated with a different interior, a new view should be obtained.
     */
    public Container getSlotContainer() {
        return new Container() {
            @Override public int getContainerSize() { return slots.size(); }
            @Override public boolean isEmpty() { return slots.stream().allMatch(ItemStack::isEmpty); }
            @Override public ItemStack getItem(int i) { return i < slots.size() ? slots.get(i) : ItemStack.EMPTY; }
            @Override public ItemStack removeItem(int i, int count) {
                if (i >= slots.size()) return ItemStack.EMPTY;
                ItemStack result = ContainerHelper.removeItem(slots, i, count);
                if (!result.isEmpty()) {
                    setChanged();
                    if (level instanceof ServerLevel sl)
                        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return result;
            }
            @Override public ItemStack removeItemNoUpdate(int i) {
                return i < slots.size() ? ContainerHelper.takeItem(slots, i) : ItemStack.EMPTY;
            }
            @Override public void setItem(int i, ItemStack stack) {
                if (i >= slots.size()) return;
                slots.set(i, stack);
                setChanged();
                if (level instanceof ServerLevel sl)
                    sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            @Override public void setChanged() { ForgeControllerBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player player) {
                return ContainerLevelAccess.create(level, worldPosition).evaluate(
                    (l, pos) -> player.distanceToSqr(Vec3.atCenterOf(pos)) < 64.0, true);
            }
            @Override public void clearContent() { slots.clear(); setChanged(); }
        };
    }

    // ---- Lifecycle ----

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
        alloyEnabled = input.getBooleanOr("alloyEnabled", true);

        fluidStorage.clear();
        Optional<ValueInput.ValueInputList> fluids = input.childrenList("fluids");
        if (fluids.isPresent()) {
            for (ValueInput entry : fluids.get()) {
                Optional<String> idStr = entry.getString("id");
                int mb = entry.getInt("mb").orElse(0);
                if (idStr.isEmpty() || mb <= 0) continue;
                Identifier id = Identifier.tryParse(idStr.get());
                if (id == null) continue;
                // Storage uses Fluid IDs now. Existing worlds saved Material IDs before
                // the migration — translate those forward by looking up the corresponding
                // molten fluid via SmitheryFluids. Anything that doesn't resolve either
                // way is dropped (was likely a stale/bogus id anyway).
                Identifier resolvedFluidId = resolveSavedFluidId(id);
                if (resolvedFluidId != null) {
                    fluidStorage.merge(resolvedFluidId, mb, Integer::sum);
                }
            }
        }

        outputFluidId = input.getString("outputFluidId").map(Identifier::tryParse).orElse(null);
        // If the persisted output fluid is no longer in storage (e.g. drained dry between
        // saves), null out the selection so the GUI doesn't show a stale tick mark.
        if (outputFluidId != null && !fluidStorage.containsKey(outputFluidId)) {
            outputFluidId = null;
        }

        // Slot items + their in-progress melt amounts are loaded into pending maps and
        // merged into the slot list the next time validateStructure() runs (triggered
        // by onLoad on server side).
        pendingSlotItems.clear();
        pendingProgress.clear();
        Optional<ValueInput.ValueInputList> slotsIn = input.childrenList("slots");
        if (slotsIn.isPresent()) {
            for (ValueInput entry : slotsIn.get()) {
                int x = entry.getInt("x").orElse(0);
                int y = entry.getInt("y").orElse(0);
                int z = entry.getInt("z").orElse(0);
                Optional<String> itemStr = entry.getString("item");
                int count = entry.getInt("count").orElse(1);
                float progress = entry.getFloatOr("progress", 0f);
                if (itemStr.isEmpty()) continue;
                Identifier itemId = Identifier.tryParse(itemStr.get());
                if (itemId == null) continue;
                Item item = BuiltInRegistries.ITEM.get(itemId).<Item>map(r -> r.value()).orElse(null);
                if (item == null || item == Items.AIR) continue;
                BlockPos pos = new BlockPos(x, y, z);
                pendingSlotItems.put(pos, new ItemStack(item, Math.max(1, count)));
                if (progress > 0f) pendingProgress.put(pos, progress);
            }
        }

        // Client side: populate slotPositions + slots directly from pendingSlotItems for the renderer.
        // (On server, validateStructure() handles this; the client doesn't run validateStructure.)
        if (level != null && level.isClientSide()) {
            List<BlockPos> posList = new ArrayList<>(pendingSlotItems.keySet());
            posList.sort((a, b) -> {
                int c = Long.compare(a.getY(), b.getY());
                if (c != 0) return c;
                c = Long.compare(a.getX(), b.getX());
                return c != 0 ? c : Long.compare(a.getZ(), b.getZ());
            });
            NonNullList<ItemStack> itemList = NonNullList.withSize(posList.size(), ItemStack.EMPTY);
            for (int i = 0; i < posList.size(); i++) {
                ItemStack s = pendingSlotItems.get(posList.get(i));
                if (s != null) itemList.set(i, s);
            }
            slotPositions = List.copyOf(posList);
            slots = itemList;
            pendingSlotItems.clear();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("temperatureC", temperatureC);
        output.putBoolean("alloyEnabled", alloyEnabled);

        ValueOutput.ValueOutputList fluids = output.childrenList("fluids");
        for (Map.Entry<Identifier, Integer> e : fluidStorage.entrySet()) {
            if (e.getValue() <= 0) continue;
            ValueOutput entry = fluids.addChild();
            entry.putString("id", e.getKey().toString());
            entry.putInt("mb", e.getValue());
        }

        if (outputFluidId != null) {
            output.putString("outputFluidId", outputFluidId.toString());
        }

        ValueOutput.ValueOutputList slotsOut = output.childrenList("slots");
        for (int i = 0; i < slotPositions.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null) continue;
            BlockPos pos = slotPositions.get(i);
            ValueOutput entry = slotsOut.addChild();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            entry.putString("item", key.toString());
            entry.putInt("count", stack.getCount());
            // Persist in-progress melt so it survives world reloads.
            if (i < meltProgressPerSlot.length && meltProgressPerSlot[i] > 0f) {
                entry.putFloat("progress", meltProgressPerSlot[i]);
            }
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Without this override the BE-data packet ships an empty CompoundTag — the
     * default getUpdateTag() returns `new CompoundTag()` regardless of saveAdditional.
     * That's why the in-world floating items never rendered: the client BE's
     * slotPositions/slots stayed empty even after server-side absorbing.
     */
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    // ---- ValidationResult ----

    public static final class ValidationResult {
        public final boolean valid;
        public final String reason;
        public final Set<BlockPos> interior;
        public final Set<BlockPos> shell;
        public final boolean openTop;
        public final Set<BlockPos> holePositions;

        private ValidationResult(boolean valid, String reason, Set<BlockPos> interior,
                                  Set<BlockPos> shell, boolean openTop, Set<BlockPos> holePositions) {
            this.valid = valid;
            this.reason = reason;
            this.interior = interior;
            this.shell = shell;
            this.openTop = openTop;
            this.holePositions = holePositions;
        }

        public int capacityBuckets() { return interior.size(); }
        public int capacityMb()      { return interior.size() * 1000; }
        public int holes()           { return holePositions.size(); }

        static ValidationResult valid(Set<BlockPos> interior, Set<BlockPos> shell,
                                      boolean openTop, Set<BlockPos> holePositions) {
            return new ValidationResult(true, "", interior, shell, openTop, holePositions);
        }

        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, Set.of(), Set.of(), false, Set.of());
        }
    }
}
