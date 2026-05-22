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

    private static final int MAX_INTERIOR_BLOCKS = 1024;
    private static final int MAX_SHELL_BLOCKS = 2048;
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

    // ---- Fluid storage ----
    private final Map<Identifier, Integer> fluidStorage = new LinkedHashMap<>();

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

        // Tally fuel across all ports.
        List<ForgeFuelPortBlockEntity> ports = collectFuelPorts(level);
        totalFuelMb = 0;
        totalFuelCapacityMb = 0;
        for (ForgeFuelPortBlockEntity p : ports) {
            totalFuelMb += p.lavaMb();
            totalFuelCapacityMb += ForgeFuelPortBlockEntity.CAPACITY_MB;
        }
        fueledLastTick = totalFuelMb > 0;

        // Consume fuel (accumulate sub-mB debt, drain first non-empty port when it rolls over).
        if (fueledLastTick) {
            fuelConsumptionAccumulator += FUEL_CONSUMPTION_PER_TICK;
            while (fuelConsumptionAccumulator >= 1f && !ports.isEmpty()) {
                fuelConsumptionAccumulator -= 1f;
                for (ForgeFuelPortBlockEntity p : ports) {
                    if (p.lavaMb() > 0) { p.drainLava(1); totalFuelMb--; break; }
                }
            }
        }

        // Temperature simulation.
        float target = fueledLastTick ? HEAT_TARGET_LAVA_C : HEAT_AMBIENT_C;
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
            if (shellPool.size() > MAX_SHELL_BLOCKS) {
                lastValidation = ValidationResult.invalid("connected shell too large (>" + MAX_SHELL_BLOCKS + ")");
                return lastValidation;
            }
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
            if (interior.size() > MAX_INTERIOR_BLOCKS) {
                lastValidation = ValidationResult.invalid("interior too large (>" + MAX_INTERIOR_BLOCKS + ")");
                return lastValidation;
            }
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
                || state.is(SmitheryBlocks.FORGE_DRAIN.get());
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

        ValueOutput.ValueOutputList fluids = output.childrenList("fluids");
        for (Map.Entry<Identifier, Integer> e : fluidStorage.entrySet()) {
            if (e.getValue() <= 0) continue;
            ValueOutput entry = fluids.addChild();
            entry.putString("id", e.getKey().toString());
            entry.putInt("mb", e.getValue());
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
