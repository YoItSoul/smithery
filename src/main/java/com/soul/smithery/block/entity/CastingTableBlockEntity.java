package com.soul.smithery.block.entity;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryFluids;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * State and behaviour for a sand-casting workbench. Drives the EMPTY -> SAND ->
 * IMPRESSED -> FILLING -> COOLING -> READY cycle, persisting per-state data
 * (impressed part type, required mB, poured fluid, fill amount, cooling progress)
 * and exposing both a write-only fluid sink and a READY-gated item source via
 * Forge capabilities.
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
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(pouredFluid);
        if (entry == null) return ItemStack.EMPTY;

        Item explicit = CastResults.resolve(entry.materialId, impressedPartTypeId);
        if (explicit != null) return new ItemStack(explicit);

        var partItem = SmitheryItems.getBuiltInPart(entry.materialId, impressedPartTypeId);
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("state", state.name());
        if (impressedPartTypeId != null) {
            tag.putString("impressedPartType", impressedPartTypeId.toString());
        }
        if (requiredMb > 0) {
            tag.putInt("requiredMb", requiredMb);
        }
        if (brushProgress > 0) {
            tag.putInt("brushProgress", brushProgress);
        }
        if (pouredFluid != Fluids.EMPTY) {
            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(pouredFluid);
            if (fluidId != null) tag.putString("pouredFluid", fluidId.toString());
        }
        if (filledMb > 0) {
            tag.putInt("filledMb", filledMb);
        }
        if (coolingTicksRemaining > 0) {
            tag.putInt("coolingTicks", coolingTicksRemaining);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        state = State.byName(tag.getString("state"));
        impressedPartTypeId = tag.contains("impressedPartType")
                ? ResourceLocation.tryParse(tag.getString("impressedPartType"))
                : null;
        requiredMb = tag.getInt("requiredMb");
        brushProgress = tag.getInt("brushProgress");

        if (tag.contains("pouredFluid")) {
            ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString("pouredFluid"));
            Fluid loaded = fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
            pouredFluid = loaded == null ? Fluids.EMPTY : loaded;
        } else {
            pouredFluid = Fluids.EMPTY;
        }
        filledMb = tag.getInt("filledMb");
        coolingTicksRemaining = tag.getInt("coolingTicks");
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
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    private final LazyOptional<IFluidHandler> fluidCapUp = LazyOptional.of(TableFluidHandler::new);
    private final LazyOptional<IItemHandler>  itemCap    = LazyOptional.of(TableItemHandler::new);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // Top-pour only: non-top sides see no fluid handler, matching the visual model.
            return (side == null || side == Direction.UP) ? fluidCapUp.cast() : LazyOptional.empty();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapUp.invalidate();
        itemCap.invalidate();
    }

    /**
     * Returns a write-only fluid handler accessible only from the table's UP face.
     * Non-top sides see no handler, matching the visual "top-pour only" model.
     */
    public @Nullable IFluidHandler fluidHandlerFor(@Nullable Direction side) {
        if (side != null && side != Direction.UP) return null;
        return new TableFluidHandler();
    }

    /** Write-only pour target; the poured fluid is never extractable. */
    private final class TableFluidHandler implements IFluidHandler {
        @Override public int getTanks() { return 1; }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return (tank != 0 || pouredFluid == Fluids.EMPTY || filledMb <= 0)
                    ? FluidStack.EMPTY : new FluidStack(pouredFluid, filledMb);
        }

        @Override public int getTankCapacity(int tank) { return Math.max(0, requiredMb); }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (!acceptsPour() || stack.isEmpty()) return false;
            return pouredFluid == Fluids.EMPTY || stack.getFluid() == pouredFluid;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!acceptsPour() || resource.isEmpty()) return 0;
            if (pouredFluid != Fluids.EMPTY && resource.getFluid() != pouredFluid) return 0;
            if (action.simulate()) {
                return Math.min(resource.getAmount(), Math.max(0, requiredMb - filledMb));
            }
            return tryPourFluid(resource.getFluid(), resource.getAmount());
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    /**
     * Returns an item handler that surfaces the cooled part at READY for hopper-style
     * extraction. Inserts are always rejected to preserve the sand/impression workflow.
     */
    public IItemHandler itemHandlerFor(@Nullable Direction side) {
        return new TableItemHandler();
    }

    /** READY-gated single-slot part source; never accepts inserts. */
    private final class TableItemHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot != 0 || state != State.READY) return ItemStack.EMPTY;
            return peekPartItem();
        }

        @Override public int getSlotLimit(int slot) { return 1; }

        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || state != State.READY || amount <= 0) return ItemStack.EMPTY;
            ItemStack peek = peekPartItem();
            if (peek.isEmpty()) return ItemStack.EMPTY;
            if (simulate) return peek;
            return tryRetrievePart();
        }
    }
}
