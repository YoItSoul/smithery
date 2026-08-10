package com.soul.smithery.block.entity;

import com.soul.smithery.api.cast.CastBlocks;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryFluids;
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
import net.minecraft.world.item.Items;
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
 * State and behaviour for a Casting Basin — the block-scale counterpart to the Casting Table.
 *
 * <p>Drives an EMPTY -&gt; FILLING -&gt; COOLING -&gt; READY cycle. There is no sand or impression
 * step: a basin has exactly one shape, so the first splash of molten material decides what it is
 * casting. The portion and the resulting item come from {@link CastBlocks}, and a fluid with no
 * registered block cast is refused rather than swallowed.
 */
public class CastingBasinBlockEntity extends BlockEntity {

    /**
     * Cast cycle states for a single basin.
     */
    public enum State {
        /** Bare basin — nothing poured. */
        EMPTY,
        /** Mid-pour; partially filled with one fluid. */
        FILLING,
        /** Fully filled and currently cooling down. */
        COOLING,
        /** Finished block ready to be picked up. */
        READY;

        static State byName(String name) {
            for (State s : values()) if (s.name().equals(name)) return s;
            return EMPTY;
        }
    }

    /**
     * Per-mB cooling time in server ticks, shared with {@link CastingTableBlockEntity} on purpose:
     * because a basin cast is nine of the material's castable unit, one rate for both machines is
     * what makes a basin take exactly nine times as long to cool as casting that unit on a table.
     */
    public static final int COOLING_TICKS_PER_MB = CastingTableBlockEntity.COOLING_TICKS_PER_MB;

    /**
     * How long the pour stream keeps rendering after the last mB arrived. A drain pushes every
     * tick while a job runs, so a few ticks of slack bridges packet jitter and the gap between two
     * pours without leaving a stream hanging in the air after the flow stops.
     */
    private static final int POUR_LINGER_TICKS = 4;

    private State state = State.EMPTY;
    private Fluid pouredFluid = Fluids.EMPTY;
    private int   requiredMb = 0;
    private int   filledMb = 0;
    private int   coolingTicksRemaining = 0;
    /**
     * Item this cast will yield, resolved once when the first pour locks the cast in and then
     * persisted. Recorded rather than re-resolved so the answer survives the data pack that produced
     * it changing mid-cast, and so it reaches the client — {@link CastBlocks}'s data layer is
     * server-side, and without this a data-pack cast would render as nothing on a dedicated server.
     */
    private @Nullable ResourceLocation resultItemId;

    /** Client-only pour-stream timer and the fill level it was last compared against. */
    private int pourTicks;
    private int lastSeenFilledMb;

