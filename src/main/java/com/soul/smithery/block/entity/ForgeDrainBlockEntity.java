package com.soul.smithery.block.entity;

import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Forge Drain port. In the new "pipes-are-just-channels" model the drain is the
 * <em>active pump</em> for its connected pipe network:
 *
 *   1. Holds a back-reference to its controller (set during multiblock validation).
 *   2. Once per server tick: walks the pipe network adjacent to it (BFS, capped),
 *      identifies all foreign fluid handlers attached to those pipes, then pulls
 *      the controller's selected output fluid and pushes it into the sinks with a
 *      gravity bias (lower-Y first).
 *   3. Also exposes a passthrough {@link ResourceHandler}&lt;{@link FluidResource}&gt;
 *      capability so buckets and external mods can extract directly. The passthrough
 *      respects the controller's selected output fluid: it only yields that fluid,
 *      never anything else stored in the forge.
 *
 * The redstone-gating, IN/OUT, and per-face configuration concerns that previously
 * lived on the pipe are gone. Sinks self-gate (casting tables refuse fluid outside
 * IMPRESSED/FILLING, etc) and the controller's GUI fluid-selection is the only knob.
 */
public class ForgeDrainBlockEntity extends BlockEntity {

    /** Total mB pushed into the network per drain per tick. Roughly one bucket every 20 ticks. */
    public static final int PUMP_RATE_MB     = 50;
    /** Safety cap on pipe-network BFS to avoid pathological worlds locking up the tick loop. */
    public static final int MAX_NETWORK_SIZE = 256;

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

    /** Resolves the currently-linked controller BE, or null if none / no longer there. */
    private @Nullable ForgeControllerBlockEntity controller() {
        if (controllerPos == null || level == null) return null;
        return level.getBlockEntity(controllerPos) instanceof ForgeControllerBlockEntity c ? c : null;
    }

    // ---- Server tick: BFS network, distribute output fluid ----

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Redstone gate: the drain only pumps while it sees a signal on any neighbour.
        // Button press (≈30 ticks of signal) is enough to fill one cast (a 72 mB guard
        // fills in 2 ticks at PUMP_RATE_MB=50) and then the downstream casting table
        // refuses further insert during its cooling window, so the rest of the button's
        // signal harmlessly idles → "1 press = 1 cast". A held lever keeps the drain
        // attempting every tick and produces continuous casts as the player retrieves
        // each finished part (table loops back to IMPRESSED → next pour).
        if (!level.hasNeighborSignal(pos)) return;

        ForgeControllerBlockEntity controller = controller();
        if (controller == null) return;
        Identifier outputFluidId = controller.outputFluidId();
        if (outputFluidId == null) return;
        if (controller.outputFluidMb() <= 0) return;

        Fluid outputFluid = BuiltInRegistries.FLUID.get(outputFluidId)
                .<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
        if (outputFluid == Fluids.EMPTY) return;

        Set<BlockPos> pipeNetwork = walkPipeNetwork(level, pos);
        if (pipeNetwork.isEmpty()) return;

        List<SinkRef> sinks = findSinks(level, pipeNetwork, pos);
        if (sinks.isEmpty()) return;

        // Gravity bias: prefer lower-Y sinks first. Stable so equal-Y sinks fill in BFS order.
        sinks.sort(Comparator.comparingInt(s -> s.pos.getY()));

        int budget = Math.min(PUMP_RATE_MB, controller.outputFluidMb());
        FluidResource resource = FluidResource.of(outputFluid);
        int totalPushed = 0;

        for (SinkRef sink : sinks) {
            if (budget <= 0) break;
            int pushed;
            try (Transaction tx = Transaction.openRoot()) {
                pushed = sink.handler.insert(resource, budget, tx);
                if (pushed > 0) tx.commit();
            }
            if (pushed > 0) {
                budget      -= pushed;
                totalPushed += pushed;
            }
        }

