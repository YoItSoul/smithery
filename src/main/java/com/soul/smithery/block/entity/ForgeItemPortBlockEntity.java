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
 * Pass-through item input port for the Forge multiblock. Holds a back-reference to its
 * controller (set by {@link ForgeControllerBlockEntity#validateStructure} on each pass)
 * and forwards inserts straight into the controller's nearest empty interior slot via
 * {@link ForgeControllerBlockEntity#tryInsertItem(ItemStack, int)}.
 *
 * <p>Extract is not supported — this is an input-only port, in the same spirit as the
 * fuel port's lava-bucket flow but for items.
 */
public class ForgeItemPortBlockEntity extends BlockEntity {

    private @Nullable BlockPos controllerPos;

    public ForgeItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.FORGE_ITEM_PORT.get(), pos, state);
    }

    /** Updates the controller back-reference. Idempotent; only marks dirty on actual change. */
    public void setControllerPos(@Nullable BlockPos pos) {
        if (!Objects.equals(controllerPos, pos)) {
            controllerPos = pos;
            setChanged();
        }
    }

    public @Nullable BlockPos controllerPos() { return controllerPos; }

    /** Resolves the currently-linked controller BE, or null if the link is stale. */
    private @Nullable ForgeControllerBlockEntity controller() {
        if (controllerPos == null || level == null) return null;
        return level.getBlockEntity(controllerPos) instanceof ForgeControllerBlockEntity c ? c : null;
    }

    /**
     * Inserts up to {@code amount} of {@code stack} into the linked forge's nearest empty
     * interior slot. Returns the number actually inserted (0 if no controller, no empty slot,
     * or the forge is invalid).
     */
    public int tryInsert(ItemStack stack, int amount) {
        ForgeControllerBlockEntity ctl = controller();
        if (ctl == null) return 0;
        return ctl.tryInsertItem(stack, amount);
    }

    // ---- NBT ----

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

    // ---- Item capability exposure ----
    //
    // One virtual slot — purely a conduit; the inserted item lands directly in the forge's
    // interior slot list. Hoppers feeding the port effectively feed the forge.

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
            // Item-handler transactions can't easily roll back forge slot writes, so we
            // refuse during open transactions and let the caller commit and retry.
            if (tx != null) return 0;
            return tryInsert(resource.toStack(1), amount);
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            return 0; // input-only port
        }
    }
}
