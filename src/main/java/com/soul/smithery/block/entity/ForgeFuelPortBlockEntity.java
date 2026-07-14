package com.soul.smithery.block.entity;

import com.soul.smithery.api.forge.ForgeFuels;
import com.soul.smithery.block.ForgeFuelPortBlock;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal fuel tank for a Forge fuel port. Stores one registered {@link ForgeFuels}
 * fluid at a time and groups vertically with neighbouring ports of the same fluid into
 * a logical stack — operations like fill, drain, and connectivity refresh treat each
 * stack as a single tank, while per-port state remains the source of truth.
 */
public class ForgeFuelPortBlockEntity extends BlockEntity {

    /** Per-port mB capacity. */
    public static final int CAPACITY_MB = 6000;

    private @Nullable Fluid fuelFluid = null;
    private int fuelMb = 0;

    private static final int SYNC_MB_THRESHOLD = 60;
    private int lastSyncedMb = 0;
    private @Nullable Fluid lastSyncedFluid = null;

    /**
     * Constructs a fuel port BE bound to the given position and blockstate.
     */
    public ForgeFuelPortBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_FUEL_PORT.get(), pos, state);
    }

    /** mB currently stored in this single port. */
    public int fuelMb() { return fuelMb; }
    /** Fluid currently stored, or null when empty. */
    public @Nullable Fluid fuelFluid() { return fuelFluid; }
    /** Free capacity remaining in this single port. */
    public int remainingCapacityMb() { return CAPACITY_MB - fuelMb; }

    /**
     * Returns the block-light emission for this port, scaled 0..15 from the fill ratio.
     */
    public int lightLevel() {
        if (fuelMb <= 0) return 0;
        return Math.min(15, 1 + (int) Math.floor(14.0 * fuelMb / CAPACITY_MB));
    }

    /** Legacy lava-only accessor: returns stored mB when the fluid is lava, else 0. */
    public int lavaMb() { return fuelFluid == Fluids.LAVA ? fuelMb : 0; }

    /**
     * Adds fuel to this single port, clamped by capacity and rejected when the port
     * already holds a different fluid. Returns the mB actually added.
     */
    public int addFuel(Fluid fluid, int mb) {
        if (fluid == null || mb <= 0) return 0;
        if (!ForgeFuels.isFuel(fluid)) return 0;
        if (fuelFluid != null && fuelFluid != fluid) return 0;
        int added = Math.min(mb, remainingCapacityMb());
        if (added > 0) {
            if (fuelFluid == null) fuelFluid = fluid;
            fuelMb += added;
            setChanged();
            maybeSyncToClient();
        }
        return added;
    }

    /**
     * Drains up to {@code mb} from this single port and resets the fluid identity when
     * fully drained. Returns the mB actually removed.
     */
    public int drainFuel(int mb) {
        int drained = Math.min(mb, fuelMb);
        if (drained > 0) {
            fuelMb -= drained;
            if (fuelMb <= 0) {
                fuelMb = 0;
                fuelFluid = null;
            }
            setChanged();
            maybeSyncToClient();
        }
        return drained;
    }

    private void maybeSyncToClient() {
        if (!(level instanceof ServerLevel sl)) return;
        boolean fluidChanged = fuelFluid != lastSyncedFluid;
        boolean edgeCrossed  = (fuelMb == 0) != (lastSyncedMb == 0);
        boolean bigDelta     = Math.abs(fuelMb - lastSyncedMb) >= SYNC_MB_THRESHOLD;
        if (fluidChanged || edgeCrossed || bigDelta) {
            int prevLight = lastSyncedFluid == null && lastSyncedMb == 0
                    ? 0
                    : Math.min(15, 1 + (int) Math.floor(14.0 * lastSyncedMb / CAPACITY_MB));
            lastSyncedMb = fuelMb;
            lastSyncedFluid = fuelFluid;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (prevLight != lightLevel()) {
                sl.getLightEngine().checkBlock(worldPosition);
            }
        }
    }

    /** Legacy lava-only drain that no-ops when the port holds a non-lava fuel. */
    public int drainLava(int mb) {
        if (fuelFluid != Fluids.LAVA) return 0;
        return drainFuel(mb);
    }

    private static List<ForgeFuelPortBlockEntity> collectPhysicalColumn(Level level, BlockPos pos) {
        List<ForgeFuelPortBlockEntity> column = new ArrayList<>();
        BlockPos cursor = pos;
        while (level.getBlockEntity(cursor.below()) instanceof ForgeFuelPortBlockEntity) {
            cursor = cursor.below();
        }
        while (level.getBlockEntity(cursor) instanceof ForgeFuelPortBlockEntity fp) {
            column.add(fp);
            cursor = cursor.above();
        }
        return column;
    }

    private static @Nullable Fluid groupFluidAt(List<ForgeFuelPortBlockEntity> column, int i) {
        ForgeFuelPortBlockEntity p = column.get(i);
        if (p.fuelFluid != null) return p.fuelFluid;
        for (int j = i - 1; j >= 0; j--) {
            if (column.get(j).fuelFluid != null) return column.get(j).fuelFluid;
        }
        for (int j = i + 1; j < column.size(); j++) {
            if (column.get(j).fuelFluid != null) return column.get(j).fuelFluid;
        }
        return null;
    }

    /**
     * Returns the contiguous run of fuel ports sharing the same group fluid as
     * {@code pos}, bottom-to-top. Multi-fluid columns split into separate stacks.
     */
    public static List<ForgeFuelPortBlockEntity> collectStack(Level level, BlockPos pos) {
        List<ForgeFuelPortBlockEntity> column = collectPhysicalColumn(level, pos);
        if (column.isEmpty()) return List.of();
        int idx = -1;
        for (int i = 0; i < column.size(); i++) {
            if (column.get(i).worldPosition.equals(pos)) { idx = i; break; }
        }
        if (idx < 0) return List.of();
        Fluid mine = groupFluidAt(column, idx);
        int lo = idx, hi = idx;
        while (lo > 0 && groupFluidAt(column, lo - 1) == mine) lo--;
        while (hi < column.size() - 1 && groupFluidAt(column, hi + 1) == mine) hi++;
        return new ArrayList<>(column.subList(lo, hi + 1));
    }

    private List<ForgeFuelPortBlockEntity> stack() {
        return level == null ? List.of(this) : collectStack(level, worldPosition);
    }

    /**
     * Gravity-settles the logical stack at {@code pos} by totalling its fuel and
     * re-pouring it from the bottom up, eliminating any empty gaps below filled ports.
     */
    public static void settleStack(Level level, BlockPos pos) {
        List<ForgeFuelPortBlockEntity> stack = collectStack(level, pos);
        if (stack.size() < 2) return;
        Fluid fluid = null;
        int total = 0;
        for (ForgeFuelPortBlockEntity p : stack) {
            if (p.fuelFluid != null && p.fuelMb > 0) {
                fluid = p.fuelFluid;
                total += p.fuelMb;
            }
        }
        if (total <= 0) return;
        boolean identityChanged = false;
        int remaining = total;
        for (ForgeFuelPortBlockEntity p : stack) {
            int put = Math.min(remaining, CAPACITY_MB);
            Fluid newFluid = put > 0 ? fluid : null;
            if (p.fuelFluid != newFluid || p.fuelMb != put) {
                if ((p.fuelFluid == null) != (newFluid == null)) identityChanged = true;
                p.fuelFluid = newFluid;
                p.fuelMb = put;
                p.setChanged();
                p.maybeSyncToClient();
            }
            remaining -= put;
        }
        if (identityChanged) refreshConnectivity(level, pos);
    }

    /**
     * Rewrites CONNECTED_UP/DOWN on every port of the physical column containing
     * {@code pos} so the flags reflect fluid-group boundaries rather than raw adjacency.
     */
    public static void refreshConnectivity(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return;
        List<ForgeFuelPortBlockEntity> column = collectPhysicalColumn(level, pos);
        if (column.isEmpty()) return;
        Fluid[] groups = new Fluid[column.size()];
        for (int i = 0; i < column.size(); i++) groups[i] = groupFluidAt(column, i);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int i = 0; i < column.size(); i++) {
            ForgeFuelPortBlockEntity p = column.get(i);
            BlockState s = p.getBlockState();
            if (!(s.getBlock() instanceof ForgeFuelPortBlock)) continue;
            boolean down = i > 0 && groups[i] == groups[i - 1];
            boolean up   = i < column.size() - 1 && groups[i] == groups[i + 1];
            BlockState ns = s
                    .setValue(ForgeFuelPortBlock.CONNECTED_UP, up)
                    .setValue(ForgeFuelPortBlock.CONNECTED_DOWN, down);
            if (ns != s) level.setBlock(p.worldPosition, ns, flags);
        }
    }

    /**
     * Bottom-up fill across the logical stack. Each port either accepts (matching fluid
     * or empty) or passes the request upward. Returns the mB actually added.
     */
    public int addFuelToStack(Fluid fluid, int mb) {
        if (fluid == null || mb <= 0) return 0;
        int remaining = mb;
        boolean identityChanged = false;
        List<ForgeFuelPortBlockEntity> stack = stack();
        for (ForgeFuelPortBlockEntity p : stack) {
            if (remaining <= 0) break;
            boolean wasEmpty = p.fuelFluid == null;
            remaining -= p.addFuel(fluid, remaining);
            if (wasEmpty && p.fuelFluid != null) identityChanged = true;
        }
        if (identityChanged && level != null) refreshConnectivity(level, worldPosition);
        return mb - remaining;
    }

    /**
     * Top-down drain across the logical stack regardless of fluid identity. Returns the
     * mB actually removed.
     */
    public int drainFuelFromStack(int mb) {
        if (mb <= 0) return 0;
        int remaining = mb;
        List<ForgeFuelPortBlockEntity> stack = stack();
        boolean identityChanged = false;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (remaining <= 0) break;
            ForgeFuelPortBlockEntity p = stack.get(i);
            boolean wasFilled = p.fuelFluid != null;
            remaining -= p.drainFuel(remaining);
            if (wasFilled && p.fuelFluid == null) identityChanged = true;
        }
        if (identityChanged && level != null) refreshConnectivity(level, worldPosition);
        return mb - remaining;
    }

    /** Top-down drain restricted to ports currently holding {@code fluid}. */
    public int drainFuelFromStack(Fluid fluid, int mb) {
        if (fluid == null || mb <= 0) return 0;
        int remaining = mb;
        List<ForgeFuelPortBlockEntity> stack = stack();
        boolean identityChanged = false;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (remaining <= 0) break;
            ForgeFuelPortBlockEntity p = stack.get(i);
            if (p.fuelFluid != fluid) continue;
            remaining -= p.drainFuel(remaining);
            if (p.fuelFluid == null) identityChanged = true;
        }
        if (identityChanged && level != null) refreshConnectivity(level, worldPosition);
        return mb - remaining;
    }

    /** Stack-aware lava drain that only touches lava-holding ports. */
    public int drainLavaFromStack(int mb) {
        return drainFuelFromStack(Fluids.LAVA, mb);
    }

    /** Total stored fuel across the logical stack (any fluid). */
    public int stackFuelMb() {
        int total = 0;
        for (ForgeFuelPortBlockEntity p : stack()) total += p.fuelMb;
        return total;
    }

    /** Total capacity across the logical stack. */
    public int stackCapacityMb() {
        return CAPACITY_MB * stack().size();
    }

    /** Total stored lava across the stack; skips ports holding other fuels. */
    public int stackLavaMb() {
        int total = 0;
        for (ForgeFuelPortBlockEntity p : stack()) {
            if (p.fuelFluid == Fluids.LAVA) total += p.fuelMb;
        }
        return total;
    }

    /** Total stored mB of a specific fluid across the stack. */
    public int stackFuelMb(Fluid fluid) {
        if (fluid == null) return 0;
        int total = 0;
        for (ForgeFuelPortBlockEntity p : stack()) {
            if (p.fuelFluid == fluid) total += p.fuelMb;
        }
        return total;
    }

    /**
     * Returns the fluid in the topmost non-empty port of the stack, or null if the stack
     * is entirely empty.
     */
    public @Nullable Fluid topStackFluid() {
        List<ForgeFuelPortBlockEntity> stack = stack();
        for (int i = stack.size() - 1; i >= 0; i--) {
            ForgeFuelPortBlockEntity p = stack.get(i);
            if (p.fuelMb > 0 && p.fuelFluid != null) return p.fuelFluid;
        }
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        java.util.Optional<String> fluidIdStr = input.getString("fuelFluid");
        if (fluidIdStr.isPresent()) {
            ResourceLocation id = ResourceLocation.tryParse(fluidIdStr.get());
            fuelFluid = id == null ? null
                    : BuiltInRegistries.FLUID.get(id).<Fluid>map(h -> h.value()).orElse(null);
            fuelMb = Math.max(0, Math.min(CAPACITY_MB, input.getInt("fuelMb").orElse(0)));
            if (fuelFluid == null) fuelMb = 0;
        } else {
            int legacy = input.getInt("lavaMb").orElse(0);
            if (legacy > 0) {
                fuelFluid = Fluids.LAVA;
                fuelMb = Math.min(CAPACITY_MB, legacy);
            } else {
                fuelFluid = null;
                fuelMb = 0;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (fuelFluid != null && fuelMb > 0) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fuelFluid);
            if (id != null) {
                output.putString("fuelFluid", id.toString());
                output.putInt("fuelMb", fuelMb);
            }
        }
    }

    /**
     * Returns a fluid capability that surfaces the entire logical stack as a single tank.
     * Inserts are accepted for any registered forge fuel matching the tank's current
     * identity; extracts can be unfiltered or fluid-specific.
     */
    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new FuelPortHandler();
    }

    private final class FuelPortHandler implements ResourceHandler<FluidResource> {
        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int slot) {
            for (ForgeFuelPortBlockEntity p : stack()) {
                if (p.fuelFluid != null && p.fuelMb > 0) return FluidResource.of(p.fuelFluid);
            }
            return FluidResource.EMPTY;
        }

        @Override public long getAmountAsLong(int slot) { return stackFuelMb(); }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            return stackCapacityMb();
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            if (resource.isEmpty()) return false;
            if (!ForgeFuels.isFuel(resource.getFluid())) return false;
            for (ForgeFuelPortBlockEntity p : stack()) {
                if (p.fuelFluid == null || p.fuelFluid == resource.getFluid()) return true;
            }
            return false;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || resource.isEmpty() || amount <= 0) return 0;
            return addFuelToStack(resource.getFluid(), amount);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || amount <= 0) return 0;
            if (resource.isEmpty()) return drainFuelFromStack(amount);
            return drainFuelFromStack(resource.getFluid(), amount);
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
