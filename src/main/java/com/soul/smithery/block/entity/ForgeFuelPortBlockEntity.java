package com.soul.smithery.block.entity;

import com.soul.smithery.api.forge.ForgeFuels;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Internal fuel tank for a Forge fuel port. Now fluid-agnostic — accepts any fluid registered
 * in {@link ForgeFuels}. The port stores ONE fluid at a time; insert calls with a different
 * fluid are rejected until the existing one is drained.
 *
 * <h3>Multiple fuels in one structure</h3>
 * A forge can have multiple fuel ports, each potentially holding different registered fuels.
 * The controller picks the highest target temperature across all ports that have fuel, so
 * you can keep both a lava-filled port and a molten-blaze-filled port and the forge heats
 * to the blaze target while burning either as available.
 *
 * <h3>Backward compat (saves)</h3>
 * Old saves stored only {@code lavaMb}. On load, that legacy field migrates into the new
 * (fuelFluid = lava, fuelMb = old value) shape transparently.
 */
public class ForgeFuelPortBlockEntity extends BlockEntity {

    public static final int CAPACITY_MB = 6000;

    /** The fluid currently stored, or {@code null} when the port is empty. */
    private @Nullable Fluid fuelFluid = null;
    private int fuelMb = 0;

    public ForgeFuelPortBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_FUEL_PORT.get(), pos, state);
    }

    public int fuelMb() { return fuelMb; }
    public @Nullable Fluid fuelFluid() { return fuelFluid; }
    public int remainingCapacityMb() { return CAPACITY_MB - fuelMb; }

    // Legacy alias for callers still asking "how much lava is in there" — returns the stored
    // amount only when the fluid is actually lava, else 0. Lets the controller's existing
    // lava-aware code paths continue working while we migrate to fluid-agnostic semantics.
    public int lavaMb() { return fuelFluid == Fluids.LAVA ? fuelMb : 0; }

    /** Returns the amount actually added (clamped by remaining capacity, and constrained
     *  to the currently-stored fuel if non-empty). */
    public int addFuel(Fluid fluid, int mb) {
        if (fluid == null || mb <= 0) return 0;
        if (!ForgeFuels.isFuel(fluid)) return 0;
        if (fuelFluid != null && fuelFluid != fluid) return 0;
        int added = Math.min(mb, remainingCapacityMb());
        if (added > 0) {
            if (fuelFluid == null) fuelFluid = fluid;
            fuelMb += added;
            setChanged();
        }
        return added;
    }

    /** Returns the amount actually drained. Resets the fuel fluid to null at empty. */
    public int drainFuel(int mb) {
        int drained = Math.min(mb, fuelMb);
        if (drained > 0) {
            fuelMb -= drained;
            if (fuelMb <= 0) {
                fuelMb = 0;
                fuelFluid = null;
            }
            setChanged();
        }
        return drained;
    }

    /** Legacy alias: drain lava specifically (no-op if the port holds a non-lava fuel). */
    public int drainLava(int mb) {
        if (fuelFluid != Fluids.LAVA) return 0;
        return drainFuel(mb);
    }

    // ---- NBT ----

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // New shape: fuelFluid id + fuelMb. Legacy: lavaMb only.
        java.util.Optional<String> fluidIdStr = input.getString("fuelFluid");
        if (fluidIdStr.isPresent()) {
            Identifier id = Identifier.tryParse(fluidIdStr.get());
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
            Identifier id = BuiltInRegistries.FLUID.getKey(fuelFluid);
            if (id != null) {
                output.putString("fuelFluid", id.toString());
                output.putInt("fuelMb", fuelMb);
            }
        }
    }

    // ---- Fluid capability exposure ----
    //
    // Now accepts any registered fuel fluid. Insert rejects fluids not in ForgeFuels, AND
    // rejects fluids that don't match the currently-stored one (to keep the tank single-fluid).

    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new FuelPortHandler();
    }

    private final class FuelPortHandler implements ResourceHandler<FluidResource> {
        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int slot) {
            return (fuelFluid == null || fuelMb <= 0) ? FluidResource.EMPTY : FluidResource.of(fuelFluid);
        }

        @Override public long getAmountAsLong(int slot) { return fuelMb; }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            return CAPACITY_MB;
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            if (resource.isEmpty()) return false;
            if (!ForgeFuels.isFuel(resource.getFluid())) return false;
            return fuelFluid == null || fuelFluid == resource.getFluid();
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || resource.isEmpty() || amount <= 0) return 0;
            return addFuel(resource.getFluid(), amount);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || fuelMb <= 0 || amount <= 0) return 0;
            if (!resource.isEmpty() && resource.getFluid() != fuelFluid) return 0;
            return drainFuel(amount);
        }
    }
}
