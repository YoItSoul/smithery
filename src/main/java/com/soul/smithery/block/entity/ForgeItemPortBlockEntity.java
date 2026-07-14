package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Pass-through item input port BE for the Forge multiblock. Holds a controller
 * back-reference and forwards inserts into the controller's nearest empty interior slot
 * via {@link ForgeControllerBlockEntity#tryInsertItem(ItemStack, int)}. Extracts are
 * never supported.
 */
public class ForgeItemPortBlockEntity extends BlockEntity {

    private @Nullable BlockPos controllerPos;

    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(ItemPortHandler::new);

    /**
     * Constructs an item port BE bound to the given position and blockstate.
     */
    public ForgeItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_ITEM_PORT.get(), pos, state);
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
     * Inserts up to {@code amount} of {@code stack} into the linked forge's nearest empty
     * interior slot. Returns the number of items actually inserted; 0 if there's no
     * controller, no empty slot, or the forge is invalid.
     */
    public int tryInsert(ItemStack stack, int amount) {
        ForgeControllerBlockEntity ctl = controller();
        if (ctl == null) return 0;
        return ctl.tryInsertItem(stack, amount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("ctlX")) {
            controllerPos = new BlockPos(tag.getInt("ctlX"), tag.getInt("ctlY"), tag.getInt("ctlZ"));
        } else {
            controllerPos = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controllerPos != null) {
            tag.putInt("ctlX", controllerPos.getX());
            tag.putInt("ctlY", controllerPos.getY());
            tag.putInt("ctlZ", controllerPos.getZ());
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    /**
     * Returns the item handler backing this port. Inserts forward into the forge;
     * extracts are unsupported.
     */
    public IItemHandler itemHandlerFor(@Nullable Direction side) {
        return new ItemPortHandler();
    }

    /**
     * Insert-only view over the linked forge. Simulated inserts report "no space" — the
     * forward into the controller's interior is not speculatively reversible, mirroring the
     * pre-port transfer handler that refused transactional inserts.
     */
    private final class ItemPortHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }

        @Override public @NotNull ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }

        @Override public int getSlotLimit(int slot) { return 64; }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (stack.isEmpty()) return false;
            ForgeControllerBlockEntity ctl = controller();
            return ctl != null && ctl.hasEmptyInteriorSlot();
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            if (simulate) return stack;
            int inserted = tryInsert(stack, stack.getCount());
            if (inserted <= 0) return stack;
            return stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }
}
