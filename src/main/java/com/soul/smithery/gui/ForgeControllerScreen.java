package com.soul.smithery.gui;

import com.soul.smithery.api.material.Material;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * GUI for the Forge Controller.
 *
 * Left panel  — scrollable list of forge item slots (click to take, scroll to scroll).
 * Right panel — molten fluid levels per material.
 * Status strip — temperature + fuel bar below the panels.
 * Player inventory at standard positions matching ForgeControllerMenu slot coordinates.
 *
 * Uses MC 26.1.2's extract* rendering API (GuiGraphicsExtractor).
 */
public class ForgeControllerScreen extends AbstractContainerScreen<ForgeControllerMenu> {

    // ---- Layout ----
    private static final int IMG_W = 248;
    private static final int IMG_H = 222;

    // Left panel (outer)
    private static final int PL_X = 7;
    private static final int PL_Y = 18;
    private static final int PL_W = 116;
    private static final int PL_H = 105;

    // Right panel (outer)
    private static final int PR_X = 127;
    private static final int PR_Y = 18;
    private static final int PR_W = 114;
    private static final int PR_H = 105;

    // Left panel inner content area (2px border on each side)
    private static final int PLC_X = PL_X + 2;
    private static final int PLC_Y = PL_Y + 2;
    private static final int PLC_W = PL_W - 4;   // 112

    // Right panel inner content area
    private static final int PRC_X = PR_X + 2;
    private static final int PRC_Y = PR_Y + 2;
    private static final int PRC_W = PR_W - 4;   // 110

    // Forge slot list inside left panel: 10px header row, then item rows
    private static final int LIST_HEADER_H = 10;
    private static final int ROW_H         = 18;
    private static final int ROW_W         = PLC_W - 5; // leave 5px for scroll bar
    private static final int SLOTS_VISIBLE = (PL_H - 4 - LIST_HEADER_H) / ROW_H; // 5

    // Fluid list inside right panel.
    // Header height = 20 because we stack "Molten Metals" (y+0) and the total
    // capacity readout (y+10) on separate lines — they don't fit side-by-side
    // in the panel's 110px content width.
    private static final int FLUID_ROW_H    = 20;
    private static final int FLUID_HEADER_H = 20;

    // ---- Colors ----
    private static final int COL_BG       = 0xFFC6C6C6;
    private static final int COL_BORDER   = 0xFF787878;
    private static final int COL_INNER    = 0xFFD4D4D4;
    private static final int COL_ROW_BRD  = 0xFF595959;
    private static final int COL_ROW_BG   = 0xFF9A9A9A;
    private static final int COL_HOVER    = 0x60FFFFFF;
    private static final int COL_TEXT     = 0xFF3F3F3F;
    private static final int COL_GRAY     = 0xFF888888;
    private static final int COL_BAR_BG   = 0xFF333333;
    private static final int COL_FUEL     = 0xFFFF7700;
    private static final int COL_TEMP_HOT = 0xFFFF5500;
    private static final int COL_SCBAR    = 0xFFAAAAAA;

    // ---- State ----
    private int scrollOffset = 0;

