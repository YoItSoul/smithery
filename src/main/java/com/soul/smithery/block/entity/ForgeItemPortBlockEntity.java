package com.soul.smithery.block.entity;

import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Pass-through item input port BE for the Forge multiblock. Holds a controller
 * back-reference and forwards inserts into the controller's nearest empty interior slot
 * via {@link ForgeControllerBlockEntity#tryInsertItem(ItemStack, int)}. Extracts are
 * never supported.
 */
public class ForgeItemPortBlockEntity extends BlockEntity {

    private @Nullable BlockPos controllerPos;

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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<Integer> x = input.getInt("ctlX");
        if (x.isPresent()) {
            controllerPos = new BlockPos(x.get(),
                    input.getInt("ctlY").orElse(0),
                    input.getInt("ctlZ").orElse(0));
        } else {
            controllerPos = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (controllerPos != null) {
            output.putInt("ctlX", controllerPos.getX());
            output.putInt("ctlY", controllerPos.getY());
            output.putInt("ctlZ", controllerPos.getZ());
        }
    }

    /**
     * Returns an item capability backed by this port. Inserts forward into the forge;
     * extracts are unsupported.
     */
    public ResourceHandler<ItemResource> itemHandlerFor(@Nullable Direction side) {
        return new ItemPortHandler();
    }

    private final class ItemPortHandler implements ResourceHandler<ItemResource> {
        @Override public int size() { return 1; }

        @Override public ItemResource getResource(int slot) { return ItemResource.EMPTY; }

        @Override public long getAmountAsLong(int slot) { return 0; }

        @Override public long getCapacityAsLong(int slot, ItemResource resource) {
            return resource.isEmpty() ? 64L : resource.getItem().getDefaultMaxStackSize();
        }

        @Override public boolean isValid(int slot, ItemResource resource) {
            if (resource.isEmpty()) return false;
            ForgeControllerBlockEntity ctl = controller();
            return ctl != null && ctl.hasEmptyInteriorSlot();
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || resource.isEmpty() || amount <= 0) return 0;
            if (tx != null) return 0;
            return tryInsert(resource.toStack(1), amount);
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            return 0;
        }
    }
}
