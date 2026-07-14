package com.soul.smithery.block.entity;

import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(DrainHandler::new);

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

        Fluid outputFluid = ForgeRegistries.FLUIDS.getValue(outputFluidId);
        if (outputFluid == null || outputFluid == Fluids.EMPTY) return;

        long currentTick = level.getGameTime();

        if (pumpStartTick < 0L) {
            pumpStartTick = currentTick;
            bfsNetwork(level, pos);
        }
        if (pipeDistances == null || pipeParents == null || sinks == null) return;

        int wavefront = (int) (currentTick - pumpStartTick) + 1;

        List<SinkRef> activeSinks = new ArrayList<>();
        for (SinkRef sink : sinks) {
            if (sink.distance > wavefront) continue;
            FluidStack probe = new FluidStack(outputFluid, PUMP_RATE_MB);
            if (sink.handler.fill(probe, IFluidHandler.FluidAction.SIMULATE) > 0) {
                activeSinks.add(sink);
            }
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
            int pushed = sink.handler.fill(new FluidStack(outputFluid, budget),
                    IFluidHandler.FluidAction.EXECUTE);
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
                IFluidHandler h = fluidSinkAt(level, seed, d.getOpposite());
                if (h != null) sinkList.add(new SinkRef(seed, h, 1, null));
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
                    IFluidHandler h = fluidSinkAt(level, neighbor, d.getOpposite());
                    if (h != null) sinkList.add(new SinkRef(neighbor, h, dist + 1, current));
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

    /** Resolves the fluid capability of the block entity at {@code pos}, or null when absent. */
    private static @Nullable IFluidHandler fluidSinkAt(Level level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        IFluidHandler h = be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        return (h != null && h.getTanks() > 0) ? h : null;
    }

    private record SinkRef(BlockPos pos, IFluidHandler handler,
                           int distance, @Nullable BlockPos entryPipe) {}

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }

    /**
     * Returns a passthrough fluid handler that surfaces only the controller's
     * currently-selected output fluid.
     */
    public IFluidHandler fluidHandlerFor(@Nullable Direction side) {
        return new DrainHandler();
    }

    /** Extract-only view of the controller's selected output fluid. */
    private final class DrainHandler implements IFluidHandler {

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
            Fluid f = ForgeRegistries.FLUIDS.getValue(id);
            return f == Fluids.EMPTY ? null : f;
        }

        @Override public int getTanks() { return 1; }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            ForgeControllerBlockEntity c = controller();
            Fluid f = activeFluid();
            if (tank != 0 || c == null || f == null) return FluidStack.EMPTY;
            return new FluidStack(f, c.outputFluidMb());
        }

        @Override
        public int getTankCapacity(int tank) {
            ForgeControllerBlockEntity c = controller();
            return c == null ? 0 : c.fluidCapacityMb();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            Fluid f = activeFluid();
            return f != null && !stack.isEmpty() && stack.getFluid() == f;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            Fluid f = activeFluid();
            if (f == null || resource.isEmpty() || resource.getFluid() != f) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            ForgeControllerBlockEntity c = controller();
            Fluid f = activeFluid();
            if (c == null || f == null || maxDrain <= 0) return FluidStack.EMPTY;
            if (action.simulate()) {
                int available = Math.min(maxDrain, c.outputFluidMb());
                return available <= 0 ? FluidStack.EMPTY : new FluidStack(f, available);
            }
            int drained = c.drainFluid(f, maxDrain);
            return drained <= 0 ? FluidStack.EMPTY : new FluidStack(f, drained);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controllerPos != null) {
            tag.putInt("ctrlX", controllerPos.getX());
            tag.putInt("ctrlY", controllerPos.getY());
            tag.putInt("ctrlZ", controllerPos.getZ());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controllerPos = (tag.contains("ctrlX") && tag.contains("ctrlY") && tag.contains("ctrlZ"))
                ? new BlockPos(tag.getInt("ctrlX"), tag.getInt("ctrlY"), tag.getInt("ctrlZ"))
                : null;
    }
}
