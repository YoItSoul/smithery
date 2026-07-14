package com.soul.smithery.block.entity;

import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import org.jetbrains.annotations.Nullable;

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
 * Network-aware pump for the forge drain port. On the rising redstone edge it BFSes its
 * connected pipe network, then advances a wavefront each tick that refreshes pipe flow
 * markers and pushes fluid into reached sinks. Also exposes a passthrough fluid handler
 * for buckets and external mods.
 */
public class ForgeDrainBlockEntity extends BlockEntity {

    /** mB pushed per drain tick to each active sink; total throughput scales linearly with sink count. */
    public static final int PUMP_RATE_MB     = 5;
    /** Safety cap on BFS to avoid runaway scans in pathological setups. */
    public static final int MAX_NETWORK_SIZE = 256;

    private @Nullable BlockPos controllerPos;

    private long pumpStartTick = -1L;
    private @Nullable Map<BlockPos, Integer> pipeDistances;
    private @Nullable Map<BlockPos, BlockPos> pipeParents;
    private @Nullable List<SinkRef> sinks;

    /**
     * Constructs a forge drain BE bound to the given position and blockstate.
     */
    public ForgeDrainBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_DRAIN.get(), pos, state);
    }

    /**
     * Updates the controller back-reference; idempotent, only marks dirty on actual change.
     */
    public void setControllerPos(@Nullable BlockPos pos) {
        if (!Objects.equals(controllerPos, pos)) {
            controllerPos = pos;
            setChanged();
        }
    }

    /** Returns the linked controller position, or null if unlinked. */
    public @Nullable BlockPos controllerPos() { return controllerPos; }

    private @Nullable ForgeControllerBlockEntity controller() {
        if (controllerPos == null || level == null) return null;
        return level.getBlockEntity(controllerPos) instanceof ForgeControllerBlockEntity c ? c : null;
    }

    /**
     * Per-tick pump logic. While powered, builds (lazily) and advances the BFS wavefront
     * one hop per tick, refreshing pipe flow markers and inserting {@link #PUMP_RATE_MB}
     * into each accepting sink. Drops the cached network on the falling edge.
     */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean signal = level.hasNeighborSignal(pos);

        if (!signal) {
            if (pumpStartTick >= 0) {
                pumpStartTick = -1L;
                pipeDistances = null;
                pipeParents = null;
                sinks = null;
            }
            return;
        }

        ForgeControllerBlockEntity controller = controller();
        if (controller == null) return;
        ResourceLocation outputFluidId = controller.outputFluidId();
        if (outputFluidId == null) return;
        if (controller.outputFluidMb() <= 0) return;

        Fluid outputFluid = BuiltInRegistries.FLUID.get(outputFluidId)
                .<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
        if (outputFluid == Fluids.EMPTY) return;

        long currentTick = level.getGameTime();

        if (pumpStartTick < 0L) {
            pumpStartTick = currentTick;
            bfsNetwork(level, pos);
        }
        if (pipeDistances == null || pipeParents == null || sinks == null) return;

        int wavefront = (int) (currentTick - pumpStartTick) + 1;
        FluidResource resource = FluidResource.of(outputFluid);

        List<SinkRef> activeSinks = new ArrayList<>();
        for (SinkRef sink : sinks) {
            if (sink.distance > wavefront) continue;
            if (sink.handler.isValid(0, resource)) activeSinks.add(sink);
        }

        Set<BlockPos> activePipes = new HashSet<>();
        for (SinkRef sink : activeSinks) {
            if (sink.entryPipe == null) continue;
            BlockPos node = sink.entryPipe;
            while (node != null) {
                if (!activePipes.add(node)) break;
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

    private void bfsNetwork(Level level, BlockPos drainPos) {
        Map<BlockPos, Integer> dists = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        List<SinkRef> sinkList = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        for (Direction d : Direction.values()) {
            BlockPos seed = drainPos.relative(d);
            if (Objects.equals(seed, controllerPos)) continue;
            if (FluidPipeBlock.isPipe(level.getBlockState(seed))) {
                if (dists.put(seed, 1) == null) {
                    parents.put(seed, null);
                    queue.add(seed);
                }
            } else {
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

    private record SinkRef(BlockPos pos, ResourceHandler<FluidResource> handler,
                           int distance, @Nullable BlockPos entryPipe) {}

    /**
     * Returns a passthrough fluid capability that surfaces only the controller's
     * currently-selected output fluid.
     */
    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new DrainHandler();
    }

    private final class DrainHandler implements ResourceHandler<FluidResource> {

        private @Nullable ResourceLocation activeId() {
            ForgeControllerBlockEntity c = controller();
            if (c == null) return null;
            ResourceLocation id = c.outputFluidId();
            if (id == null || c.outputFluidMb() <= 0) return null;
            return id;
        }

        private @Nullable Fluid activeFluid() {
            ResourceLocation id = activeId();
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
