package com.soul.smithery.block.entity;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

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

    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(PressItemHandler::new);

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
        Smithery.LOGGER.info("[PartPress @{}] cycled to index {} ({})",
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
     * The press handles only non-meltable inputs (logs, flint, slime, coral, red
     * slime); meltables go through the forge/cast pipeline instead. Returns null for
     * unsupported items.
     *
     * <p>1.20.1 has no resin item, so the resin material has no press input on this
     * branch.
     */
    public static @Nullable ResourceLocation resolveMaterialFor(ItemStack stack) {
        if (stack.is(ItemTags.LOGS))        return SmitheryMaterials.WOOD;
        if (stack.is(Items.FLINT))          return SmitheryMaterials.FLINT;
        if (stack.is(Items.SLIME_BALL))     return SmitheryMaterials.SLIME;
        if (isCoralBlockItem(stack))        return SmitheryMaterials.CORAL;
        if (stack.is(SmitheryItems.RED_SLIME.get())) return SmitheryMaterials.RED_SLIME;
        return null;
    }

    private static boolean isCoralBlockItem(ItemStack stack) {
        var it = stack.getItem();
        return it == Items.TUBE_CORAL_BLOCK
            || it == Items.BRAIN_CORAL_BLOCK
            || it == Items.BUBBLE_CORAL_BLOCK
            || it == Items.FIRE_CORAL_BLOCK
            || it == Items.HORN_CORAL_BLOCK
            || it == Items.DEAD_TUBE_CORAL_BLOCK
            || it == Items.DEAD_BRAIN_CORAL_BLOCK
            || it == Items.DEAD_BUBBLE_CORAL_BLOCK
            || it == Items.DEAD_FIRE_CORAL_BLOCK
            || it == Items.DEAD_HORN_CORAL_BLOCK;
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
        var partItem = SmitheryItems.findPart(materialId, pt.id());
        if (partItem == null) return;

        ItemStack produced = new ItemStack(partItem);
        if (!output.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(output, produced)) return;
            if (output.getCount() >= output.getMaxStackSize()) return;
            output.grow(1);
        } else {
            output = produced;
        }
        input.shrink(1);
        if (input.isEmpty()) input = ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedPartIndex", selectedPartIndex);
        tag.putBoolean("closed", closed);
        if (!input.isEmpty())  tag.put("input",  input.save(new CompoundTag()));
        if (!output.isEmpty()) tag.put("output", output.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int newIndex = tag.getInt("selectedPartIndex");
        if (newIndex != selectedPartIndex) {
            Smithery.LOGGER.info("[PartPress @{}] load: index {} -> {} (side={})",
                    worldPosition, selectedPartIndex, newIndex,
                    level != null && level.isClientSide() ? "client" : "server");
        }
        selectedPartIndex = newIndex;
        closed = tag.getBoolean("closed");
        input  = tag.contains("input")  ? ItemStack.of(tag.getCompound("input"))  : ItemStack.EMPTY;
        output = tag.contains("output") ? ItemStack.of(tag.getCompound("output")) : ItemStack.EMPTY;
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<PartPressBlockEntity> ctrl = new AnimationController<>(
                this, CONTROLLER, 0, state -> state.setAndContinue(closed ? IDLE_CLOSE : IDLE_OPEN));
        ctrl.triggerableAnim(ANIM_OPEN,  IDLE_OPEN)
            .triggerableAnim(ANIM_CLOSE, IDLE_CLOSE);
        controllers.add(ctrl);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
     * Returns the press's item handler: slot 0 is the input (insert-only while open),
     * slot 1 is the output (extract-only while open). Both lock when the press is closed.
     */
    public IItemHandler itemHandlerFor(@Nullable Direction side) {
        return new PressItemHandler();
    }

    /** Two-slot view: input slot accepts one pressable item while open, output extracts while open. */
    private final class PressItemHandler implements IItemHandler {
        @Override public int getSlots() { return 2; }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot == 0 ? input : (slot == 1 ? output : ItemStack.EMPTY);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (stack.isEmpty() || closed) return false;
            if (slot == 0) return canAcceptInput(stack.copyWithCount(1));
            return false;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != 0 || closed || stack.isEmpty()) return stack;
            if (!input.isEmpty()) return stack;
            if (!canAcceptInput(stack.copyWithCount(1))) return stack;
            if (!simulate) {
                input = stack.copyWithCount(1);
                markDirtyAndSync();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || closed || amount <= 0) return ItemStack.EMPTY;
            if (output.isEmpty()) return ItemStack.EMPTY;
            int extracted = Math.min(amount, output.getCount());
            ItemStack result = output.copyWithCount(extracted);
            if (!simulate) {
                output.shrink(extracted);
                if (output.isEmpty()) output = ItemStack.EMPTY;
                markDirtyAndSync();
            }
            return result;
        }
    }

    private static List<PartType> nonSyntheticPartTypes() {
        return SmitheryAPI.PART_TYPES.all().stream()
                .filter(pt -> !pt.syntheticCast())
                .filter(pt -> !"bowstring".equals(pt.id().getPath()))
                .toList();
    }
}