        if (totalPushed > 0) {
            controller.drainFluid(outputFluid, totalPushed);
        }
    }

    /** BFS the pipe network reachable from {@code drainPos}'s adjacent pipe blocks. */
    private Set<BlockPos> walkPipeNetwork(ServerLevel level, BlockPos drainPos) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos seed = drainPos.relative(d);
            if (FluidPipeBlock.isPipe(level.getBlockState(seed))) {
                queue.add(seed);
            }
        }
        while (!queue.isEmpty() && visited.size() < MAX_NETWORK_SIZE) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) continue;
            for (Direction d : Direction.values()) {
                BlockPos neighbor = current.relative(d);
                if (visited.contains(neighbor)) continue;
                if (FluidPipeBlock.isPipe(level.getBlockState(neighbor))) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    /**
     * For every pipe in the network, inspect its 6 face-neighbours and record any that
     * expose a real fluid handler (size&gt;0). Skips the drain itself and the linked
     * controller so we don't pump back into our own source.
     */
    private List<SinkRef> findSinks(ServerLevel level, Set<BlockPos> pipeNetwork, BlockPos drainPos) {
        List<SinkRef> sinks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pipePos : pipeNetwork) {
            for (Direction d : Direction.values()) {
                BlockPos neighbor = pipePos.relative(d);
                if (neighbor.equals(drainPos)) continue;
                if (Objects.equals(neighbor, controllerPos)) continue;
                if (pipeNetwork.contains(neighbor)) continue;
                if (!seen.add(neighbor)) continue;
                ResourceHandler<FluidResource> handler = level.getCapability(
                        Capabilities.Fluid.BLOCK, neighbor, d.getOpposite());
                if (handler != null && handler.size() > 0) {
                    sinks.add(new SinkRef(neighbor, handler));
                }
            }
        }
        return sinks;
    }

    private record SinkRef(BlockPos pos, ResourceHandler<FluidResource> handler) {}

    // ---- Passthrough fluid capability (for buckets / external mod queries) ----

    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new DrainHandler();
    }

    /**
     * Exposes only the controller's currently-selected output fluid — never any other
     * fluid stored in the forge. {@link #size()} is 0 when nothing is selected or the
     * selected fluid is empty, otherwise 1.
     */
    private final class DrainHandler implements ResourceHandler<FluidResource> {

        private @Nullable Identifier activeId() {
            ForgeControllerBlockEntity c = controller();
            if (c == null) return null;
            Identifier id = c.outputFluidId();
            if (id == null || c.outputFluidMb() <= 0) return null;
            return id;
        }

        private @Nullable Fluid activeFluid() {
            Identifier id = activeId();
            if (id == null) return null;
            return BuiltInRegistries.FLUID.get(id).<Fluid>map(r -> r.value()).orElse(null);
        }

        @Override public int size() {
            return activeId() == null ? 0 : 1;
        }

        @Override public FluidResource getResource(int slot) {
            if (slot != 0) return FluidResource.EMPTY;
            Fluid f = activeFluid();
            return f == null ? FluidResource.EMPTY : FluidResource.of(f);
        }

        @Override public long getAmountAsLong(int slot) {
            ForgeControllerBlockEntity c = controller();
            return (slot != 0 || c == null) ? 0L : c.outputFluidMb();
        }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            ForgeControllerBlockEntity c = controller();
            return c == null ? 0L : c.fluidCapacityMb();
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            Fluid f = activeFluid();
            return f != null && !resource.isEmpty() && resource.getFluid() == f;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            // Drain is output-only — molten fluid is generated by the forge, not pumped back in.
            return 0;
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            ForgeControllerBlockEntity c = controller();
            Fluid f = activeFluid();
            if (slot != 0 || c == null || f == null || resource.isEmpty() || amount <= 0) return 0;
            if (resource.getFluid() != f) return 0;
            return c.drainFluid(f, amount);
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
