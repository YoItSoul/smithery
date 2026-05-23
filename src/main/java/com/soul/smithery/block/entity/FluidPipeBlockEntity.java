package com.soul.smithery.block.entity;

import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.block.FluidPipeFaceMode;
import com.soul.smithery.block.FluidPipeFaceVisual;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * One-way fluid pipe BE. State held here:
 *   - Per-face FluidPipeFaceMode, packed two bits per face into a short (6 × 2 = 12 bits).
 *   - Single fluid identity (Fluid registry id) + mB amount, capped at {@link #CAPACITY_MB}.
 *
 * Per-tick behaviour:
 *   1. Push fluid out of Out-capable faces (CONNECTED-non-up or OUT mode) into neighbour
 *      pipes (their In-capable face) or fluid handlers — DOWN first, then horizontal, then UP.
 *      CONNECTED gravity rule: never push up. OUT bypasses gravity.
 *   2. Pull fluid from external fluid handlers via In-capable faces (CONNECTED or IN).
 *      Pipe-to-pipe pulls are driven by the source side's push, not by us.
 *
 * Pipe-to-container transfers require a redstone signal at this pipe, except when the
 * sink is a casting table directly below — that's a "smart sink" exempt from gating.
 */
public class FluidPipeBlockEntity extends BlockEntity {

    public static final int CAPACITY_MB         = 250;
    public static final int TRANSFER_RATE_MB    = 25;

    // ---- Per-face mode storage ----
    // Two bits per direction, packed in the order indexed by Direction.get3DDataValue()
    // (down=0, up=1, north=2, south=3, west=4, east=5). Twelve bits total; we keep the
    // field as int so ValueInput.getInt(String) can round-trip it directly (no getShort).
    // Default is CONNECTED (ordinal 0) for all six faces.
    private int faceModes = 0;

    // ---- Single-fluid storage ----
    // fluid == Fluids.EMPTY ⇔ amountMb == 0. We clear identity when emptied so the
    // next inbound fluid can be a different one.
    private Fluid storedFluid = Fluids.EMPTY;
    private int   storedAmountMb = 0;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    // ---- Face mode accessors ----

    public FluidPipeFaceMode faceMode(Direction dir) {
        int shift = dir.get3DDataValue() * 2;
        return FluidPipeFaceMode.byOrdinal((faceModes >>> shift) & 0b11);
    }

    private void setFaceMode(Direction dir, FluidPipeFaceMode mode) {
        int shift = dir.get3DDataValue() * 2;
        int mask  = 0b11 << shift;
        int bits  = (mode.ordinal() & 0b11) << shift;
        faceModes = (faceModes & ~mask) | bits;
    }

    /** Stick interaction: cycle and return the new mode. Recomputes visuals + syncs. */
    public FluidPipeFaceMode cycleFaceMode(Direction dir) {
        FluidPipeFaceMode next = faceMode(dir).next();
        setFaceMode(dir, next);
        refreshAllVisuals();
        markDirtyAndSync();
        // Defensive: a mode swap may leave OUR own face's visual unchanged while still
        // flipping a neighbour pipe's visual (e.g. with the wrong rule set, OUT↔CONNECTED
        // could keep our TOPIPE but flip neighbour TOOTHER↔TOPIPE). refreshAllVisuals' setBlock
        // is gated on newState != state, so without this nudge the neighbour can render stale.
        // updateNeighborsAt fires neighborChanged on all 6 adjacent positions unconditionally.
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        return next;
    }

    // ---- Fluid storage ----

    public Fluid storedFluid()  { return storedFluid; }
    public int   storedAmountMb() { return storedAmountMb; }
    public int   remainingCapacity() { return CAPACITY_MB - storedAmountMb; }
    public boolean isEmpty()    { return storedAmountMb <= 0; }

    public String storedFluidLabel() {
        if (isEmpty()) return "empty";
        Identifier id = BuiltInRegistries.FLUID.getKey(storedFluid);
        String name = id == null ? "unknown" : id.toString();
        return name + " " + storedAmountMb + "/" + CAPACITY_MB + " mB";
    }

    /**
     * Adds up to {@code mb} of {@code fluid} to this pipe. Mismatched fluids are rejected
     * outright (return 0); excess over capacity is clamped. Returns the actual mB added.
     */
    public int receive(Fluid fluid, int mb) {
        if (fluid == null || fluid == Fluids.EMPTY || mb <= 0) return 0;
        if (!isEmpty() && fluid != storedFluid) return 0;
        int toAdd = Math.min(mb, remainingCapacity());
        if (toAdd <= 0) return 0;
        if (isEmpty()) storedFluid = fluid;
        storedAmountMb += toAdd;
        setChanged();
        return toAdd;
    }

    /** Drains up to {@code mb}. Returns the actual mB removed. Clears identity at zero. */
    public int extract(int mb) {
        if (mb <= 0 || isEmpty()) return 0;
        int taken = Math.min(mb, storedAmountMb);
        storedAmountMb -= taken;
        if (storedAmountMb <= 0) {
            storedAmountMb = 0;
            storedFluid = Fluids.EMPTY;
        }
        setChanged();
        return taken;
    }

    // ---- Visual computation ----

    /**
     * Recomputes the per-face FluidPipeFaceVisual values from the current FaceMode + the
     * actual neighbour at each face. Only issues a setBlock if anything changed — avoids
     * spurious chunk rebuilds. Always safe to call repeatedly.
     */
    public void refreshAllVisuals() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FluidPipeBlock)) return;

        BlockState newState = state;
        for (Direction dir : Direction.values()) {
            FluidPipeFaceVisual visual = computeVisual(dir);
            newState = newState.setValue(FluidPipeBlock.propertyFor(dir), visual);
        }
        if (newState != state) {
            // Flag 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS. Neighbour-update cascade is bounded:
            // adjacent pipes' visuals only depend on the *block type* of their neighbours
            // (pipe / container / air), not on per-face properties, so a property-only state
            // change rolls through neighbours once and then settles to no-ops.
            level.setBlock(worldPosition, newState, 3);
        }
    }

    private FluidPipeFaceVisual computeVisual(Direction dir) {
        if (level == null) return FluidPipeFaceVisual.NONE;
        FluidPipeFaceMode mode = faceMode(dir);
        BlockPos neighborPos = worldPosition.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);

        // 1. Casting-table-below override (DOWN face only). The smart-sink visual overrides
        //    the normal container rules; player's explicit DISCONNECTED still wins.
        boolean castingTableBelow = (dir == Direction.DOWN
                && neighborState.is(SmitheryBlocks.CASTING_TABLE.get()));
        if (castingTableBelow) {
            return switch (mode) {
                case DISCONNECTED -> FluidPipeFaceVisual.CAP_STUB;
                case CONNECTED, IN, OUT -> FluidPipeFaceVisual.ARM_OPEN;
            };
        }

        // 2. Classify the neighbour. AIR is always "no neighbour" — skip the capability check
        //    entirely. NeoForge 26.1's BlockCapability registry returns a non-null proxy
        //    handler for many vanilla blocks (waterloggable, fluid sources, etc.), so an
        //    unguarded capability lookup ends up treating air as a foreign fluid container.
        boolean isPipe = FluidPipeBlock.isPipe(neighborState);
        boolean isContainer = !isPipe && !neighborState.isAir() && neighborHasFluidHandler(neighborPos, dir);

        // 3. Player-selected DISCONNECTED: show a stub if there's something connectable on
        //    the other side ("could connect but turned off"), otherwise render nothing.
        if (mode == FluidPipeFaceMode.DISCONNECTED) {
            return (isPipe || isContainer) ? FluidPipeFaceVisual.CAP_STUB : FluidPipeFaceVisual.NONE;
        }

        // 4. Pipe-to-pipe with directional visuals. The rule:
        //      TOPIPE  = "this face pushes (spigot)" or neutral bidirectional default
        //      TOOTHER = "this face receives (flange)"
        //    Reading both ends together always tells you the flow direction at the join
        //    (spigot → flange = fluid goes that way).
        if (isPipe) {
            FluidPipeFaceMode otherMode = FluidPipeFaceMode.CONNECTED;
            if (level.getBlockEntity(neighborPos) instanceof FluidPipeBlockEntity other) {
                otherMode = other.faceMode(dir.getOpposite());
            }
            if (otherMode == FluidPipeFaceMode.DISCONNECTED) {
                return FluidPipeFaceVisual.CAP_STUB;
            }
            return switch (mode) {
                case OUT -> FluidPipeFaceVisual.ARM_TOPIPE;   // we push out (spigot)
                case IN  -> FluidPipeFaceVisual.ARM_TOOTHER;  // we receive (flange)
                case CONNECTED -> switch (otherMode) {
                    // CONNECTED takes its visual cue from the neighbour's role: if neighbour
                    // pushes into us we're the receiver (flange); if neighbour pulls from us
                    // we're the pusher (spigot); CONNECTED↔CONNECTED renders ARM_OPEN
                    // (no cap, continuous tube) so plain bidirectional joins are visually
                    // distinct from any one-way join.
                    case OUT          -> FluidPipeFaceVisual.ARM_TOOTHER;
                    case IN           -> FluidPipeFaceVisual.ARM_TOPIPE;
                    case CONNECTED    -> FluidPipeFaceVisual.ARM_OPEN;
                    case DISCONNECTED -> FluidPipeFaceVisual.CAP_STUB; // handled above; defensive
                };
                case DISCONNECTED -> FluidPipeFaceVisual.CAP_STUB; // handled above; defensive
            };
        }

        // 5. Foreign fluid container. OUT pushes through TOPIPE (free flow into the tank);
        //    IN and CONNECTED render the TOOTHER flange (the directional foreign interface).
        if (isContainer) {
            return mode == FluidPipeFaceMode.OUT
                    ? FluidPipeFaceVisual.ARM_TOPIPE
                    : FluidPipeFaceVisual.ARM_TOOTHER;
        }

        // 6. Lone face (air, inert wall, non-fluid block): render nothing.
        return FluidPipeFaceVisual.NONE;
    }

    /**
     * Returns true iff the neighbour at {@code pos} exposes a *real* {@link ResourceHandler}
     * &lt;{@link FluidResource}&gt; from {@code dir.getOpposite()}. A handler with size 0 (i.e.
     * NeoForge's empty proxy for blocks that don't actually contain fluid) is treated as no
     * container. {@code dir} is the direction *from* this pipe *to* the neighbour.
     */
    private boolean neighborHasFluidHandler(BlockPos pos, Direction dir) {
        ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK,
                pos, dir.getOpposite());
        return h != null && h.size() > 0;
    }

    // ---- Server tick ----

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Fast path: nothing to push AND nothing to pull (no IN/CONNECTED face on a container).
        // We still check for inbound pulls because a redstone-gated tank could be feeding us
        // from rest. The IN-face check is cheap (six ordinal compares).
        boolean hasFluid = !isEmpty();
        boolean hasPullCandidate = anyPullableFace();
        if (!hasFluid && !hasPullCandidate) return;

        boolean changed = false;
        if (hasFluid) {
            changed |= pushOutbound(level, pos);
        }
        if (anyPullableFace()) {
            changed |= pullInbound(level, pos);
        }
        if (changed) {
            // Storage changed but the BlockState (visuals) didn't — just resync the BE NBT.
            markDirtyAndSync();
        }
    }

    private boolean anyPullableFace() {
        for (Direction d : Direction.values()) {
            if (faceMode(d).allowsInbound()) return true;
        }
        return false;
    }

    // ---- Outbound push ----

    /** Order: DOWN first, horizontals next, UP last (UP only via OUT override). */
    private static final Direction[] PUSH_ORDER = new Direction[] {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

    private boolean pushOutbound(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        for (Direction dir : PUSH_ORDER) {
            if (storedAmountMb <= 0) break;
            FluidPipeFaceMode mode = faceMode(dir);
            if (!mode.allowsOutbound()) continue;
            // Gravity rule: CONNECTED never pushes upward. OUT explicit overrides it.
            if (mode == FluidPipeFaceMode.CONNECTED && dir == Direction.UP) continue;

            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            // Pipe-to-pipe.
            if (FluidPipeBlock.isPipe(neighborState)
                    && level.getBlockEntity(neighborPos) instanceof FluidPipeBlockEntity other) {
                if (transferToNeighborPipe(other, dir, mode)) changed = true;
                continue;
            }

            // Pipe-to-container. Redstone gate, with casting-table-below as a smart-sink exception.
            boolean isCastingTableBelow = (dir == Direction.DOWN
                    && neighborState.is(SmitheryBlocks.CASTING_TABLE.get()));
            if (!isCastingTableBelow && !level.hasNeighborSignal(pos)) continue;

            ResourceHandler<FluidResource> sink = level.getCapability(Capabilities.Fluid.BLOCK,
                    neighborPos, dir.getOpposite());
            if (sink == null) continue;
            if (fillSink(sink, dir)) changed = true;
        }
        return changed;
    }

    private boolean transferToNeighborPipe(FluidPipeBlockEntity other, Direction dir, FluidPipeFaceMode mode) {
        FluidPipeFaceMode otherMode = other.faceMode(dir.getOpposite());
        if (!otherMode.allowsInbound()) return false;
        if (!other.isEmpty() && other.storedFluid != storedFluid) return false;

        int budget = Math.min(storedAmountMb, TRANSFER_RATE_MB);
        budget = Math.min(budget, other.remainingCapacity());
        if (budget <= 0) return false;

        // Direction-specific transfer policy.
        int toTransfer;
        if (dir == Direction.DOWN) {
            // Free fall to a lower pipe: push the full budget if neighbour has space.
            toTransfer = budget;
        } else if (dir == Direction.UP) {
            // Only reached via OUT mode (gravity-check above blocks CONNECTED-up).
            toTransfer = budget;
        } else {
            // Horizontal: equalize toward neighbour. Only transfer if neighbour has strictly less.
            int diff = storedAmountMb - other.storedAmountMb;
            if (diff <= 0) return false;
            // Half the difference per tick gives nice smooth averaging; clamp to budget.
            toTransfer = Math.min(budget, (diff + 1) / 2);
        }
        if (toTransfer <= 0) return false;

        int accepted = other.receive(storedFluid, toTransfer);
        if (accepted <= 0) return false;
        extract(accepted);
        return true;
    }

    private boolean fillSink(ResourceHandler<FluidResource> sink, Direction dir) {
        int budget = Math.min(storedAmountMb, TRANSFER_RATE_MB);
        if (budget <= 0) return false;
        FluidResource offer = FluidResource.of(storedFluid);
        int accepted;
        // Open a real transaction so handlers that journal (vanilla / other mods) stay consistent.
        try (Transaction tx = Transaction.openRoot()) {
            accepted = sink.insert(offer, budget, tx);
            if (accepted > 0) tx.commit();
        }
        if (accepted <= 0) return false;
        extract(accepted);
        return true;
    }

    // ---- Inbound pull (from external containers only — pipe-to-pipe is push-driven) ----

    private boolean pullInbound(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        for (Direction dir : Direction.values()) {
            FluidPipeFaceMode mode = faceMode(dir);
            if (!mode.allowsInbound()) continue;
            // Gravity rule: CONNECTED never pulls upward through DOWN face (fluid would have
            // to come from below, which is non-pressurised). IN explicit overrides.
            if (mode == FluidPipeFaceMode.CONNECTED && dir == Direction.DOWN) continue;
            if (remainingCapacity() <= 0) continue;

            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            // Pipe-to-pipe pulls are handled on the other side's push tick.
            if (FluidPipeBlock.isPipe(neighborState)) continue;

            // Redstone-gated: containers only feed pipes when the pipe sees a redstone signal.
            if (!level.hasNeighborSignal(pos)) continue;

            ResourceHandler<FluidResource> source = level.getCapability(Capabilities.Fluid.BLOCK,
                    neighborPos, dir.getOpposite());
            if (source == null) continue;
            if (drainSource(source)) changed = true;
        }
        return changed;
    }

    private boolean drainSource(ResourceHandler<FluidResource> source) {
        int budget = Math.min(TRANSFER_RATE_MB, remainingCapacity());
        if (budget <= 0) return false;

        // Find an extractable resource compatible with our current contents (or anything,
        // if we're empty). The predicate keeps us from triggering an extract on the wrong fluid.
        try (Transaction tx = Transaction.openRoot()) {
            FluidResource candidate = ResourceHandlerUtil.findExtractableResource(source,
                    res -> !res.isEmpty() && (isEmpty() || res.getFluid() == storedFluid),
                    tx);
            if (candidate == null || candidate.isEmpty()) return false;
            int extracted = source.extract(candidate, budget, tx);
            if (extracted <= 0) return false;
            int accepted = receive(candidate.getFluid(), extracted);
            if (accepted < extracted) {
                // Capacity mismatch — would only happen if remainingCapacity changed mid-tick
                // (shouldn't be possible in single-threaded server loop). Abort the transaction.
                return false;
            }
            tx.commit();
            return true;
        }
    }

    // ---- Capability handler ----

    /**
     * Returns the {@link ResourceHandler}&lt;{@link FluidResource}&gt; view of this pipe seen
     * from {@code side}. The handler respects the side's FaceMode: DISCONNECTED refuses
     * both insert and extract, OUT refuses insert, IN refuses extract. A null side
     * (internal access) is unrestricted.
     *
     * Note on transactions: this handler commits immediately and does not journal state for
     * the transaction context. Aborted transactions from external callers will not roll back
     * inserts/extracts on this handler. Internal pipe-to-pipe transfers don't go through
     * this handler (they call receive/extract directly on the neighbour BE), so this only
     * affects external mod interop with mods that rely on transaction rollback.
     */
    public ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        return new SidedHandler(side);
    }

    private final class SidedHandler implements ResourceHandler<FluidResource> {
        private final @Nullable Direction side;
        SidedHandler(@Nullable Direction side) { this.side = side; }

        private boolean allowsInsert()  { return side == null || faceMode(side).allowsInbound();  }
        private boolean allowsExtract() { return side == null || faceMode(side).allowsOutbound(); }

        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int slot) {
            return isEmpty() ? FluidResource.EMPTY : FluidResource.of(storedFluid);
        }

        @Override public long getAmountAsLong(int slot) { return storedAmountMb; }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            if (resource.isEmpty()) return CAPACITY_MB;
            if (isEmpty() || resource.getFluid() == storedFluid) return CAPACITY_MB;
            return 0;
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            if (resource.isEmpty()) return false;
            return isEmpty() || resource.getFluid() == storedFluid;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || !allowsInsert() || resource.isEmpty() || amount <= 0) return 0;
            return receive(resource.getFluid(), amount);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || !allowsExtract() || isEmpty() || amount <= 0) return 0;
            if (!resource.isEmpty() && resource.getFluid() != storedFluid) return 0;
            return FluidPipeBlockEntity.this.extract(amount);
        }
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("faceModes", faceModes);
        if (!isEmpty()) {
            Identifier fluidId = BuiltInRegistries.FLUID.getKey(storedFluid);
            if (fluidId != null) {
                output.putString("fluid", fluidId.toString());
                output.putInt("mb", storedAmountMb);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        faceModes = input.getInt("faceModes").orElse(0);
        Optional<String> fluidStr = input.getString("fluid");
        if (fluidStr.isPresent()) {
            Identifier fluidId = Identifier.tryParse(fluidStr.get());
            Fluid fluid = fluidId == null ? Fluids.EMPTY
                    : BuiltInRegistries.FLUID.get(fluidId).<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
            int mb = input.getInt("mb").orElse(0);
            if (fluid != Fluids.EMPTY && mb > 0) {
                storedFluid = fluid;
                storedAmountMb = Math.min(CAPACITY_MB, mb);
            } else {
                storedFluid = Fluids.EMPTY;
                storedAmountMb = 0;
            }
        } else {
            storedFluid = Fluids.EMPTY;
            storedAmountMb = 0;
        }
    }

    // ---- Sync ----

    private void markDirtyAndSync() {
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            // Initial visual sync — neighbours might have changed while the chunk was unloaded.
            refreshAllVisuals();
        }
    }
}
