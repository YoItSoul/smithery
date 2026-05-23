package com.soul.smithery.block.entity;

import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Forge Drain port. The drain is the network-aware pump for its connected pipe chain.
 *
 * Per redstone-signaled tick:
 *   1. On rising edge: BFS the network from each adjacent pipe, caching every pipe's
 *      hop-distance from us and every non-pipe fluid sink with its hop-distance too.
 *   2. Advance the wavefront by 1 hop per tick of signal — pipes inside the wavefront get
 *      their flow marker refreshed (renderer animates the molten cube travelling); sinks
 *      inside the wavefront are eligible for direct fluid push.
 *   3. Push up to {@link #PUMP_RATE_MB} into EACH eligible sink in sorted order, stopping
 *      when the controller's fluid runs out. Multiple casting tables on the same network
 *      fill in parallel. Each cast's acceptance is gated by its own state (COOLING/READY
 *      refuses inserts), so closed casts are skipped naturally and don't waste budget.
 *
 * On falling edge: cached BFS state is dropped. Pipe markers decay over
 * {@link FluidPipeBlockEntity#FLOW_PERSIST_TICKS}, fading the wave back to inert.
 *
 * Also exposes a passthrough fluid handler so buckets / external mods can extract directly.
 */
public class ForgeDrainBlockEntity extends BlockEntity {

    /**
     * mB pushed per drain tick PER ACTIVE SINK. With N active sinks, total drain throughput
     * scales as N × PUMP_RATE_MB. The forge controller's molten supply is consumed at the
     * same rate, so a wide pipe network casts faster but also drains the forge faster.
     */
    public static final int PUMP_RATE_MB     = 5;
    /** Safety cap on BFS to avoid runaway scans in pathological setups. */
    public static final int MAX_NETWORK_SIZE = 256;

    private @Nullable BlockPos controllerPos;

    /** Pumping bookkeeping — populated on rising edge, cleared on falling. */
    private long pumpStartTick = -1L;
    private @Nullable Map<BlockPos, Integer> pipeDistances;
    /** Pipe → parent pipe on its shortest path back toward the drain. Null parent = drain-adjacent. */
    private @Nullable Map<BlockPos, BlockPos> pipeParents;
    private @Nullable List<SinkRef> sinks;

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

    // ---- Server tick ----

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean signal = level.hasNeighborSignal(pos);

        if (!signal) {
            if (pumpStartTick >= 0) {
                // Falling edge: drop the cached network. Pipes' markers will decay naturally.
                pumpStartTick = -1L;
                pipeDistances = null;
                pipeParents = null;
                sinks = null;
            }
            return;
        }

        ForgeControllerBlockEntity controller = controller();
        if (controller == null) return;
        Identifier outputFluidId = controller.outputFluidId();
        if (outputFluidId == null) return;
        if (controller.outputFluidMb() <= 0) return;

        Fluid outputFluid = BuiltInRegistries.FLUID.get(outputFluidId)
                .<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
        if (outputFluid == Fluids.EMPTY) return;

        long currentTick = level.getGameTime();

        // Rising edge: build the BFS map.
        if (pumpStartTick < 0L) {
            pumpStartTick = currentTick;
            bfsNetwork(level, pos);
        }
        if (pipeDistances == null || pipeParents == null || sinks == null) return;

        // Wavefront radius: hops the wave has had time to advance since the signal started.
        // Pipes / sinks with distance <= wavefront are "reached" this tick.
        int wavefront = (int) (currentTick - pumpStartTick) + 1;
        FluidResource resource = FluidResource.of(outputFluid);

        // Find every sink the wavefront has reached AND that's currently willing to accept
        // this fluid. We push to ALL of them in parallel — multiple casting tables connected
        // to the same network fill simultaneously rather than the drain favouring one. The
        // sinks list is pre-sorted by (distance ASC, Y/X/Z tiebreaker), so when the controller's
        // fluid runs out mid-distribution the closer/lower-coord sinks win the leftover — a
        // deterministic order rather than a random "whoever the iterator hit first".
        List<SinkRef> activeSinks = new ArrayList<>();
        for (SinkRef sink : sinks) {
            if (sink.distance > wavefront) continue;
            if (sink.handler.isValid(0, resource)) activeSinks.add(sink);
        }

        // Collect the union of pipe paths back from every active sink. Pipes on at least one
        // active path get their flow marker refreshed; every other pipe in the cached network
        // that's still glowing gets cleared immediately, so the player sees molten fluid
        // travelling toward exactly the casts currently receiving it.
        Set<BlockPos> activePipes = new HashSet<>();
        for (SinkRef sink : activeSinks) {
            if (sink.entryPipe == null) continue;
            BlockPos node = sink.entryPipe;
            while (node != null) {
                if (!activePipes.add(node)) break;  // already walked from another sink — skip the shared tail
                node = pipeParents.get(node);
            }
        }
        for (BlockPos node : activePipes) {
            Integer dist = pipeDistances.get(node);
            if (dist != null && dist <= wavefront
                    && level.getBlockEntity(node) instanceof FluidPipeBlockEntity pipe) {
                pipe.markFlow(outputFluidId);
            }
        }
        for (BlockPos pipePos : pipeDistances.keySet()) {
            if (activePipes.contains(pipePos)) continue;
            if (level.getBlockEntity(pipePos) instanceof FluidPipeBlockEntity pipe
                    && pipe.intensityTicks() > 0) {
                pipe.clearFlow();
            }
        }

        if (activeSinks.isEmpty()) return;

        // Push up to PUMP_RATE_MB into each active sink this tick, in sorted order, stopping
        // when the controller runs out of fluid. PUMP_RATE_MB is per-sink (not a shared budget):
        // adding more casts to the network speeds up overall casting throughput. The forge's
        // fluid drains correspondingly faster — so the limiter becomes melting speed, not
        // delivery speed.
        for (SinkRef sink : activeSinks) {
            int available = controller.outputFluidMb();
            if (available <= 0) break;
            int budget = Math.min(PUMP_RATE_MB, available);
            int pushed;
            try (Transaction tx = Transaction.openRoot()) {
                pushed = sink.handler.insert(resource, budget, tx);
                if (pushed > 0) tx.commit();
            }
            if (pushed > 0) {
                controller.drainFluid(outputFluid, pushed);
            }
        }
    }

    /**
     * BFS the pipe network reachable from the drain's 6 adjacent positions. Records:
     *   - hop distance for every pipe
     *   - parent pipe (the one we discovered it from — null for drain-adjacent seeds)
     *   - every non-pipe fluid sink touching the network, with its entry pipe (the pipe
     *     that connects it back to the drain)
     * Skips the linked controller block so we never try to pump back into our own source.
     */
    private void bfsNetwork(Level level, BlockPos drainPos) {
        Map<BlockPos, Integer> dists = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        List<SinkRef> sinkList = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        // Seed with adjacent pipes at distance 1. Parent = null (they're at the drain).
        for (Direction d : Direction.values()) {
            BlockPos seed = drainPos.relative(d);
            if (Objects.equals(seed, controllerPos)) continue;
            if (FluidPipeBlock.isPipe(level.getBlockState(seed))) {
                if (dists.put(seed, 1) == null) {
                    parents.put(seed, null);
                    queue.add(seed);
                }
            } else {
                // Sinks directly adjacent to the drain (no pipes needed). entryPipe = null.
                ResourceHandler<FluidResource> h = level.getCapability(
                        Capabilities.Fluid.BLOCK, seed, d.getOpposite());
                if (h != null && h.size() > 0) sinkList.add(new SinkRef(seed, h, 1, null));
            }
        }

        while (!queue.isEmpty() && dists.size() < MAX_NETWORK_SIZE) {
            BlockPos current = queue.poll();
            int dist = dists.get(current);
            for (Direction d : Direction.values()) {
                BlockPos neighbor = current.relative(d);
                if (Objects.equals(neighbor, drainPos)) continue;
                if (Objects.equals(neighbor, controllerPos)) continue;
                if (FluidPipeBlock.isPipe(level.getBlockState(neighbor))) {
                    if (dists.containsKey(neighbor)) continue;
                    dists.put(neighbor, dist + 1);
                    parents.put(neighbor, current);
                    queue.add(neighbor);
                } else {
                    ResourceHandler<FluidResource> h = level.getCapability(
                            Capabilities.Fluid.BLOCK, neighbor, d.getOpposite());
                    if (h != null && h.size() > 0) sinkList.add(new SinkRef(neighbor, h, dist + 1, current));
                }
            }
        }

        // Sort sinks by (distance ASC, Y ASC, X ASC, Z ASC). The Y/X/Z tiebreakers make the
        // fill order deterministic across presses when multiple sinks sit at the same hop
        // distance — without them, BFS direction iteration could shuffle the "first" cast
        // between presses, making it look like fluid was randomly distributed.
        sinkList.sort((a, b) -> {
            int c = Integer.compare(a.distance, b.distance);
            if (c != 0) return c;
            c = Integer.compare(a.pos.getY(), b.pos.getY());
            if (c != 0) return c;
            c = Integer.compare(a.pos.getX(), b.pos.getX());
            return c != 0 ? c : Integer.compare(a.pos.getZ(), b.pos.getZ());
        });

        this.pipeDistances = dists;
        this.pipeParents   = parents;
        this.sinks         = sinkList;
    }

    /**
     * @param entryPipe pipe directly adjacent to the sink (touches the sink face used for
     *                  the capability query). Null for sinks adjacent to the drain itself,
     *                  in which case there's no pipe path to mark.
     */
    private record SinkRef(BlockPos pos, ResourceHandler<FluidResource> handler,
                           int distance, @Nullable BlockPos entryPipe) {}

    // ---- Passthrough fluid capability (for buckets / external mod queries) ----

    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new DrainHandler();
    }

    /**
     * Exposes only the controller's currently-selected output fluid — never any other
     * fluid stored in the forge. {@code size()} is 0 when nothing is selected, otherwise 1.
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

        @Override public int size() { return activeId() == null ? 0 : 1; }

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
