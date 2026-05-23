package com.soul.smithery.block.entity;

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

import java.util.Objects;
import java.util.Optional;

/**
 * Block entity for the Forge Drain port. Holds a back-reference (as a BlockPos) to its
 * Forge Controller — populated when {@link ForgeControllerBlockEntity#validateStructure}
 * runs and finds this drain in the multiblock shell. The fluid capability exposed here
 * is purely a passthrough into the controller's fluid storage.
 *
 * The drain is *extract-only* from external callers (pipes, buckets, other mods). Forge
 * fluid is generated inside the controller via melting, not inserted from outside, so
 * we hard-reject inserts here. A future bucket-from-drain interaction (right-click with
 * empty bucket) would call drain() directly.
 */
public class ForgeDrainBlockEntity extends BlockEntity {

    private @Nullable BlockPos controllerPos;

    public ForgeDrainBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_DRAIN.get(), pos, state);
    }

    /** Updates the controller back-reference. Idempotent; only marks dirty on actual change. */
    public void setControllerPos(@Nullable BlockPos pos) {
        if (!Objects.equals(controllerPos, pos)) {
            controllerPos = pos;
            setChanged();
        }
    }

    public @Nullable BlockPos controllerPos() { return controllerPos; }

    /** Resolves the currently-linked controller BE, or null if there's none / it's no longer there. */
    private @Nullable ForgeControllerBlockEntity controller() {
        if (controllerPos == null || level == null) return null;
        return level.getBlockEntity(controllerPos) instanceof ForgeControllerBlockEntity c ? c : null;
    }

    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new DrainHandler();
    }

    /**
     * Read-through view of the controller's fluid storage. {@link #size()} matches the
     * controller's current fluid map cardinality (often 1), and slot iteration reads
     * straight off the map's insertion order.
     */
    private final class DrainHandler implements ResourceHandler<FluidResource> {

        @Override public int size() {
            ForgeControllerBlockEntity c = controller();
            return (c == null) ? 0 : c.fluidStorageView().size();
        }

        @Override public FluidResource getResource(int slot) {
            ForgeControllerBlockEntity c = controller();
            if (c == null || slot < 0) return FluidResource.EMPTY;
            int i = 0;
            for (Identifier id : c.fluidStorageView().keySet()) {
                if (i++ == slot) {
                    Fluid fluid = BuiltInRegistries.FLUID.get(id)
                            .<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
                    return fluid == Fluids.EMPTY ? FluidResource.EMPTY : FluidResource.of(fluid);
                }
            }
            return FluidResource.EMPTY;
        }

        @Override public long getAmountAsLong(int slot) {
            ForgeControllerBlockEntity c = controller();
            if (c == null || slot < 0) return 0L;
            int i = 0;
            for (Integer mb : c.fluidStorageView().values()) {
                if (i++ == slot) return mb.longValue();
            }
            return 0L;
        }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            ForgeControllerBlockEntity c = controller();
            return c == null ? 0L : c.fluidCapacityMb();
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            return !resource.isEmpty() && controller() != null;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            // Drain is output-only — molten fluid lives in the forge; pipes can't push fluid
            // back into it (no recipe for what to do with re-inserted fluid).
            return 0;
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            ForgeControllerBlockEntity c = controller();
            if (c == null || resource.isEmpty() || amount <= 0) return 0;
            // drainFluid handles canAccessFluids() (multiblock validity) gating internally.
            return c.drainFluid(resource.getFluid(), amount);
        }
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (controllerPos != null) {
            output.putInt("ctrlX", controllerPos.getX());
            output.putInt("ctrlY", controllerPos.getY());
            output.putInt("ctrlZ", controllerPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<Integer> x = input.getInt("ctrlX");
        Optional<Integer> y = input.getInt("ctrlY");
        Optional<Integer> z = input.getInt("ctrlZ");
        controllerPos = (x.isPresent() && y.isPresent() && z.isPresent())
                ? new BlockPos(x.get(), y.get(), z.get())
                : null;
    }
}
