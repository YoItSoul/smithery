package com.soul.smithery.gui;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.block.entity.ForgeControllerBlockEntity;
import com.soul.smithery.registry.SmitheryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Container menu for the forge controller multiblock.
 *
 * <p>Forge slot count is dynamic (one per interior air block) and is sent explicitly from the
 * server through the extra-data buffer so the client never disagrees with the server's slot
 * vector. Forge item slots are placed off-screen because the screen renders them manually as
 * a scrollable list; the menu still exposes their stacks for tooltips, melt progress, and
 * shift-click routing.
 *
 * <p>Container data layout is documented by the {@code DATA_*} constants; per-fluid stored
 * mB and per-slot melt progress live in trailing variable-length regions.
 */
public class ForgeControllerMenu extends AbstractContainerMenu {

    /** Container data index: forge temperature in tenths of degrees Celsius. */
    public static final int DATA_TEMP            = 0;
    /** Container data index: total fuel stored across all ports, in mB. */
    public static final int DATA_FUEL            = 1;
    /** Container data index: combined fuel capacity in mB. */
    public static final int DATA_FUEL_CAP        = 2;
    /** Container data index: 1 if the multiblock validated, 0 otherwise. */
    public static final int DATA_VALID           = 3;
    /** Container data index: number of leak (hole) positions on the last validation. */
    public static final int DATA_HOLES           = 4;
    /** Container data index: combined fluid capacity in mB. */
    public static final int DATA_FLUID_CAP       = 5;
    /** Container data index: total stored fluid across all materials in mB. */
    public static final int DATA_FLUID_TOTAL     = 6;
    /** Container data index of the player-selected output fluid material; -1 if none. */
    public static final int DATA_OUTPUT_FLUID_IX = 7;
    /** Container data index: 1 if the auto-alloy loop is enabled, 0 if paused. */
    public static final int DATA_ALLOY_ENABLED   = 8;
    /** Container data index where the per-material fluid amounts begin. */
    public static final int DATA_FLUID_BASE      = 9;

    private static final int OFFSCREEN = -9999;
    private static final int FORGE_SLOT_MAX_STACK = 1;

    private final @Nullable ForgeControllerBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final List<Material> materialList;
    private final int forgeSlotCount;
    private final int dataMeltBase;
    private final int[] syncData;

