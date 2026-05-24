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

    public static final int DATA_TEMP            = 0;
    public static final int DATA_FUEL            = 1;
    public static final int DATA_FUEL_CAP        = 2;
    public static final int DATA_VALID           = 3;
    public static final int DATA_HOLES           = 4;
    public static final int DATA_FLUID_CAP       = 5;
    public static final int DATA_FLUID_TOTAL     = 6;
    /** Index into materialList of the player-selected output fluid; -1 if none. */
    public static final int DATA_OUTPUT_FLUID_IX = 7;
    /** 1 = alloying enabled (default), 0 = paused. Toggled via the screen's alloy button. */
    public static final int DATA_ALLOY_ENABLED   = 8;
    public static final int DATA_FLUID_BASE      = 9;
    // Melt progress per slot (in mB) lives after the fluid slots in syncData.
    // dataMeltBase = DATA_FLUID_BASE + materialList.size().

    private static final int OFFSCREEN = -9999;
    private static final int FORGE_SLOT_MAX_STACK = 1;

    private final @Nullable ForgeControllerBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final List<Material> materialList;
    private final int forgeSlotCount;
    private final int dataMeltBase;
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
        this.dataMeltBase   = DATA_FLUID_BASE + materialList.size();
        this.syncData       = new int[dataMeltBase + forgeSlotCount];
        // -1 sentinel until the server's first broadcast arrives; the default Java int 0
        // would otherwise be misread as "material index 0 is selected" by the client screen.
        this.syncData[DATA_OUTPUT_FLUID_IX] = -1;

        // Forge item slots — rendered manually by the screen. Capped at 1 stack
        // per slot so each interior air block holds exactly one melting item.
        for (int i = 0; i < forgeSlotCount; i++) {
            addSlot(new Slot(slotContainer, i, OFFSCREEN, OFFSCREEN) {
                @Override public int getMaxStackSize()                  { return FORGE_SLOT_MAX_STACK; }
                @Override public int getMaxStackSize(ItemStack stack)   { return FORGE_SLOT_MAX_STACK; }
            });
        }

        // Player main inventory (rows 0-2) — centered horizontally in the 248px-wide screen.
        // 9 slots × 18px = 162px wide, so left margin = (248 - 162) / 2 = 43,
        // and the first slot's interior begins at x = 43 + 1 (border) = 44.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 44 + col * 18, 143 + row * 18));
            }
        }
        // Hotbar.
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
            syncData[DATA_OUTPUT_FLUID_IX] = computeOutputFluidIndex(blockEntity);
            syncData[DATA_ALLOY_ENABLED]   = blockEntity.isAlloyEnabled() ? 1 : 0;
            for (int i = 0; i < materialList.size(); i++) {
                // Storage is now keyed by Fluid (not Material). Look up the molten fluid for
                // each material; if absent (e.g. wood has no molten form), report 0.
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

    /** Index into {@link #getMaterials()} of the currently-selected output fluid; -1 if none. */
    public int getOutputFluidMaterialIndex() {
        return syncData[DATA_OUTPUT_FLUID_IX];
    }

    /** Whether the controller's auto-alloy loop is enabled (synced from server). */
    public boolean isAlloyEnabled() { return syncData[DATA_ALLOY_ENABLED] == 1; }

    // ---- Menu button IDs (used by clickMenuButton) ----
    /** Toggles {@link ForgeControllerBlockEntity#isAlloyEnabled()}. */
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

    /**
     * Server-side helper to resolve the controller's selected Fluid id into a material-list
     * index. -1 if no selection or the selected fluid doesn't map to one of our materials.
     */
    private int computeOutputFluidIndex(ForgeControllerBlockEntity be) {
        net.minecraft.resources.Identifier fluidId = be.outputFluidId();
        if (fluidId == null) return -1;
        for (int i = 0; i < materialList.size(); i++) {
            com.soul.smithery.registry.SmitheryFluids.Entry entry =
                    com.soul.smithery.registry.SmitheryFluids.forMaterial(materialList.get(i).id());
            if (entry == null) continue;
            net.minecraft.resources.Identifier matFluidId =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
            if (fluidId.equals(matFluidId)) return i;
        }
        return -1;
    }

    /** Synced melt progress (mB) for the given forge slot; 0 if out of range. */
    public int getMeltProgressMb(int slotIndex) {
        int i = dataMeltBase + slotIndex;
        return (slotIndex >= 0 && slotIndex < forgeSlotCount && i < syncData.length)
                ? syncData[i] : 0;
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