    public ForgeControllerScreen(ForgeControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMG_W, IMG_H);
        this.titleLabelX     = PL_X + 2;
        this.titleLabelY     = 6;
        this.inventoryLabelY = 134; // 9px above player inventory at y=143
    }

    // ---- Render pipeline (extract* pattern) ----

    /** Replaces renderBg — draws the custom GUI background and custom panels. */
    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;

        // Main background fill.
        g.fill(x, y, x + IMG_W, y + IMG_H, COL_BG);

        // Panels.
        drawPanel(g, x + PL_X, y + PL_Y, PL_W, PL_H);
        drawPanel(g, x + PR_X, y + PR_Y, PR_W, PR_H);

        // Player inventory slot backgrounds (no texture, so drawn manually).
        drawPlayerInvSlots(g, x, y);

        // Panel contents.
        renderForgeSlots(g, x, y, mouseX, mouseY);
        renderFluidList(g, x, y);
        renderStatusStrip(g, x, y);

        // Forge slot item tooltip (super.extractContents doesn't handle our off-screen slots).
        int listTopY  = y + PLC_Y + LIST_HEADER_H;
        int listLeftX = x + PLC_X;
        if (mouseX >= listLeftX && mouseX < listLeftX + ROW_W
                && mouseY >= listTopY && mouseY < listTopY + SLOTS_VISIBLE * ROW_H) {
            int row = (mouseY - listTopY) / ROW_H;
            int slotIdx = scrollOffset + row;
            if (slotIdx >= 0 && slotIdx < menu.getForgeSlotCount()) {
                ItemStack stack = menu.getSlot(slotIdx).getItem();
                if (!stack.isEmpty()) {
                    g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }
    }

    /** Replaces renderLabels. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, COL_TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COL_TEXT, false);
    }

    // ---- Drawing helpers ----

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_INNER);
    }

    /**
     * Blits the player-inventory slot region from vanilla's inventory.png
     * (3 rows × 9 cols + 4px gap + hotbar = 162×76 from texture coord 7,83).
     * Aligns to the menu's slot positions: first row at y=143, hotbar at y=201.
     * Using the vanilla texture also fixes the z-order — items now render above
     * the slot backgrounds, which they didn't with raw g.fill() rectangles
     * because the new extractor pipeline submits fills on a layer above items.
     */
    private void drawPlayerInvSlots(GuiGraphicsExtractor g, int sx, int sy) {
        g.blit(RenderPipelines.GUI_TEXTURED,
                AbstractContainerScreen.INVENTORY_LOCATION,
                sx + 7, sy + 142,   // dest top-left (1px above first slot at y=143)
                7f, 83f,             // src u, v in inventory.png
                162, 76,             // width × height
                256, 256);           // texture sheet size
    }

    // ---- Left panel: forge slot list ----

    private void renderForgeSlots(GuiGraphicsExtractor g, int sx, int sy, int mouseX, int mouseY) {
        int slotCount = menu.getForgeSlotCount();
        int cx = sx + PLC_X;
        int cy = sy + PLC_Y;
        int listTop = cy + LIST_HEADER_H;

        g.text(font, "Forge Items", cx, cy, COL_TEXT, false);

        for (int row = 0; row < SLOTS_VISIBLE; row++) {
            int slotIdx = scrollOffset + row;
            if (slotIdx >= slotCount) break;

            int rx = cx;
            int ry = listTop + row * ROW_H;

            // Row frame.
            g.fill(rx, ry, rx + ROW_W, ry + ROW_H - 1, COL_ROW_BRD);
            g.fill(rx + 1, ry + 1, rx + ROW_W - 1, ry + ROW_H - 2, COL_ROW_BG);

            // Hover highlight.
            if (isInRow(mouseX, mouseY, sx, sy, row)) {
                g.fill(rx + 1, ry + 1, rx + ROW_W - 1, ry + ROW_H - 2, COL_HOVER);
            }

            ItemStack stack = menu.getSlot(slotIdx).getItem();
            if (stack.isEmpty()) {
                g.text(font, "Empty", rx + 3, ry + 5, COL_GRAY, false);
            } else {
                g.item(stack, rx + 1, ry + 1);
                g.itemDecorations(font, stack, rx + 1, ry + 1);

                String name = stack.getHoverName().getString();
                int maxW = ROW_W - 22;
                while (name.length() > 1 && font.width(name) > maxW) {
                    name = name.substring(0, name.length() - 1);
                }
                g.text(font, name, rx + 20, ry + 5, COL_TEXT, false);
            }
        }

        // Scroll bar (shown when list overflows visible area).
        if (slotCount > SLOTS_VISIBLE) {
            int totalH = SLOTS_VISIBLE * ROW_H;
            int barH   = Math.max(6, totalH * SLOTS_VISIBLE / slotCount);
            int barY   = (scrollOffset == 0) ? 0
                       : (int)((float) scrollOffset / (slotCount - SLOTS_VISIBLE) * (totalH - barH));
            int bx = cx + ROW_W + 1;
            g.fill(bx, listTop, bx + 3, listTop + totalH, COL_BAR_BG);
            g.fill(bx, listTop + barY, bx + 3, listTop + barY + barH, COL_SCBAR);
        }
    }

    private boolean isInRow(int mouseX, int mouseY, int sx, int sy, int row) {
        int listTop = sy + PLC_Y + LIST_HEADER_H;
        int rx = sx + PLC_X;
        int ry = listTop + row * ROW_H;
        return mouseX >= rx && mouseX < rx + ROW_W
            && mouseY >= ry && mouseY < ry + ROW_H - 1;
    }

    // ---- Right panel: fluid list ----

    private void renderFluidList(GuiGraphicsExtractor g, int sx, int sy) {
        int cx       = sx + PRC_X;
        int cy       = sy + PRC_Y;
        int listTop  = cy + FLUID_HEADER_H;
        int maxBottom = sy + PR_Y + PR_H - 2;

        g.text(font, "Molten Metals", cx, cy, COL_TEXT, false);

        List<Material> materials = menu.getMaterials();
        int capacity = menu.getFluidCapacityMb();
        int totalMb  = menu.getTotalFluidMb();

        // Capacity readout on its own line directly below the header, right-aligned.
        if (capacity > 0) {
            String cap = totalMb + " / " + capacity + " mB";
            g.text(font, cap, sx + PR_X + PR_W - 2 - font.width(cap), cy + 10, COL_GRAY, false);
        }

        boolean anyFluid = false;
        int rowTop = listTop;

        for (int i = 0; i < materials.size(); i++) {
            int stored = menu.getStoredMbForMaterial(i);
            if (stored <= 0) continue;
            if (rowTop + FLUID_ROW_H > maxBottom) break;

            anyFluid = true;
            Material mat = materials.get(i);

            String path = mat.id().getPath();
            String name = path.isEmpty() ? "?" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
            String mbStr = stored + " mB";

            g.text(font, name, cx, rowTop, COL_TEXT, false);
            g.text(font, mbStr, cx + PRC_W - font.width(mbStr), rowTop, COL_GRAY, false);

            int barY = rowTop + 10;
            g.fill(cx, barY, cx + PRC_W, barY + 5, COL_BAR_BG);
            int fillW = capacity > 0 ? Math.max(1, (int)((float) stored / capacity * PRC_W)) : 0;
            g.fill(cx, barY, cx + fillW, barY + 5, mat.stats().moltenColor() | 0xFF000000);

            rowTop += FLUID_ROW_H;
        }

        if (!anyFluid) {
            g.text(font, "No molten metals", cx, listTop + 8, COL_GRAY, false);
        }
    }

    // ---- Status strip ----

    private void renderStatusStrip(GuiGraphicsExtractor g, int sx, int sy) {
        int stripY = sy + PL_Y + PL_H + 4; // 4px below panels

        float temp = menu.getTemperatureC();
        int tempColor = menu.isForgeValid() && temp > 100f ? COL_TEMP_HOT : COL_TEXT;
        g.text(font, String.format("%.0f°C", temp), sx + PL_X + 2, stripY, tempColor, false);

        String status = menu.isForgeValid() ? "valid" : "invalid";
        int statusColor = menu.isForgeValid() ? 0xFF00AA00 : 0xFFAA0000;
        g.text(font, status, sx + PL_X + 48, stripY, statusColor, false);

        // Fuel bar (right side of status strip).
        int fuelMb  = menu.getFuelMb();
        int fuelCap = menu.getFuelCapacityMb();
        if (fuelCap > 0) {
            int bw = 56, bh = 6;
            int bx = sx + PR_X + PR_W - bw - 2;
            int by = stripY + 1;
            g.fill(bx, by, bx + bw, by + bh, COL_BAR_BG);
            int fw = Math.max(0, (int)((float) fuelMb / fuelCap * bw));
            g.fill(bx, by, bx + fw, by + bh, COL_FUEL);
            String fuelStr = fuelMb + " mB";
            g.text(font, fuelStr, bx - font.width(fuelStr) - 3, stripY, COL_GRAY, false);
        }
    }

    // ---- Scroll ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int px = leftPos + PL_X, py = topPos + PL_Y;
        if (mouseX >= px && mouseX < px + PL_W && mouseY >= py && mouseY < py + PL_H) {
            int maxScroll = Math.max(0, menu.getForgeSlotCount() - SLOTS_VISIBLE);
            scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- Click handling for forge slots ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int listTopY  = topPos  + PLC_Y + LIST_HEADER_H;
        int listLeftX = leftPos + PLC_X;
        if (event.x() >= listLeftX && event.x() < listLeftX + ROW_W
                && event.y() >= listTopY && event.y() < listTopY + SLOTS_VISIBLE * ROW_H) {
            int row     = (int)((event.y() - listTopY) / ROW_H);
            int slotIdx = scrollOffset + row;
            if (slotIdx >= 0 && slotIdx < menu.getForgeSlotCount()) {
                ContainerInput type = event.hasShiftDown()
                        ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
                slotClicked(menu.getSlot(slotIdx), slotIdx, event.button(), type);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