    /**
     * Client-side network constructor invoked by NeoForge with the server-supplied buf.
     *
     * @param id assigned menu id
     * @param playerInv local player's inventory
     * @param buf payload containing the controller position followed by a varint slot count
     */
    public ForgeControllerMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, buf.readBlockPos(), buf.readVarInt());
    }

    private ForgeControllerMenu(int id, Inventory playerInv, BlockPos pos, int forgeSlotCount) {
        this(id, playerInv,
                lookupBE(playerInv.player, pos),
                pos,
                forgeSlotCount,
                new SimpleContainer(Math.max(forgeSlotCount, 1)));
    }

    /**
     * Server-side constructor invoked from the block entity's {@code createMenu}.
     *
     * @param id assigned menu id
     * @param playerInv inventory of the opening player
     * @param be controller block entity, or {@code null} when the BE is missing (fallback path)
     */
    public ForgeControllerMenu(int id, Inventory playerInv, @Nullable ForgeControllerBlockEntity be) {
        this(id, playerInv,
                be,
                be != null ? be.getBlockPos() : BlockPos.ZERO,
                be != null ? be.slots().size() : 0,
                be != null ? be.getSlotContainer() : new SimpleContainer(1));
    }

    private ForgeControllerMenu(int id,
                                Inventory playerInv,
                                @Nullable ForgeControllerBlockEntity be,
                                BlockPos pos,
                                int forgeSlotCount,
                                Container slotContainer) {
        super(SmitheryMenus.FORGE_CONTROLLER.get(), id);
        this.blockEntity    = be;
        this.blockPos       = pos;
        this.materialList   = List.copyOf(SmitheryAPI.MATERIALS.all());
        this.forgeSlotCount = forgeSlotCount;
        this.dataMeltBase   = DATA_FLUID_BASE + materialList.size();
        this.syncData       = new int[dataMeltBase + forgeSlotCount];
        this.syncData[DATA_OUTPUT_FLUID_IX] = -1;

        for (int i = 0; i < forgeSlotCount; i++) {
            addSlot(new Slot(slotContainer, i, OFFSCREEN, OFFSCREEN) {
                @Override public int getMaxStackSize()                  { return FORGE_SLOT_MAX_STACK; }
                @Override public int getMaxStackSize(ItemStack stack)   { return FORGE_SLOT_MAX_STACK; }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 44 + col * 18, 143 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 44 + col * 18, 201));
        }

        addDataSlots(new ContainerData() {
            @Override public int get(int i)          { return syncData[i]; }
            @Override public void set(int i, int v)  { syncData[i] = v; }
            @Override public int getCount()          { return syncData.length; }
        });
    }

    private static @Nullable ForgeControllerBlockEntity lookupBE(Player player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof ForgeControllerBlockEntity be) return be;
        return null;
    }

    @Override
    public void broadcastChanges() {
        if (blockEntity != null) {
            syncData[DATA_TEMP]        = (int)(blockEntity.temperatureC() * 10f);
            syncData[DATA_FUEL]        = blockEntity.totalFuelMb();
            syncData[DATA_FUEL_CAP]    = blockEntity.totalFuelCapacityMb();
            syncData[DATA_VALID]       = blockEntity.lastValidation().valid ? 1 : 0;
            syncData[DATA_HOLES]       = blockEntity.lastValidation().holes();
            syncData[DATA_FLUID_CAP]   = blockEntity.fluidCapacityMb();
            syncData[DATA_FLUID_TOTAL] = blockEntity.totalStoredFluidMb();
            syncData[DATA_OUTPUT_FLUID_IX] = computeOutputFluidIndex(blockEntity);
            syncData[DATA_ALLOY_ENABLED]   = blockEntity.isAlloyEnabled() ? 1 : 0;
            for (int i = 0; i < materialList.size(); i++) {
                com.soul.smithery.registry.SmitheryFluids.Entry entry =
                        com.soul.smithery.registry.SmitheryFluids.forMaterial(materialList.get(i).id());
                syncData[DATA_FLUID_BASE + i] = entry == null ? 0
                        : blockEntity.storedFluidMb(entry.source.get());
            }
            for (int i = 0; i < forgeSlotCount; i++) {
                syncData[dataMeltBase + i] = blockEntity.meltProgressMb(i);
            }
        }
        super.broadcastChanges();
    }

    /**
     * Returns the synced forge temperature in degrees Celsius.
     *
     * @return current temperature in degrees Celsius
     */
    public float getTemperatureC()      { return syncData[DATA_TEMP] / 10f; }

    /**
     * Returns the synced total fuel mB.
     *
     * @return current total fuel in mB
     */
    public int getFuelMb()              { return syncData[DATA_FUEL]; }

    /**
     * Returns the synced combined fuel capacity.
     *
     * @return total fuel capacity in mB
     */
    public int getFuelCapacityMb()      { return syncData[DATA_FUEL_CAP]; }

    /**
     * Returns whether the multiblock was reported valid on the last validation.
     *
     * @return {@code true} if the structure formed correctly
     */
    public boolean isForgeValid()       { return syncData[DATA_VALID] == 1; }

    /**
     * Returns the number of leak positions reported on the last validation.
     *
     * @return number of holes
     */
    public int getHoles()               { return syncData[DATA_HOLES]; }

    /**
     * Returns the synced combined fluid capacity.
     *
     * @return total fluid capacity in mB
     */
    public int getFluidCapacityMb()     { return syncData[DATA_FLUID_CAP]; }

    /**
     * Returns the synced total fluid stored across all materials.
     *
     * @return total stored fluid in mB
     */
    public int getTotalFluidMb()        { return syncData[DATA_FLUID_TOTAL]; }

    /**
     * Returns the snapshot of materials used to size and index the fluid sync arrays.
     *
     * @return immutable material list in registry order
     */
    public List<Material> getMaterials(){ return materialList; }

    /**
     * Returns the number of forge slots this menu was created with.
     *
     * @return forge slot count
     */
    public int getForgeSlotCount()      { return forgeSlotCount; }

    /**
     * Returns the controller block position this menu refers to.
     *
     * @return controller world position
     */
    public BlockPos getBlockPos()       { return blockPos; }

    /**
     * Returns the synced stored mB for the material at the given material-list index.
     *
     * @param index index into {@link #getMaterials()}
     * @return stored mB, or 0 if the index is out of range
     */
    public int getStoredMbForMaterial(int index) {
        int i = DATA_FLUID_BASE + index;
        return (i >= 0 && i < syncData.length) ? syncData[i] : 0;
    }

    /**
     * Returns the index into {@link #getMaterials()} of the selected output fluid.
     *
     * @return material index, or -1 if no output is selected
     */
    public int getOutputFluidMaterialIndex() {
        return syncData[DATA_OUTPUT_FLUID_IX];
    }

    /**
     * Returns whether the controller's auto-alloy loop is enabled.
     *
     * @return {@code true} if alloying is currently enabled on the server
     */
    public boolean isAlloyEnabled() { return syncData[DATA_ALLOY_ENABLED] == 1; }

    /** Menu button id that toggles {@link ForgeControllerBlockEntity#isAlloyEnabled()}. */
    public static final int BUTTON_TOGGLE_ALLOY = 0;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) return false;
        if (id == BUTTON_TOGGLE_ALLOY) {
            blockEntity.setAlloyEnabled(!blockEntity.isAlloyEnabled());
            broadcastChanges();
            return true;
        }
        return false;
    }

    private int computeOutputFluidIndex(ForgeControllerBlockEntity be) {
        net.minecraft.resources.ResourceLocation fluidId = be.outputFluidId();
        if (fluidId == null) return -1;
        for (int i = 0; i < materialList.size(); i++) {
            com.soul.smithery.registry.SmitheryFluids.Entry entry =
                    com.soul.smithery.registry.SmitheryFluids.forMaterial(materialList.get(i).id());
            if (entry == null) continue;
            net.minecraft.resources.ResourceLocation matFluidId =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
            if (fluidId.equals(matFluidId)) return i;
        }
        return -1;
    }

    /**
     * Returns the synced melt progress for the given forge slot.
     *
     * @param slotIndex zero-based forge slot index
     * @return progress in mB, or 0 if the index is out of range
     */
    public int getMeltProgressMb(int slotIndex) {
        int i = dataMeltBase + slotIndex;
        return (slotIndex >= 0 && slotIndex < forgeSlotCount && i < syncData.length)
                ? syncData[i] : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem().copy();
        if (index < forgeSlotCount) {
            if (!moveItemStackTo(slot.getItem(), forgeSlotCount, forgeSlotCount + 36, true))
                return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(slot.getItem(), 0, forgeSlotCount, false))
                return ItemStack.EMPTY;
        }

        if (slot.getItem().isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null
            || ContainerLevelAccess.create(blockEntity.getLevel(), blockPos)
                .evaluate((level, pos) -> player.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0, true);
    }
}