    /**
     * Constructs a casting basin BE bound to the given position and blockstate.
     */
    public CastingBasinBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.CASTING_BASIN.get(), pos, state);
    }

    /** Returns the current cycle {@link State}. */
    public State state() { return state; }
    /** Returns the fluid currently poured (or being poured); {@code Fluids.EMPTY} when none. */
    public Fluid pouredFluid() { return pouredFluid; }
    /** Returns the mB needed to complete the cast of the poured fluid; 0 while EMPTY. */
    public int requiredMb() { return requiredMb; }
    /** Returns the mB poured so far toward {@link #requiredMb()}. */
    public int filledMb() { return filledMb; }
    /** Returns the remaining cooling-state ticks before the cast becomes READY. */
    public int coolingTicksRemaining() { return coolingTicksRemaining; }

    /**
     * How full the basin is as a 0..1 fraction, used by the renderer to set the fluid surface
     * height. Reads as full from COOLING onward.
     */
    public float fillFraction() {
        if (requiredMb <= 0) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, (float) filledMb / (float) requiredMb));
    }

    /**
     * Cooling progress as a 0..1 fraction used by the renderer to fade the pool from molten colour
     * to the solid material colour. Returns 1.0 during FILLING and 0.0 outside of FILLING / COOLING.
     */
    public float coolingFraction() {
        if (state == State.FILLING) return 1.0f;
        if (state != State.COOLING) return 0.0f;
        int total = Math.max(1, requiredMb * COOLING_TICKS_PER_MB);
        return Math.max(0.0f, Math.min(1.0f, (float) coolingTicksRemaining / (float) total));
    }

    /**
     * True iff this basin can currently accept poured fluid (either EMPTY, or partially FILLING
     * with room remaining).
     */
    public boolean acceptsPour() {
        return state == State.EMPTY || (state == State.FILLING && filledMb < requiredMb);
    }

    /**
     * Pours fluid into an EMPTY or partially-FILLING basin, locking the fluid identity and the
     * portion on the first pour and rejecting mismatched fluids thereafter. Returns the mB actually
     * accepted; transitions to COOLING when the basin is full.
     */
    public int tryPourFluid(Fluid fluid, int mb) {
        if (fluid == null || fluid == Fluids.EMPTY || mb <= 0) return 0;
        if (!acceptsPour()) return 0;
        if (state == State.EMPTY) {
            CastBlocks.Cast cast = castFor(fluid);
            if (cast == null) return 0;
            Item result = cast.result().get();
            ResourceLocation resultId = result == null ? null : ForgeRegistries.ITEMS.getKey(result);
            // A cast that resolves to nothing would cool into an empty basin; refuse the pour.
            if (resultId == null) return 0;
            pouredFluid = fluid;
            requiredMb = cast.mb();
            resultItemId = resultId;
            filledMb = 0;
        } else if (pouredFluid != fluid) {
            return 0;
        }

        int accepted = Math.min(mb, requiredMb - filledMb);
        if (accepted <= 0) return 0;
        filledMb += accepted;

        state = filledMb >= requiredMb ? State.COOLING : State.FILLING;
        if (state == State.COOLING) {
            coolingTicksRemaining = Math.max(1, requiredMb * COOLING_TICKS_PER_MB);
        }
        markDirtyAndSync();
        return accepted;
    }

    /**
     * Server-side cooling countdown. Every other transition is pour- or interaction-driven.
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
     * Client-side cooling prediction so the renderer's molten-to-solid lerp transitions smoothly
     * between the sparse server syncs. Re-converges naturally on the READY snap. Also drives the
     * pour-stream timer — see {@link #isPouring()}.
     */
    public void clientTick() {
        if (filledMb > lastSeenFilledMb) {
            pourTicks = POUR_LINGER_TICKS;
        } else if (pourTicks > 0) {
            pourTicks--;
        }
        lastSeenFilledMb = filledMb;

        if (state == State.COOLING && coolingTicksRemaining > 0) {
            coolingTicksRemaining--;
        }
    }

    /**
     * True while the basin is visibly being poured into, so the renderer can draw the stream
     * falling from the spout above.
     *
     * <p>Derived on the client from the fill level climbing rather than tracked as its own synced
     * flag: every pour already calls {@link #markDirtyAndSync()}, so the rise is visible without
     * spending another field on the wire.
     */
    public boolean isPouring() {
        return pourTicks > 0;
    }

    /**
     * READY -&gt; EMPTY: resolves the cooled cast into its block item, returns it for the caller to
     * deliver, and empties the basin for the next pour. Returns {@link ItemStack#EMPTY} when not
     * READY, or when the recorded result item is gone from the registry entirely, in which case the
     * basin still empties rather than staying stuck.
     */
    public ItemStack tryRetrieveBlock() {
        if (state != State.READY) return ItemStack.EMPTY;
        ItemStack result = resolveBlockItem();
        state = State.EMPTY;
        pouredFluid = Fluids.EMPTY;
        requiredMb = 0;
        resultItemId = null;
        filledMb = 0;
        coolingTicksRemaining = 0;
        markDirtyAndSync();
        return result;
    }

    /**
     * Returns the block item this basin would produce now, without consuming the cast. Used by the
     * renderer to draw the finished block and by the block to drop it when broken.
     */
    public ItemStack peekBlockItem() {
        return resolveBlockItem();
    }

    private ItemStack resolveBlockItem() {
        if (resultItemId == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(resultItemId);
        return (item == null || item == Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** The basin cast for whatever material {@code fluid} is the molten form of, or null. */
    private static CastBlocks.Cast castFor(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return null;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        return entry == null ? null : CastBlocks.resolve(entry.materialId);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("state", state.name());
        if (pouredFluid != Fluids.EMPTY) {
            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(pouredFluid);
            if (fluidId != null) tag.putString("pouredFluid", fluidId.toString());
        }
        if (requiredMb > 0) {
            tag.putInt("requiredMb", requiredMb);
        }
        if (resultItemId != null) {
            tag.putString("resultItem", resultItemId.toString());
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
        if (tag.contains("pouredFluid")) {
            ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString("pouredFluid"));
            Fluid loaded = fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
            pouredFluid = loaded == null ? Fluids.EMPTY : loaded;
        } else {
            pouredFluid = Fluids.EMPTY;
        }
        requiredMb = tag.getInt("requiredMb");
        resultItemId = tag.contains("resultItem")
                ? ResourceLocation.tryParse(tag.getString("resultItem"))
                : null;
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

    private final LazyOptional<IFluidHandler> fluidCapUp = LazyOptional.of(BasinFluidHandler::new);
    private final LazyOptional<IItemHandler>  itemCap    = LazyOptional.of(BasinItemHandler::new);

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
     * Returns a write-only fluid handler accessible only from the basin's UP face.
     * Non-top sides see no handler, matching the visual "top-pour only" model.
     */
    public @Nullable IFluidHandler fluidHandlerFor(@Nullable Direction side) {
        if (side != null && side != Direction.UP) return null;
        return new BasinFluidHandler();
    }

    /** Write-only pour target; the poured fluid is never extractable. */
    private final class BasinFluidHandler implements IFluidHandler {
        @Override public int getTanks() { return 1; }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return (tank != 0 || pouredFluid == Fluids.EMPTY || filledMb <= 0)
                    ? FluidStack.EMPTY : new FluidStack(pouredFluid, filledMb);
        }

        // An empty basin has no portion yet, so it advertises the largest one it could take. Any
        // pipe or tank that asks is told the truth again the moment a fluid locks the cast in.
        @Override
        public int getTankCapacity(int tank) {
            return requiredMb > 0 ? requiredMb : largestRegisteredCastMb();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (!acceptsPour() || stack.isEmpty()) return false;
            if (pouredFluid != Fluids.EMPTY) return stack.getFluid() == pouredFluid;
            return castFor(stack.getFluid()) != null;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!acceptsPour() || resource.isEmpty()) return 0;
            int capacity;
            if (pouredFluid != Fluids.EMPTY) {
                if (resource.getFluid() != pouredFluid) return 0;
                capacity = requiredMb;
            } else {
                CastBlocks.Cast cast = castFor(resource.getFluid());
                if (cast == null) return 0;
                capacity = cast.mb();
            }
            if (action.simulate()) {
                return Math.min(resource.getAmount(), Math.max(0, capacity - filledMb));
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

    private static int largestRegisteredCastMb() {
        int max = 0;
        for (CastBlocks.Cast cast : CastBlocks.all().values()) {
            max = Math.max(max, cast.mb());
        }
        return max;
    }

    /**
     * Returns an item handler that surfaces the cooled block at READY for hopper-style extraction.
     * Inserts are always rejected — a basin is filled with fluid, never with items.
     */
    public IItemHandler itemHandlerFor(@Nullable Direction side) {
        return new BasinItemHandler();
    }

    /** READY-gated single-slot block source; never accepts inserts. */
    private final class BasinItemHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot != 0 || state != State.READY) return ItemStack.EMPTY;
            return peekBlockItem();
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
            ItemStack peek = peekBlockItem();
            if (peek.isEmpty()) return ItemStack.EMPTY;
            if (simulate) return peek;
            return tryRetrieveBlock();
        }
    }
}
