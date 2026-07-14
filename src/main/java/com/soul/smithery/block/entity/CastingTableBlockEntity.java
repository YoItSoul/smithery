package com.soul.smithery.block.entity;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * State and behaviour for a sand-casting workbench. Drives the EMPTY -> SAND ->
 * IMPRESSED -> FILLING -> COOLING -> READY cycle, persisting per-state data
 * (impressed part type, required mB, poured fluid, fill amount, cooling progress)
 * and exposing both a write-only fluid sink and a READY-gated item source via
 * NeoForge capabilities.
 */
public class CastingTableBlockEntity extends BlockEntity {

    /**
     * Cast cycle states for a single table.
     */
    public enum State {
        /** Bare table — no sand, no impression. */
        EMPTY,
        /** Sand layer poured but not yet shaped. */
        SAND,
        /** Sand has a part-type impression; ready to receive fluid. */
        IMPRESSED,
        /** Mid-pour; partially filled with one fluid. */
        FILLING,
        /** Fully filled and currently cooling down. */
        COOLING,
        /** Sand re-coats the cooled part; brush to expose. */
        COVERED,
        /** Finished part ready to be picked up. */
        READY;

        static State byName(String name) {
            for (State s : values()) if (s.name().equals(name)) return s;
            return EMPTY;
        }
    }

    /** Number of brush strokes needed to sweep sand off; matches vanilla suspicious-sand pacing. */
    public static final int MAX_BRUSH = 4;
    /** Per-mB cooling time in server ticks. */
    public static final int COOLING_TICKS_PER_MB = 2;

    private State state = State.EMPTY;
    private @Nullable ResourceLocation impressedPartTypeId;
    private int requiredMb = 0;
    private int brushProgress = 0;

    private Fluid pouredFluid = Fluids.EMPTY;
    private int   filledMb = 0;
    private int   coolingTicksRemaining = 0;

