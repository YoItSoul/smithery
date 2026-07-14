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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.UUID;

/**
 * Server-side state and behaviour for the Forge multiblock. Validates the shell on a
 * cadence, treats interior air blocks as per-block inventory slots, melts items in
 * place, accumulates molten fluid storage, drives the alloy and mob-fluid pipelines,
 * and exposes the controller menu for the player GUI.
 */
public class ForgeControllerBlockEntity extends BlockEntity implements MenuProvider {

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

    private float temperatureC = HEAT_AMBIENT_C;
    private float lastSavedTemperatureC = HEAT_AMBIENT_C;
    private int   ticksSinceValidation = 0;
    private boolean fueledLastTick = false;
    private float fuelConsumptionAccumulator = 0f;
    private int totalFuelMb = 0;
    private int totalFuelCapacityMb = 0;

    private boolean alloyEnabled = true;

    private final Map<ResourceLocation, Integer> fluidStorage = new LinkedHashMap<>();
    private @Nullable ResourceLocation outputFluidId;

    private List<BlockPos> slotPositions = List.of();
    private NonNullList<ItemStack> slots = NonNullList.create();
    private float[] meltProgressPerSlot = new float[0];

    private final Map<BlockPos, ItemStack> pendingSlotItems = new HashMap<>();
    private final Map<BlockPos, Float>     pendingProgress  = new HashMap<>();

    private final Map<UUID, Float> trackedMobHealth = new HashMap<>();
    private static final int MOB_FLUID_MB_PER_HIT = 50;
    private static final float MOB_DAMAGE_MIN_TEMP_C = 100f;
    private static final int MOB_DAMAGE_INTERVAL_TICKS = 20;

