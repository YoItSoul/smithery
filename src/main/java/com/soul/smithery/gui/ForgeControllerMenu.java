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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Menu for the Forge Controller.
 *
 * The number of forge slots is dynamic — one per interior air block. Because the
 * client may not have a fully-synced BE at menu-open time, the slot count is sent
 * explicitly from the server via the extra-data buf rather than being read from
 * the client's BE. If they disagreed, vanilla's ContainerSetContent packet would
 * crash with IndexOutOfBoundsException.
 *
 * Forge item slots are placed at an off-screen position so vanilla rendering skips
 * them; the screen draws them manually in its scrollable left panel.
 *
 * ContainerData layout:
 *   [0] temperature × 10 (tenths of °C)
 *   [1] fuel mB
 *   [2] fuel capacity mB
 *   [3] structure valid (1/0)
 *   [4] hole count
 *   [5] fluid capacity mB
 *   [6] total stored fluid mB
 *   [7 + i] stored mB for material i (in SmitheryAPI.MATERIALS registry order)
 */
public class ForgeControllerMenu extends AbstractContainerMenu {

    public static final int DATA_TEMP        = 0;
    public static final int DATA_FUEL        = 1;
    public static final int DATA_FUEL_CAP    = 2;
    public static final int DATA_VALID       = 3;
    public static final int DATA_HOLES       = 4;
    public static final int DATA_FLUID_CAP   = 5;
    public static final int DATA_FLUID_TOTAL = 6;
    public static final int DATA_FLUID_BASE  = 7;

    private static final int OFFSCREEN = -9999;

    private final @Nullable ForgeControllerBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final List<Material> materialList;
    private final int forgeSlotCount;
    private final int[] syncData;

    // ----- Client-side (network) entry point -----
    // The buf was filled by ForgeControllerBlock with: BlockPos, then VarInt(slotCount).
    public ForgeControllerMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, buf.readBlockPos(), buf.readVarInt());
    }

    // ----- Client-side construction with explicit slot count from server -----
    private ForgeControllerMenu(int id, Inventory playerInv, BlockPos pos, int forgeSlotCount) {
        this(id, playerInv,
                lookupBE(playerInv.player, pos),  // may exist on client (used by stillValid, screen accessors)
                pos,
                forgeSlotCount,
                new SimpleContainer(Math.max(forgeSlotCount, 1))); // populated by vanilla ContainerSetContent
    }

    // ----- Server-side entry point (from BE.createMenu) -----
    public ForgeControllerMenu(int id, Inventory playerInv, @Nullable ForgeControllerBlockEntity be) {
        this(id, playerInv,
                be,
                be != null ? be.getBlockPos() : BlockPos.ZERO,
                be != null ? be.slots().size() : 0,
                be != null ? be.getSlotContainer() : new SimpleContainer(1));
    }

    // ----- Main constructor (does all the work) -----
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
        this.syncData       = new int[DATA_FLUID_BASE + materialList.size()];

        // Forge item slots — rendered manually by the screen.
        for (int i = 0; i < forgeSlotCount; i++) {
            addSlot(new Slot(slotContainer, i, OFFSCREEN, OFFSCREEN));
        }

        // Player main inventory (rows 0-2).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 143 + row * 18));
            }
        }
        // Hotbar.
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 201));
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

    // ---- Server-side data push ----

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
            for (int i = 0; i < materialList.size(); i++) {
                syncData[DATA_FLUID_BASE + i] = blockEntity.storedFluidMb(materialList.get(i).id());
            }
        }
        super.broadcastChanges();
    }

    // ---- Accessors (read from syncData — valid on both sides) ----

    public float getTemperatureC()      { return syncData[DATA_TEMP] / 10f; }
    public int getFuelMb()              { return syncData[DATA_FUEL]; }
    public int getFuelCapacityMb()      { return syncData[DATA_FUEL_CAP]; }
    public boolean isForgeValid()       { return syncData[DATA_VALID] == 1; }
    public int getHoles()               { return syncData[DATA_HOLES]; }
    public int getFluidCapacityMb()     { return syncData[DATA_FLUID_CAP]; }
    public int getTotalFluidMb()        { return syncData[DATA_FLUID_TOTAL]; }
    public List<Material> getMaterials(){ return materialList; }
    public int getForgeSlotCount()      { return forgeSlotCount; }
    public BlockPos getBlockPos()       { return blockPos; }

    public int getStoredMbForMaterial(int index) {
        int i = DATA_FLUID_BASE + index;
        return (i >= 0 && i < syncData.length) ? syncData[i] : 0;
    }

    // ---- Shift-click ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem().copy();
        if (index < forgeSlotCount) {
            // Forge → player inventory
            if (!moveItemStackTo(slot.getItem(), forgeSlotCount, forgeSlotCount + 36, true))
                return ItemStack.EMPTY;
        } else {
            // Player → forge (first empty slot)
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