    /**
     * Constructs a casting table BE bound to the given position and blockstate.
     */
    public CastingTableBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.CASTING_TABLE.get(), pos, state);
    }

    /** Returns the current cycle {@link State}. */
    public State state() { return state; }
    /** Returns the impressed PartType id, or null if no impression. */
    public @Nullable ResourceLocation impressedPartTypeId() { return impressedPartTypeId; }
    /** Returns the mB required to fully fill the current impression. */
    public int requiredMb() { return requiredMb; }
    /** Returns the current brush stroke count toward {@link #MAX_BRUSH}. */
    public int brushProgress() { return brushProgress; }
    /** Returns the fluid currently poured (or being poured); {@code Fluids.EMPTY} when none. */
    public Fluid pouredFluid() { return pouredFluid; }
    /** Returns the mB poured so far toward {@link #requiredMb()}. */
    public int filledMb() { return filledMb; }
    /** Returns the remaining cooling-state ticks before the cast becomes READY. */
    public int coolingTicksRemaining() { return coolingTicksRemaining; }

    /**
     * Cooling progress as a 0..1 fraction used by the renderer to fade between the
     * molten fluid tint and the solid part colour. Returns 1.0 during FILLING and 0.0
     * outside of FILLING / COOLING.
     */
    public float coolingFraction() {
        if (state == State.FILLING) return 1.0f;
        if (state != State.COOLING) return 0.0f;
        int total = Math.max(1, requiredMb * COOLING_TICKS_PER_MB);
        return Math.max(0.0f, Math.min(1.0f, (float) coolingTicksRemaining / (float) total));
    }

    /**
     * True iff this table can currently accept poured fluid (either IMPRESSED, or
     * partially FILLING with room remaining).
     */
    public boolean acceptsPour() {
        return state == State.IMPRESSED || (state == State.FILLING && filledMb < requiredMb);
    }

    /**
     * Places a fresh sand layer or smooths an existing impression. Advances EMPTY -> SAND
     * fresh, or IMPRESSED -> SAND while clearing the impression. Returns true when the
     * state advanced; the caller is then responsible for consuming one sand item.
     */
    public boolean tryFillSand() {
        if (state == State.EMPTY) {
            state = State.SAND;
            markDirtyAndSync();
            return true;
        }
        if (state == State.IMPRESSED) {
            state = State.SAND;
            impressedPartTypeId = null;
            requiredMb = 0;
            brushProgress = 0;
            markDirtyAndSync();
            return true;
        }
        return false;
    }

    /**
     * Advances SAND -> IMPRESSED using a {@link PartItem} as the template (not consumed).
     * Records the PartType id and the mB the resulting cast will require. Returns true
     * when the impression was actually made.
     */
    public boolean tryImpressPart(ItemStack stack) {
        if (state != State.SAND) return false;
        if (!(stack.getItem() instanceof PartItem partItem)) return false;
        PartType pt = SmitheryAPI.PART_TYPES.get(partItem.partTypeId());
        if (pt == null) return false;
        impressedPartTypeId = partItem.partTypeId();
        requiredMb = pt.castMb();
        state = State.IMPRESSED;
        markDirtyAndSync();
        return true;
    }

    /**
     * Advances SAND -> IMPRESSED using a non-PartItem template resolved through the
     * {@link com.soul.smithery.api.cast.CastTemplates} registry. The template item itself
     * is not consumed. Returns true when the impression was actually made.
     */
    public boolean tryImpressTemplateItem(ResourceLocation castTypeId) {
        if (state != State.SAND || castTypeId == null) return false;
        PartType pt = SmitheryAPI.PART_TYPES.get(castTypeId);
        if (pt == null) return false;
        impressedPartTypeId = pt.id();
        requiredMb = pt.castMb();
        state = State.IMPRESSED;
        markDirtyAndSync();
        return true;
    }

    /**
     * Pours fluid into an IMPRESSED or partially-FILLING table, locking the fluid identity
     * on the first pour and rejecting mismatched fluids thereafter. Returns the mB actually
     * accepted; transitions to COOLING when the impression is fully filled.
     */
    public int tryPourFluid(Fluid fluid, int mb) {
        if (fluid == null || fluid == Fluids.EMPTY || mb <= 0) return 0;
        if (!acceptsPour()) return 0;
        if (state == State.IMPRESSED) {
            pouredFluid = fluid;
            filledMb = 0;
        } else if (pouredFluid != fluid) {
            return 0;
        }

        int accepted = Math.min(mb, requiredMb - filledMb);
        if (accepted <= 0) return 0;
        filledMb += accepted;

        if (state == State.IMPRESSED) {
            state = State.FILLING;
            brushProgress = 0;
        }
        if (filledMb >= requiredMb) {
            state = State.COOLING;
            coolingTicksRemaining = Math.max(1, requiredMb * COOLING_TICKS_PER_MB);
        }
        markDirtyAndSync();
        return accepted;
    }

    /**
     * Server-side cooling countdown. Other state transitions are interaction-driven.
     */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState blockState) {
        if (state != State.COOLING) return;
        if (coolingTicksRemaining > 0) {
            coolingTicksRemaining--;
            if (coolingTicksRemaining == 0) {
                state = State.READY;
                markDirtyAndSync();
            }
        }
    }

    /**
     * Client-side cooling prediction so the renderer's molten-to-solid lerp transitions
     * smoothly between the sparse server syncs. Re-converges naturally on the READY snap.
     */
    public void clientTick() {
        if (state == State.COOLING && coolingTicksRemaining > 0) {
            coolingTicksRemaining--;
        }
    }

    /**
     * READY -> IMPRESSED: resolves the cooled cast into the matching (Material x PartType)
     * stack, returns it for the caller to deliver, and preserves the sand impression for
     * subsequent pours. Falls all the way back to EMPTY if resolution fails (corrupt save).
     * Returns {@link ItemStack#EMPTY} when not READY.
     */
    public ItemStack tryRetrievePart() {
        if (state != State.READY) return ItemStack.EMPTY;
        ItemStack result = resolvePartItem();
        state = State.IMPRESSED;
        pouredFluid = Fluids.EMPTY;
        filledMb = 0;
        coolingTicksRemaining = 0;
        brushProgress = 0;
        if (result.isEmpty()) {
            state = State.EMPTY;
            impressedPartTypeId = null;
            requiredMb = 0;
        }
        markDirtyAndSync();
        return result;
    }

    private ItemStack resolvePartItem() {
        if (impressedPartTypeId == null || pouredFluid == Fluids.EMPTY) return ItemStack.EMPTY;
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forFluid(pouredFluid);
        if (entry == null) return ItemStack.EMPTY;

        net.minecraft.world.item.Item explicit =
                com.soul.smithery.api.cast.CastResults.resolve(entry.materialId, impressedPartTypeId);
        if (explicit != null) return new ItemStack(explicit);

        var partItem = com.soul.smithery.registry.SmitheryItems.getBuiltInPart(
                entry.materialId, impressedPartTypeId);
        if (partItem == null) return ItemStack.EMPTY;
        return new ItemStack(partItem.get());
    }

    /**
     * Returns a copy of the part item that would be produced now, without consuming the
     * cast. Used by the renderer to preview the READY part.
     */
    public ItemStack peekPartItem() {
        return resolvePartItem();
    }

    /**
     * Applies one brush stroke; only effective in SAND, IMPRESSED, or COVERED states.
     * Reaching {@link #MAX_BRUSH} clears the sand layer: SAND/IMPRESSED -> EMPTY (impression
     * lost), COVERED -> READY (cooled part exposed). Returns true when the brush actually
     * advanced something; false for inert states so the caller can pass through to vanilla.
     */
    public boolean tryBrush() {
        boolean accepts = switch (state) {
            case SAND, IMPRESSED, COVERED -> true;
            default -> false;
        };
        if (!accepts) return false;

        brushProgress++;
        if (brushProgress >= MAX_BRUSH) {
            brushProgress = 0;
            switch (state) {
                case SAND, IMPRESSED -> {
                    state = State.EMPTY;
                    impressedPartTypeId = null;
                    requiredMb = 0;
                    pouredFluid = Fluids.EMPTY;
                    filledMb = 0;
                    coolingTicksRemaining = 0;
                }
                case COVERED -> state = State.READY;
                default -> {}
            }
        }
        markDirtyAndSync();
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("state", state.name());
        if (impressedPartTypeId != null) {
            output.putString("impressedPartType", impressedPartTypeId.toString());
        }
        if (requiredMb > 0) {
            output.putInt("requiredMb", requiredMb);
        }
        if (brushProgress > 0) {
            output.putInt("brushProgress", brushProgress);
        }
        if (pouredFluid != Fluids.EMPTY) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(pouredFluid);
            if (fluidId != null) output.putString("pouredFluid", fluidId.toString());
        }
        if (filledMb > 0) {
            output.putInt("filledMb", filledMb);
        }
        if (coolingTicksRemaining > 0) {
            output.putInt("coolingTicks", coolingTicksRemaining);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        state = input.getString("state").map(State::byName).orElse(State.EMPTY);
        Optional<String> ptStr = input.getString("impressedPartType");
        impressedPartTypeId = ptStr.map(ResourceLocation::tryParse).orElse(null);
        requiredMb = input.getInt("requiredMb").orElse(0);
        brushProgress = input.getInt("brushProgress").orElse(0);

        Optional<String> fluidStr = input.getString("pouredFluid");
        if (fluidStr.isPresent()) {
            ResourceLocation fluidId = ResourceLocation.tryParse(fluidStr.get());
            pouredFluid = fluidId == null
                    ? Fluids.EMPTY
                    : BuiltInRegistries.FLUID.get(fluidId).<Fluid>map(r -> r.value()).orElse(Fluids.EMPTY);
        } else {
            pouredFluid = Fluids.EMPTY;
        }
        filledMb = input.getInt("filledMb").orElse(0);
        coolingTicksRemaining = input.getInt("coolingTicks").orElse(0);
    }

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

    /**
     * Returns a write-only fluid capability accessible only from the table's UP face.
     * Non-top sides see no handler, matching the visual "top-pour only" model.
     */
    public @Nullable ResourceHandler<FluidResource> fluidHandlerFor(@Nullable Direction side) {
        if (side != null && side != Direction.UP) return null;
        return new TableFluidHandler();
    }

    private final class TableFluidHandler implements ResourceHandler<FluidResource> {
        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int slot) {
            return (pouredFluid == Fluids.EMPTY || filledMb <= 0)
                    ? FluidResource.EMPTY : FluidResource.of(pouredFluid);
        }

        @Override public long getAmountAsLong(int slot) { return filledMb; }

        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            return Math.max(0, requiredMb);
        }

        @Override public boolean isValid(int slot, FluidResource resource) {
            if (!acceptsPour() || resource.isEmpty()) return false;
            return pouredFluid == Fluids.EMPTY || resource.getFluid() == pouredFluid;
        }

        @Override
        public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || !acceptsPour() || resource.isEmpty() || amount <= 0) return 0;
            if (pouredFluid != Fluids.EMPTY && resource.getFluid() != pouredFluid) return 0;
            return tryPourFluid(resource.getFluid(), amount);
        }

        @Override
        public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            return 0;
        }
    }

    /**
     * Returns an item capability that surfaces the cooled part at READY for hopper-style
     * extraction. Inserts are always rejected to preserve the sand/impression workflow.
     */
    public ResourceHandler<ItemResource> itemHandlerFor(@Nullable Direction side) {
        return new TableItemHandler();
    }

    private final class TableItemHandler implements ResourceHandler<ItemResource> {
        @Override public int size() { return 1; }

        @Override public ItemResource getResource(int slot) {
            if (slot != 0 || state != State.READY) return ItemResource.EMPTY;
            ItemStack stack = peekPartItem();
            return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
        }

        @Override public long getAmountAsLong(int slot) {
            if (slot != 0 || state != State.READY) return 0L;
            return peekPartItem().isEmpty() ? 0L : 1L;
        }

        @Override public long getCapacityAsLong(int slot, ItemResource resource) {
            return 1L;
        }

        @Override public boolean isValid(int slot, ItemResource resource) {
            return false;
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            return 0;
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || state != State.READY || amount <= 0) return 0;
            if (resource.isEmpty()) return 0;
            ItemStack peek = peekPartItem();
            if (peek.isEmpty() || resource.getItem() != peek.getItem()) return 0;
            ItemStack retrieved = tryRetrievePart();
            return retrieved.isEmpty() ? 0 : 1;
        }
    }
}
