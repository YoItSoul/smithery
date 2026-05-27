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
import net.minecraft.resources.Identifier;
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
 * Part Press internal state — slots, selected part type, and redstone-driven open/closed pose.
 *
 * <h3>Slot model</h3>
 * Two slots: an input stack (raw material, accepted while open) and an output stack (cut part,
 * extractable while open). Single-item slots — the press cuts one part per close cycle.
 *
 * <h3>Cycle</h3>
 * <ol>
 *   <li>Open (POWERED=false). Player or hopper inserts raw material; bare-hand right-click
 *       cycles {@link #selectedPartIndex} through all non-synthetic part types.</li>
 *   <li>Redstone signal turns ON → block flips POWERED=true → {@link #onPowerChanged} triggers
 *       the close animation and runs the cut once: input shrinks by 1, output gains a fresh
 *       {@code smithery:<material>_<part_type>} stack resolved via {@link SmitheryAPI#MELTING_RECIPES}.</li>
 *   <li>Redstone signal turns OFF → POWERED=false → open animation; output is now reachable by
 *       hoppers or a bare-hand right-click.</li>
 * </ol>
 *
 * <h3>Material lookup</h3>
 * Reuses the global melting-recipe map ({@link SmitheryAPI#MELTING_RECIPES}) as a generic
 * "input item → Smithery material" lookup. mB amount is ignored — the press is a 1:1 cutter,
 * not a forge. Items without a melting recipe are rejected.
 */
public class PartPressBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** Animation controller name (matches the one registered in {@link #registerControllers}). */
    private static final String CONTROLLER = "press";
    /** Trigger animation ids — bone names in {@code part_press.animation.json}. */
    private static final String ANIM_OPEN = "open";
    private static final String ANIM_CLOSE = "close";

    /** "Empty" idle animations the controller defaults to between triggers. */
    private static final RawAnimation IDLE_OPEN  = RawAnimation.begin().thenPlay(ANIM_OPEN);
    private static final RawAnimation IDLE_CLOSE = RawAnimation.begin().thenPlay(ANIM_CLOSE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Index into the non-synthetic part-types list. Clamped on load. */
    private int selectedPartIndex = 0;
    private ItemStack input  = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private boolean closed   = false;

    public PartPressBlockEntity(BlockPos pos, BlockState state) {
        super(SmitheryBlockEntities.PART_PRESS.get(), pos, state);
    }

    // ---- Accessors ----

    public int selectedPartIndex() { return selectedPartIndex; }
    public ItemStack inputItem()   { return input; }
    public ItemStack outputItem()  { return output; }
    public boolean isClosed()      { return closed; }

    /** The PartType currently selected — wraps {@link #selectedPartIndex} into the live registry list. */
    public @Nullable PartType selectedPartType() {
        List<PartType> all = nonSyntheticPartTypes();
        if (all.isEmpty()) return null;
        int idx = Math.floorMod(selectedPartIndex, all.size());
        return all.get(idx);
    }

    // ---- Mutations ----

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
     * True iff the press can accept {@code stack} as raw material. The input slot has a hard
     * capacity of ONE item — once anything is in there, no more inserts until the cut consumes
     * it or the player pulls it back out. Also refuses while the output slot still holds an
     * unclaimed part, since the press isn't "ready for the next job" until the player or a
     * hopper has cleared the previous result.
     */
    public boolean canAcceptInput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!input.isEmpty()) return false;
        if (!output.isEmpty()) return false;
        return resolveMaterialFor(stack) != null;
    }

    /**
     * Resolves an input {@link ItemStack} to the smithery material id it should produce a part of.
     *
     * <p>The press is the <em>non-meltable</em> part-production path — meltable materials
     * (iron, gold, stone, lapis, etc) go through the forge → cast pipeline, not the press.
     * Domains don't overlap; iron ingots are rejected here on purpose so players can't bypass
     * the forge with a redstone tick.
     *
     * <p>Accepted inputs: any log, flint, slime balls (regular + red), resin clumps, and
     * live or dead coral blocks. Returns {@code null} for anything else — the press is the
     * sole non-meltable→part path, so this list is authoritative.
     */
    public static @Nullable Identifier resolveMaterialFor(ItemStack stack) {
        if (stack.is(net.minecraft.tags.ItemTags.LOGS))   return SmitheryMaterials.WOOD;
        if (stack.is(net.minecraft.world.item.Items.FLINT))        return SmitheryMaterials.FLINT;
        if (stack.is(net.minecraft.world.item.Items.SLIME_BALL))   return SmitheryMaterials.SLIME;
        if (stack.is(net.minecraft.world.item.Items.RESIN_CLUMP))  return SmitheryMaterials.RESIN;
        if (isCoralBlockItem(stack)) return SmitheryMaterials.CORAL;
        // Red slime mirrors slime in part-press coverage — every part type slime can make,
        // red slime can too. It also keeps its bowstring eligibility for shaped recipes.
        if (stack.is(com.soul.smithery.registry.SmitheryItems.RED_SLIME.get()))
            return SmitheryMaterials.RED_SLIME;
        // Other bowstring-class materials (string / flamestring / breezestring / kelp_string)
        // are NOT pressed — those bowstring PartItems are produced via shaped crafting recipes
        // (data/smithery/recipe/*_bowstring.json) so each one is a deliberate, hand-formed part.
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

    /** Inserts exactly one item from {@code stack} into the (single-item) input slot. */
    public int insertOne(ItemStack stack) {
        if (!canAcceptInput(stack)) return 0;
        input = stack.copyWithCount(1);
        markDirtyAndSync();
        return 1;
    }

    /** Pops the current output stack out and returns it (caller is responsible for delivery). */
    public ItemStack takeOutput() {
        if (output.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = output;
        output = ItemStack.EMPTY;
        markDirtyAndSync();
        return out;
    }

    /** Pops the whole unpressed input stack out — lets the player retrieve raw material before cutting. */
    public ItemStack takeInput() {
        if (input.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = input;
        input = ItemStack.EMPTY;
        markDirtyAndSync();
        return out;
    }

    /**
     * Called by {@link com.soul.smithery.block.PartPressBlock#neighborChanged} on every transition.
     * Drives the animation trigger and, on a fresh "going closed" edge, performs the cut.
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

    /**
     * Consume one input and emit one part into the output slot, if the inputs+selection are valid
     * and there's room in the output. No-op otherwise. Called once per close-edge.
     */
    private void performCut() {
        if (input.isEmpty()) return;
        PartType pt = selectedPartType();
        if (pt == null) return;
        Identifier materialId = resolveMaterialFor(input);
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

    // ---- NBT ----

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

    // ---- Sync helpers ----

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

    // ---- Geckolib hooks ----

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

    // ---- Item capability exposure ----
    //
    // Two virtual slots: 0 = input, 1 = output. Hoppers can insert into 0 only while open;
    // they extract from 1 only while open. Closed state (POWERED=true) locks both — the press
    // is mid-cut, not a buffer. Insertion is gated by canAcceptInput so hoppers can't smuggle
    // unrelated items into the slot.

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
            // Input slot is single-item; output slot mirrors the part's stack size (always 1 for
            // smithery PartItems but kept generic so future cast outputs aren't capped artificially).
            if (slot == 0) return 1L;
            if (slot == 1) return resource.isEmpty() ? 64L : resource.getItem().getDefaultMaxStackSize();
            return 0L;
        }

        @Override public boolean isValid(int slot, ItemResource resource) {
            if (resource.isEmpty() || closed) return false;
            if (slot == 0) return canAcceptInput(resource.toStack(1));
            return false; // output slot is extract-only
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            if (slot != 0 || closed || resource.isEmpty() || amount <= 0) return 0;
            // Single-item slot: accept at most 1 item, and only when the slot is empty.
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

    // ---- Helpers ----

    /**
     * Selectable PartTypes for the press: registered, non-synthetic, and not bowstring.
     * Bowstrings are excluded because every bowstring PartItem is hand-crafted via shaped
     * recipes (data/smithery/recipe/*_bowstring.json) — the press isn't a valid producer.
     */
    private static List<PartType> nonSyntheticPartTypes() {
        return SmitheryAPI.PART_TYPES.all().stream()
                .filter(pt -> !pt.syntheticCast())
                .filter(pt -> !"bowstring".equals(pt.id().getPath()))
                .toList();
    }
}