    /**
     * Constructs a forge controller BE bound to the given position and blockstate.
     */
    public ForgeControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_CONTROLLER.get(), pos, state);
    }

    /** Returns the most recent structure validation result. */
    public ValidationResult lastValidation()    { return lastValidation; }
    /** Returns the current interior temperature in degrees Celsius. */
    public float temperatureC()                 { return temperatureC; }
    /** True iff the forge had any fuel available on the previous tick. */
    public boolean isFueled()                   { return fueledLastTick; }
    /** True iff auto-alloying is enabled via the GUI toggle. */
    public boolean isAlloyEnabled()             { return alloyEnabled; }
    /** Sets the auto-alloy GUI toggle and marks the BE dirty on change. */
    public void setAlloyEnabled(boolean v) {
        if (alloyEnabled == v) return;
        alloyEnabled = v;
        setChanged();
    }
    /** Total fuel currently stored across all fuel ports, in mB. */
    public int totalFuelMb()                    { return totalFuelMb; }
    /** Total combined capacity of all fuel ports, in mB. */
    public int totalFuelCapacityMb()            { return totalFuelCapacityMb; }
    /** Target temperature for the current fuel mix; ambient when unfueled. */
    public float targetTemperatureC()           { return fueledLastTick ? HEAT_TARGET_LAVA_C : HEAT_AMBIENT_C; }
    /** Block positions of all interior slots in deterministic Y/X/Z order. */
    public List<BlockPos> slotPositions()       { return slotPositions; }
    /** Backing item list, indexed parallel to {@link #slotPositions()}. */
    public NonNullList<ItemStack> slots()       { return slots; }

    /**
     * Returns the integer mB of melt progress accrued for slot {@code i}, or 0 if the
     * index is out of range.
     */
    public int meltProgressMb(int i) {
        return (i >= 0 && i < meltProgressPerSlot.length) ? (int) meltProgressPerSlot[i] : 0;
    }

    /** True iff the forge is currently in a valid structural state and may store fluids. */
    public boolean canAccessFluids() { return lastValidation.valid; }

    /** Total mB capacity for molten fluid (one block of interior = 1000 mB). */
    public int fluidCapacityMb() {
        return lastValidation.valid ? lastValidation.capacityMb() : 0;
    }

    /** Sum of stored mB across every fluid currently in the forge. */
    public int totalStoredFluidMb() {
        int sum = 0;
        for (int v : fluidStorage.values()) sum += v;
        return sum;
    }

    /** Free fluid capacity remaining, clamped to non-negative. */
    public int remainingFluidCapacityMb() {
        return Math.max(0, fluidCapacityMb() - totalStoredFluidMb());
    }

    /** Returns the mB stored for the given fluid; 0 if absent or unregistered. */
    public int storedFluidMb(net.minecraft.world.level.material.Fluid fluid) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        return id == null ? 0 : fluidStorage.getOrDefault(id, 0);
    }

    /** Read-only view of the fluid storage map keyed by Fluid id. */
    public Map<ResourceLocation, Integer> fluidStorageView() {
        return Collections.unmodifiableMap(fluidStorage);
    }

    /** Currently-selected output fluid id, or null if none picked in the GUI. */
    public @Nullable ResourceLocation outputFluidId() { return outputFluidId; }

    /** mB stored for the currently-selected output fluid; 0 if none. */
    public int outputFluidMb() {
        return outputFluidId == null ? 0 : fluidStorage.getOrDefault(outputFluidId, 0);
    }

    /**
     * Selects {@code fluidId} as the output fluid and re-anchors its storage entry to the
     * end of iteration order so the GUI renders it at the bottom of the stack. Returns
     * true if the selection changed.
     */
    public boolean setOutputFluid(@Nullable ResourceLocation fluidId) {
        if (fluidId != null && !fluidStorage.containsKey(fluidId)) {
            return false;
        }
        boolean changed = !Objects.equals(this.outputFluidId, fluidId);
        this.outputFluidId = fluidId;
        if (fluidId != null) {
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

    /** Adds up to {@code mb} of the given fluid, returning the amount actually accepted. */
    public int addFluid(net.minecraft.world.level.material.Fluid fluid, int mb) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null || !canAccessFluids() || mb <= 0) return 0;
        int toAdd = Math.min(mb, remainingFluidCapacityMb());
        if (toAdd <= 0) return 0;
        fluidStorage.merge(id, toAdd, Integer::sum);
        setChanged();
        return toAdd;
    }

    private static @Nullable ResourceLocation resolveSavedFluidId(ResourceLocation saved) {
        if (net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(saved)) {
            return saved;
        }
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forMaterial(saved);
        if (entry == null) return null;
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
    }

    /** Drains up to {@code mb} of the given fluid, returning the amount actually removed. */
    public int drainFluid(net.minecraft.world.level.material.Fluid fluid, int mb) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
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

    /**
     * Drives the full server-side tick: periodic structure revalidation, fuel tally and
     * consumption, temperature simulation, item entity absorption, mob scalding, in-place
     * melting, and the alloy pipeline.
     */
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

        if (fueledLastTick) {
            fuelConsumptionAccumulator += FUEL_CONSUMPTION_PER_TICK;
            while (fuelConsumptionAccumulator >= 1f && !ports.isEmpty()) {
                fuelConsumptionAccumulator -= 1f;
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
                hot.drainFuelFromStack(hot.fuelFluid(), 1);
                totalFuelMb--;
            }
        }

        float target = fueledLastTick ? fuelTarget : HEAT_AMBIENT_C;
        boolean heating = target > temperatureC;
        float rate = heating ? HEAT_RATE_PER_TICK : COOL_RATE_PER_TICK;
        if (!lastValidation.openTop) {
            rate *= heating ? CLOSED_TOP_FACTOR : (1f / CLOSED_TOP_FACTOR);
        }
        temperatureC += (target - temperatureC) * rate;
        if (temperatureC < HEAT_AMBIENT_C) temperatureC = HEAT_AMBIENT_C;
        markIfTemperatureMoved();

        absorbItemEntities(level);
        absorbMobFluids(level);
        meltFromSlots(level);

        processAlloys();
    }

    private void processAlloys() {
        if (!alloyEnabled) return;
        for (com.soul.smithery.api.alloy.AlloyRecipe recipe : com.soul.smithery.api.alloy.AlloyRecipes.all()) {
            if (temperatureC < recipe.minTemperatureC()) continue;

            int n = recipe.inputs().size();
            ResourceLocation[] inputFluidIds = new ResourceLocation[n];
            boolean canFire = true;
            for (int i = 0; i < n; i++) {
                com.soul.smithery.api.alloy.AlloyRecipe.Input in = recipe.inputs().get(i);
                ResourceLocation fluidId = materialToFluidId(in.material());
                if (fluidId == null) { canFire = false; break; }
                if (fluidStorage.getOrDefault(fluidId, 0) < in.mb()) { canFire = false; break; }
                inputFluidIds[i] = fluidId;
            }
            if (!canFire) continue;

            ResourceLocation outputFluidId = materialToFluidId(recipe.result().material());
            if (outputFluidId == null) continue;

            for (int i = 0; i < n; i++) {
                com.soul.smithery.api.alloy.AlloyRecipe.Input in = recipe.inputs().get(i);
                ResourceLocation fluidId = inputFluidIds[i];
                int remaining = fluidStorage.getOrDefault(fluidId, 0) - in.mb();
                if (remaining <= 0) fluidStorage.remove(fluidId);
                else fluidStorage.put(fluidId, remaining);
            }
            fluidStorage.merge(outputFluidId, recipe.result().mb(), Integer::sum);
            setChanged();
        }
    }

    private static @Nullable ResourceLocation materialToFluidId(ResourceLocation materialId) {
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forMaterial(materialId);
        if (entry == null) return null;
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
    }

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

            while (!stack.isEmpty()) {
                int slot = nearestEmptySlot(entity.position());
                if (slot < 0) break;
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

    private void absorbMobFluids(ServerLevel level) {
        if (temperatureC < MOB_DAMAGE_MIN_TEMP_C) { trackedMobHealth.clear(); return; }
        AABB box = computeInteriorAabb();
        if (box == null) { trackedMobHealth.clear(); return; }
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, box);
        if (mobs.isEmpty()) { trackedMobHealth.clear(); return; }

        boolean applyDamageThisTick = (level.getGameTime() % MOB_DAMAGE_INTERVAL_TICKS) == 0L;
        var damageSource = level.damageSources().inFire();

        Set<UUID> present = new HashSet<>();
        for (LivingEntity entity : mobs) {
            if (entity instanceof Player) continue;
            if (!entity.isAlive()) continue;
            BlockPos foot = BlockPos.containing(entity.position());
            if (!lastValidation.interior.contains(foot)) continue;

            UUID id = entity.getUUID();
            present.add(id);

            if (applyDamageThisTick) {
                entity.hurt(damageSource, 1.0f);
                if (!entity.isAlive()) continue;
            }

            float currentHp = entity.getHealth();
            Float prevHp = trackedMobHealth.get(id);
            if (prevHp != null && currentHp < prevHp - 0.01f) {
                net.minecraft.world.level.material.Fluid fluid = fluidForEntity(entity);
                if (fluid != null) addFluid(fluid, MOB_FLUID_MB_PER_HIT);
            }
            trackedMobHealth.put(id, currentHp);
        }
        trackedMobHealth.keySet().retainAll(present);
    }

    private static net.minecraft.world.level.material.@Nullable Fluid fluidForEntity(LivingEntity entity) {
        ResourceLocation materialId = com.soul.smithery.api.forge.ForgeMobDrops.materialFor(entity);
        if (materialId == null) return null;
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forMaterial(materialId);
        return entry == null ? null : entry.source.get();
    }

    /** True iff the forge is currently valid and has at least one empty interior slot. */
    public boolean hasEmptyInteriorSlot() {
        if (!lastValidation.valid) return false;
        for (ItemStack s : slots) if (s.isEmpty()) return true;
        return false;
    }

    /**
     * Inserts up to {@code amount} copies of {@code stack} into the nearest empty interior
     * slots (one per slot, smallest-distance-first from the controller). Returns the count
     * actually inserted.
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

    private void meltFromSlots(ServerLevel level) {
        if (slots.isEmpty() || SmitheryAPI.MELTING_RECIPES.isEmpty()) return;

        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) { meltProgressPerSlot[i] = 0f; continue; }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            MeltingRecipe recipe = SmitheryAPI.MELTING_RECIPES.get(itemId);
            if (recipe == null) continue;

            Material material = SmitheryAPI.MATERIALS.get(recipe.outputMaterialId());
            if (material == null) continue;
            MaterialStats stats = material.stats();
            if (temperatureC < stats.meltingTemp()) continue;
            if (remainingFluidCapacityMb() <= 0) continue;

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

    /**
     * Re-runs the full multiblock validation: BFS the shell, flood-fill the interior,
     * classify face neighbours, count required ports, and rebuild the slot list. Always
     * call server-side only. Returns the new {@link ValidationResult}.
     */
    public ValidationResult validateStructure() {
        if (level == null) {
            lastValidation = ValidationResult.invalid("no level");
            return lastValidation;
        }

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

        rebuildSlots(interior);

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

    private void rebuildSlots(Set<BlockPos> interior) {
        List<BlockPos> newPositions = new ArrayList<>(interior);
        newPositions.sort((a, b) -> {
            int c = Long.compare(a.getY(), b.getY());
            if (c != 0) return c;
            c = Long.compare(a.getX(), b.getX());
            return c != 0 ? c : Long.compare(a.getZ(), b.getZ());
        });

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

    /**
     * True iff {@code state} is one of the shell blocks the multiblock recognises:
     * furnace bricks, the controller itself, fuel ports, drains, or item ports.
     */
    public static boolean isShellBlock(BlockState state) {
        return state.is(SmitheryBlocks.FURNACE_BRICKS.get())
                || state.is(SmitheryBlocks.FORGE_CONTROLLER.get())
                || state.is(SmitheryBlocks.FORGE_FUEL_PORT.get())
                || state.is(SmitheryBlocks.FORGE_DRAIN.get())
                || state.is(SmitheryBlocks.FORGE_ITEM_PORT.get());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.smithery.forge_controller");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.soul.smithery.gui.ForgeControllerMenu(containerId, playerInventory, this);
    }

    /**
     * Live container view over the forge's interior slots. Mutations write through to
     * {@link #slots} and trigger {@link #setChanged()}; the slot count is fixed at the
     * call site, so re-obtain after structure re-validation.
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
                ResourceLocation id = ResourceLocation.tryParse(idStr.get());
                if (id == null) continue;
                ResourceLocation resolvedFluidId = resolveSavedFluidId(id);
                if (resolvedFluidId != null) {
                    fluidStorage.merge(resolvedFluidId, mb, Integer::sum);
                }
            }
        }

        outputFluidId = input.getString("outputFluidId").map(ResourceLocation::tryParse).orElse(null);
        if (outputFluidId != null && !fluidStorage.containsKey(outputFluidId)) {
            outputFluidId = null;
        }

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
                ResourceLocation itemId = ResourceLocation.tryParse(itemStr.get());
                if (itemId == null) continue;
                Item item = BuiltInRegistries.ITEM.get(itemId).<Item>map(r -> r.value()).orElse(null);
                if (item == null || item == Items.AIR) continue;
                BlockPos pos = new BlockPos(x, y, z);
                pendingSlotItems.put(pos, new ItemStack(item, Math.max(1, count)));
                if (progress > 0f) pendingProgress.put(pos, progress);
            }
        }

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
        for (Map.Entry<ResourceLocation, Integer> e : fluidStorage.entrySet()) {
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
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null) continue;
            BlockPos pos = slotPositions.get(i);
            ValueOutput entry = slotsOut.addChild();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            entry.putString("item", key.toString());
            entry.putInt("count", stack.getCount());
            if (i < meltProgressPerSlot.length && meltProgressPerSlot[i] > 0f) {
                entry.putFloat("progress", meltProgressPerSlot[i]);
            }
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /**
     * Snapshot of the structural validation pass: valid flag, failure reason, the interior
     * and shell position sets, whether the top is open, and any hole positions that
     * prevented validation. Returned by {@link #validateStructure()} and replayed via
     * {@link #lastValidation()}.
     */
    public static final class ValidationResult {
        /** True iff the structure currently passes all multiblock checks. */
        public final boolean valid;
        /** Human-readable reason for the most recent validation failure ({@code ""} on success). */
        public final String reason;
        /** Interior air positions that form the inventory / fluid volume. */
        public final Set<BlockPos> interior;
        /** Shell positions enclosing the interior. */
        public final Set<BlockPos> shell;
        /** True iff the structure has no shell block above its interior. */
        public final boolean openTop;
        /** Positions where the shell is breached; flashed to the player for debugging. */
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

        /** Interior block count, equal to fluid capacity in buckets. */
        public int capacityBuckets() { return interior.size(); }
        /** Interior block count times 1000, the total fluid capacity in mB. */
        public int capacityMb()      { return interior.size() * 1000; }
        /** Number of hole positions detected this pass. */
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
