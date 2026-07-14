package com.soul.smithery.block.entity;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryItems;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Part Press internal state: input/output slots, selected part type, redstone-driven
 * open/closed pose, and the Geckolib animation hookup. Open while unpowered (insert
 * input, extract output, cycle template); on the rising power edge, runs one cut from
 * input to output using the selected part type and the input's resolved material.
 */
public class PartPressBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final String CONTROLLER = "press";
    private static final String ANIM_OPEN = "open";
    private static final String ANIM_CLOSE = "close";

    private static final RawAnimation IDLE_OPEN  = RawAnimation.begin().thenPlay(ANIM_OPEN);
    private static final RawAnimation IDLE_CLOSE = RawAnimation.begin().thenPlay(ANIM_CLOSE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int selectedPartIndex = 0;
    private ItemStack input  = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private boolean closed   = false;

    /**
     * Constructs a part press BE bound to the given position and blockstate.
     */
    public PartPressBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.PART_PRESS.get(), pos, state);
    }

    /** Current index into the non-synthetic part-types list. */
    public int selectedPartIndex() { return selectedPartIndex; }
    /** Current input slot stack ({@code ItemStack.EMPTY} when empty). */
    public ItemStack inputItem()   { return input; }
    /** Current output slot stack ({@code ItemStack.EMPTY} when empty). */
    public ItemStack outputItem()  { return output; }
    /** True when the press is in its powered closed pose. */
    public boolean isClosed()      { return closed; }

    /**
     * Returns the PartType the press is currently configured to cut, wrapped through
     * the live registry list. Null if no selectable part types exist.
     */
    public @Nullable PartType selectedPartType() {
        List<PartType> all = nonSyntheticPartTypes();
        if (all.isEmpty()) return null;
        int idx = Math.floorMod(selectedPartIndex, all.size());
        return all.get(idx);
    }

    /**
     * Advances {@link #selectedPartIndex} to the next selectable part type and syncs
     * to the client.
     */
    public void cycleSelectedPart() {
        List<PartType> all = nonSyntheticPartTypes();
        if (all.isEmpty()) return;
        selectedPartIndex = Math.floorMod(selectedPartIndex + 1, all.size());
        markDirtyAndSync();
        com.soul.smithery.Smithery.LOGGER.info("[PartPress @{}] cycled to index {} ({})",
                worldPosition, selectedPartIndex,
                selectedPartType() != null ? selectedPartType().id() : "null");
    }

    /**
     * True iff the press can currently accept {@code stack} as raw material. Requires
     * an empty input slot, an empty output slot, and a resolved material via
     * {@link #resolveMaterialFor(ItemStack)}.
     */
    public boolean canAcceptInput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!input.isEmpty()) return false;
        if (!output.isEmpty()) return false;
        return resolveMaterialFor(stack) != null;
    }

    /**
     * Resolves an input item to the smithery material id it should produce a part of.
     * The press handles only non-meltable inputs (logs, flint, slime, resin, coral,
     * red slime); meltables go through the forge/cast pipeline instead. Returns null
     * for unsupported items.
     */
    public static @Nullable ResourceLocation resolveMaterialFor(ItemStack stack) {
        if (stack.is(net.minecraft.tags.ItemTags.LOGS))   return SmitheryMaterials.WOOD;
        if (stack.is(net.minecraft.world.item.Items.FLINT))        return SmitheryMaterials.FLINT;
        if (stack.is(net.minecraft.world.item.Items.SLIME_BALL))   return SmitheryMaterials.SLIME;
        if (stack.is(net.minecraft.world.item.Items.RESIN_CLUMP))  return SmitheryMaterials.RESIN;
        if (isCoralBlockItem(stack)) return SmitheryMaterials.CORAL;
        if (stack.is(com.soul.smithery.registry.SmitheryItems.RED_SLIME.get()))
            return SmitheryMaterials.RED_SLIME;
        return null;
    }

    private static boolean isCoralBlockItem(ItemStack stack) {
        var it = stack.getItem();
        return it == net.minecraft.world.item.Items.TUBE_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.BRAIN_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.BUBBLE_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.FIRE_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.HORN_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.DEAD_TUBE_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.DEAD_BRAIN_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.DEAD_BUBBLE_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.DEAD_FIRE_CORAL_BLOCK
            || it == net.minecraft.world.item.Items.DEAD_HORN_CORAL_BLOCK;
    }

    /**
     * Inserts exactly one item from {@code stack} into the single-item input slot.
     * Returns 1 on success, 0 if rejected.
     */
    public int insertOne(ItemStack stack) {
        if (!canAcceptInput(stack)) return 0;
        input = stack.copyWithCount(1);
        markDirtyAndSync();
        return 1;
    }

    /** Pops the output stack out and clears it; caller is responsible for delivery. */
    public ItemStack takeOutput() {
        if (output.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = output;
        output = ItemStack.EMPTY;
        markDirtyAndSync();
        return out;
    }

    /** Pops the unpressed input stack out and clears it; caller is responsible for delivery. */
    public ItemStack takeInput() {
        if (input.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = input;
        input = ItemStack.EMPTY;
        markDirtyAndSync();
        return out;
    }

    /**
     * Power state transition hook from {@link com.soul.smithery.block.PartPressBlock}.
     * On the rising "going closed" edge triggers the cut and the close animation; on
     * the falling edge triggers the open animation.
     */
    public void onPowerChanged(boolean powered) {
        if (powered == closed) return;
        closed = powered;
        if (closed) {
            performCut();
            triggerAnim(CONTROLLER, ANIM_CLOSE);
        } else {
            triggerAnim(CONTROLLER, ANIM_OPEN);
        }
        markDirtyAndSync();
    }

    private void performCut() {
        if (input.isEmpty()) return;
        PartType pt = selectedPartType();
        if (pt == null) return;
        ResourceLocation materialId = resolveMaterialFor(input);
        if (materialId == null) return;
        var partItem = SmitheryItems.getBuiltInPart(materialId, pt.id());
        if (partItem == null) return;

        ItemStack produced = new ItemStack(partItem.get());
        if (!output.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(output, produced)) return;
            if (output.getCount() >= output.getMaxStackSize()) return;
            output.grow(1);
        } else {
            output = produced;
        }
        input.shrink(1);
        if (input.isEmpty()) input = ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("selectedPartIndex", selectedPartIndex);
        out.putBoolean("closed", closed);
        if (!input.isEmpty())  out.store("input",  ItemStack.OPTIONAL_CODEC, input);
        if (!output.isEmpty()) out.store("output", ItemStack.OPTIONAL_CODEC, output);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int newIndex = in.getIntOr("selectedPartIndex", 0);
        if (newIndex != selectedPartIndex) {
            com.soul.smithery.Smithery.LOGGER.info(
                    "[PartPress @{}] loadAdditional: index {} -> {} (side={})",
                    worldPosition, selectedPartIndex, newIndex,
                    level != null && level.isClientSide() ? "client" : "server");
        }
        selectedPartIndex = newIndex;
        closed = in.getBooleanOr("closed", false);
        input  = in.read("input",  ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        output = in.read("output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<PartPressBlockEntity> ctrl = new AnimationController<>(
                CONTROLLER, 0, state -> state.setAndContinue(closed ? IDLE_CLOSE : IDLE_OPEN));
        ctrl.triggerableAnim(ANIM_OPEN,  IDLE_OPEN)
            .triggerableAnim(ANIM_CLOSE, IDLE_CLOSE);
        controllers.add(ctrl);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * Returns the press's item capability: slot 0 is the input (insert-only while open),
     * slot 1 is the output (extract-only while open). Both lock when the press is closed.
     */
    public ResourceHandler<ItemResource> itemHandlerFor(@Nullable Direction side) {
        return new PressItemHandler();
    }

    private final class PressItemHandler implements ResourceHandler<ItemResource> {
        @Override public int size() { return 2; }

        @Override public ItemResource getResource(int slot) {
            ItemStack s = slot == 0 ? input : (slot == 1 ? output : ItemStack.EMPTY);
            return s.isEmpty() ? ItemResource.EMPTY : ItemResource.of(s);
        }

        @Override public long getAmountAsLong(int slot) {
            ItemStack s = slot == 0 ? input : (slot == 1 ? output : ItemStack.EMPTY);
            return s.getCount();
        }

        @Override public long getCapacityAsLong(int slot, ItemResource resource) {
            if (slot == 0) return 1L;
            if (slot == 1) return resource.isEmpty() ? 64L : resource.getItem().getDefaultMaxStackSize();
            return 0L;
        }

        @Override public boolean isValid(int slot, ItemResource resource) {
            if (resource.isEmpty() || closed) return false;
            if (slot == 0) return canAcceptInput(resource.toStack(1));
            return false;
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || closed || resource.isEmpty() || amount <= 0) return 0;
            if (!input.isEmpty()) return 0;
            if (!canAcceptInput(resource.toStack(1))) return 0;
            input = resource.toStack(1);
            markDirtyAndSync();
            return 1;
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (slot != 1 || closed || resource.isEmpty() || amount <= 0) return 0;
            if (output.isEmpty() || resource.getItem() != output.getItem()) return 0;
            int extracted = Math.min(amount, output.getCount());
            output.shrink(extracted);
            if (output.isEmpty()) output = ItemStack.EMPTY;
            markDirtyAndSync();
            return extracted;
        }
    }

    private static List<PartType> nonSyntheticPartTypes() {
        return SmitheryAPI.PART_TYPES.all().stream()
                .filter(pt -> !pt.syntheticCast())
                .filter(pt -> !"bowstring".equals(pt.id().getPath()))
                .toList();
    }
}
