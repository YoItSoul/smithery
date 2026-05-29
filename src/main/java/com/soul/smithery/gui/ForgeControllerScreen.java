package com.soul.smithery.gui;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.melting.MeltingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Screen for the forge controller.
 *
 * <p>The left panel is a scrollable list of forge item slots (click to take, scroll to scroll).
 * The right panel is a stacked molten-fluid tank, with the currently-selected output fluid
 * highlighted at the bottom and click-to-select on each layer. A status strip beneath the
 * panels shows temperature, validity, and a fuel bar; a small toggle in the top-right pauses
 * or resumes the auto-alloy loop.
 *
 * <p>Rendering uses the {@code extractBackground}/{@code extractLabels} pipeline introduced in
 * MC 26.1.2. Forge item slots live off-screen on the menu side and are rendered here manually.
 */
public class ForgeControllerScreen extends AbstractContainerScreen<ForgeControllerMenu> {

    private static final int IMG_W = 248;
    private static final int IMG_H = 222;

    private static final int PL_X = 7;
    private static final int PL_Y = 18;
    private static final int PL_W = 116;
    private static final int PL_H = 105;

    private static final int PR_X = 127;
    private static final int PR_Y = 18;
    private static final int PR_W = 114;
    private static final int PR_H = 105;

    private static final int PLC_X = PL_X + 2;
    private static final int PLC_Y = PL_Y + 2;
    private static final int PLC_W = PL_W - 4;

    private static final int PRC_X = PR_X + 2;
    private static final int PRC_Y = PR_Y + 2;
    private static final int PRC_W = PR_W - 4;

    private static final int LIST_HEADER_H = 10;
    private static final int ROW_H         = 18;
    private static final int ROW_W         = PLC_W - 5;
    private static final int SLOTS_VISIBLE = (PL_H - 4 - LIST_HEADER_H) / ROW_H;

    private static final int FLUID_HEADER_H = 20;

    private static final int TANK_W       = 40;
    private static final int TANK_X       = PRC_X + (PRC_W - TANK_W) / 2;
    private static final int TANK_Y       = PRC_Y + FLUID_HEADER_H + 2;
    private static final int TANK_BOTTOM  = PR_Y + PR_H - 4;
    private static final int TANK_H       = TANK_BOTTOM - TANK_Y;
    private static final int COL_TANK_EMPTY = 0xFF1A1A1A;

    private static final net.minecraft.resources.Identifier MOLTEN_FLOW_TEXTURE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    com.soul.smithery.Smithery.MODID, "textures/gui/molten_flow.png");
    private static final net.minecraft.resources.Identifier WATER_FLOW_TEXTURE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    com.soul.smithery.Smithery.MODID, "textures/gui/water_flow.png");
    private static final int FLOW_FRAME_W      = 32;
    private static final int FLOW_FRAME_H      = 32;
    private static final int FLOW_FRAME_COUNT  = 16;
    private static final int FLOW_TEX_W        = 32;
    private static final int FLOW_TEX_H        = 512;
    private static final int FLOW_FRAMETIME_MS = 150;

    private static final int ALLOY_BTN_W = 14;
    private static final int ALLOY_BTN_H = 14;
    private static final int ALLOY_BTN_X = 222;
    private static final int ALLOY_BTN_Y = 4;
    private static final int COL_ALLOY_ON  = 0xFF2E8B57;
    private static final int COL_ALLOY_OFF = 0xFF8B2E2E;

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

    private int scrollOffset = 0;

    /**
     * Constructs the screen for the given menu.
     *
     * @param menu the synced container menu
     * @param playerInventory player inventory (used for title display)
     * @param title screen title component
     */
    public ForgeControllerScreen(ForgeControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMG_W, IMG_H);
        this.titleLabelX     = PL_X + 2;
        this.titleLabelY     = 6;
        this.inventoryLabelX = 44;
        this.inventoryLabelY = 134;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);

        int x = leftPos, y = topPos;

        g.fill(x, y, x + IMG_W, y + IMG_H, COL_BG);

        drawPanel(g, x + PL_X, y + PL_Y, PL_W, PL_H);
        drawPanel(g, x + PR_X, y + PR_Y, PR_W, PR_H);

        drawPlayerInvSlots(g, x, y);

        renderForgeSlots(g, x, y, mouseX, mouseY);
        renderFluidTank(g, x, y, mouseX, mouseY);
        renderStatusStrip(g, x, y);
        renderAlloyToggleButton(g, x, y, mouseX, mouseY);

        int listTopY  = y + PLC_Y + LIST_HEADER_H;
        int listLeftX = x + PLC_X;
        if (mouseX >= listLeftX && mouseX < listLeftX + ROW_W
                && mouseY >= listTopY && mouseY < listTopY + SLOTS_VISIBLE * ROW_H) {
            int row = (mouseY - listTopY) / ROW_H;
            int slotIdx = scrollOffset + row;
            if (slotIdx >= 0 && slotIdx < menu.getForgeSlotCount()) {
                ItemStack stack = menu.getSlot(slotIdx).getItem();
                if (!stack.isEmpty()) {
                    List<Component> lines = buildSlotTooltip(stack, slotIdx);
                    g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    private static final class MeltState {
        enum Status { NOT_MELTABLE, TOO_COOL, FORGE_FULL, MELTING }
        Status status;
        float  meltingTempC;
        int    progressMb;
        int    maxMb;
        int    barColor;
        Material material;
    }

    private MeltState computeMeltState(ItemStack stack, int slotIdx) {
        MeltState ms = new MeltState();
        ms.progressMb = menu.getMeltProgressMb(slotIdx);

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        MeltingRecipe recipe = SmitheryAPI.MELTING_RECIPES.get(itemId);
        if (recipe == null) {
            ms.status   = MeltState.Status.NOT_MELTABLE;
            ms.barColor = COL_GRAY;
            return ms;
        }

        Material mat = SmitheryAPI.MATERIALS.get(recipe.outputMaterialId());
        ms.material = mat;
        ms.maxMb    = recipe.outputMb();
        if (mat != null) {
            MaterialStats stats = mat.stats();
            ms.meltingTempC = stats.meltingTemp();
            ms.barColor     = stats.moltenColor() | 0xFF000000;
        } else {
            ms.barColor = COL_GRAY;
        }

        float temp = menu.getTemperatureC();
        if (temp < ms.meltingTempC) {
            ms.status   = MeltState.Status.TOO_COOL;
            ms.barColor = 0xFF6688AA;
            return ms;
        }
        if (menu.getFluidCapacityMb() > 0
                && menu.getTotalFluidMb() >= menu.getFluidCapacityMb()) {
            ms.status   = MeltState.Status.FORGE_FULL;
            ms.barColor = 0xFFAA4422;
            return ms;
        }
        ms.status = MeltState.Status.MELTING;
        return ms;
    }

    private List<Component> buildSlotTooltip(ItemStack stack, int slotIdx) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));

        MeltState ms = computeMeltState(stack, slotIdx);
        switch (ms.status) {
            case NOT_MELTABLE ->
                lines.add(Component.literal("Not meltable").withStyle(ChatFormatting.DARK_GRAY));
            case TOO_COOL ->
                lines.add(Component.literal(String.format(
                                "Too cool — needs %.0f°C (forge %.0f°C)",
                                ms.meltingTempC, menu.getTemperatureC()))
                        .withStyle(ChatFormatting.AQUA));
            case FORGE_FULL ->
                lines.add(Component.literal("Forge full — drain fluids to resume")
                        .withStyle(ChatFormatting.RED));
            case MELTING -> {
                int pct = ms.maxMb > 0 ? (int)((float) ms.progressMb / ms.maxMb * 100) : 0;
                lines.add(Component.literal(String.format(
                                "Melting: %d / %d mB (%d%%)",
                                ms.progressMb, ms.maxMb, pct))
                        .withStyle(ChatFormatting.GOLD));
                if (ms.material != null) {
                    String matName = ms.material.id().getPath();
                    if (!matName.isEmpty()) {
                        matName = Character.toUpperCase(matName.charAt(0)) + matName.substring(1);
                    }
                    lines.add(Component.literal("→ Molten " + matName)
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, COL_TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COL_TEXT, false);
    }

    private void renderAlloyToggleButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int bx = x + ALLOY_BTN_X;
        int by = y + ALLOY_BTN_Y;
        boolean enabled = menu.isAlloyEnabled();
        int fill = enabled ? COL_ALLOY_ON : COL_ALLOY_OFF;
        g.fill(bx, by, bx + ALLOY_BTN_W, by + ALLOY_BTN_H, COL_BORDER);
        g.fill(bx + 1, by + 1, bx + ALLOY_BTN_W - 1, by + ALLOY_BTN_H - 1, fill);
        String label = "A";
        int textW = font.width(label);
        g.text(font, net.minecraft.network.chat.Component.literal(label),
                bx + (ALLOY_BTN_W - textW) / 2,
                by + (ALLOY_BTN_H - 8) / 2,
                0xFFFFFFFF, false);
        if (mouseX >= bx && mouseX < bx + ALLOY_BTN_W && mouseY >= by && mouseY < by + ALLOY_BTN_H) {
            net.minecraft.network.chat.Component tip = enabled
                    ? net.minecraft.network.chat.Component.translatable(
                            "tooltip.smithery.forge.alloy_enabled")
                    : net.minecraft.network.chat.Component.translatable(
                            "tooltip.smithery.forge.alloy_disabled");
            g.setTooltipForNextFrame(font, java.util.List.of(tip), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_INNER);
    }

    private void drawPlayerInvSlots(GuiGraphicsExtractor g, int sx, int sy) {
        g.blit(RenderPipelines.GUI_TEXTURED,
                AbstractContainerScreen.INVENTORY_LOCATION,
                sx + 43, sy + 142,
                7f, 83f,
                162, 76,
                256, 256);
    }

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

            g.fill(rx, ry, rx + ROW_W, ry + ROW_H - 1, COL_ROW_BRD);
            g.fill(rx + 1, ry + 1, rx + ROW_W - 1, ry + ROW_H - 2, COL_ROW_BG);

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
                g.text(font, name, rx + 20, ry + 2, COL_TEXT, false);

                MeltState ms = computeMeltState(stack, slotIdx);
                int barX = rx + 20;
                int barY = ry + 12;
                int barW = ROW_W - 22;
                int barH = 4;
                g.fill(barX, barY, barX + barW, barY + barH, COL_BAR_BG);
                if (ms.maxMb > 0) {
                    int fillW = (int)((float) ms.progressMb / ms.maxMb * barW);
                    g.fill(barX, barY, barX + fillW, barY + barH, ms.barColor);
                }
            }
        }

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

    private void renderFluidTank(GuiGraphicsExtractor g, int sx, int sy, int mouseX, int mouseY) {
        int cx = sx + PRC_X;
        int cy = sy + PRC_Y;

        g.text(font, "Molten Metals", cx, cy, COL_TEXT, false);

        int capacity = menu.getFluidCapacityMb();
        int totalMb  = menu.getTotalFluidMb();
        if (capacity > 0) {
            String cap = totalMb + " / " + capacity + " mB";
            g.text(font, cap, sx + PR_X + PR_W - 2 - font.width(cap), cy + 10, COL_GRAY, false);
        }

        int tankX = sx + TANK_X;
        int tankY = sy + TANK_Y;
        g.fill(tankX, tankY, tankX + TANK_W, tankY + TANK_H, COL_BORDER);
        g.fill(tankX + 1, tankY + 1, tankX + TANK_W - 1, tankY + TANK_H - 1, COL_TANK_EMPTY);
        if (capacity <= 0) return;

        int innerX = tankX + 1;
        int innerY = tankY + 1;
        int innerW = TANK_W - 2;
        int innerH = TANK_H - 2;
        List<FluidLayer> layers = computeFluidLayers(innerY, innerH, capacity);
        int frame  = (int)((System.currentTimeMillis() / FLOW_FRAMETIME_MS) % FLOW_FRAME_COUNT);
        float baseV = (float)(frame * FLOW_FRAME_H);
        for (FluidLayer layer : layers) {
            int color = layer.material.stats().moltenColor() | 0xFF000000;
            int layerH = layer.bottomY - layer.topY;
            net.minecraft.resources.Identifier flowTex =
                    layer.material.stats().fluidBase()
                            == com.soul.smithery.api.material.MaterialStats.FluidBase.WATER
                    ? WATER_FLOW_TEXTURE : MOLTEN_FLOW_TEXTURE;
            drawTiledMolten(g, innerX, layer.topY, innerW, layerH, baseV, color, flowTex);
            g.fill(innerX, layer.topY, innerX + innerW, layer.topY + 1, brighten(color));
            if (layer.selected) {
                int outline = 0xFFFFD060;
                g.fill(innerX, layer.topY, innerX + innerW, layer.topY + 1, outline);
                g.fill(innerX, layer.bottomY - 1, innerX + innerW, layer.bottomY, outline);
                g.fill(innerX, layer.topY, innerX + 1, layer.bottomY, outline);
                g.fill(innerX + innerW - 1, layer.topY, innerX + innerW, layer.bottomY, outline);
            }
        }

        if (mouseX >= innerX && mouseX < innerX + innerW
                && mouseY >= innerY && mouseY < innerY + innerH) {
            for (FluidLayer layer : layers) {
                if (mouseY >= layer.topY && mouseY < layer.bottomY) {
                    String matName = layer.material.id().getPath();
                    if (!matName.isEmpty()) {
                        matName = Character.toUpperCase(matName.charAt(0)) + matName.substring(1);
                    }
                    int pct = capacity > 0 ? (int)((long) layer.storedMb * 100 / capacity) : 0;
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal("Molten " + matName).withStyle(ChatFormatting.WHITE));
                    lines.add(Component.literal(layer.storedMb + " mB (" + pct + "% of tank)")
                            .withStyle(ChatFormatting.GOLD));
                    lines.add(Component.literal(layer.selected
                                    ? "Active drain output — click to clear"
                                    : "Click to set as drain output")
                            .withStyle(layer.selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
                    g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    private record FluidLayer(Material material, int matIdx, int storedMb,
                              int topY, int bottomY, boolean selected) {}

    private List<FluidLayer> computeFluidLayers(int innerY, int innerH, int capacity) {
        List<FluidLayer> out = new ArrayList<>();
        if (capacity <= 0) return out;
        int bottomY = innerY + innerH;
        int cumPx = 0;
        List<Material> materials = menu.getMaterials();
        int selectedIdx = menu.getOutputFluidMaterialIndex();

        int[] order = buildLayerOrder(materials.size(), selectedIdx);
        for (int idx : order) {
            int stored = menu.getStoredMbForMaterial(idx);
            if (stored <= 0) continue;
            int layerPx = Math.max(1, (int)((long) stored * innerH / capacity));
            if (cumPx + layerPx > innerH) layerPx = innerH - cumPx;
            if (layerPx <= 0) break;
            int layerBottom = bottomY - cumPx;
            int layerTop    = layerBottom - layerPx;
            out.add(new FluidLayer(materials.get(idx), idx, stored, layerTop, layerBottom,
                    idx == selectedIdx));
            cumPx += layerPx;
        }
        return out;
    }

    private static int[] buildLayerOrder(int total, int selectedIdx) {
        int[] order = new int[total];
        int w = 0;
        if (selectedIdx >= 0 && selectedIdx < total) {
            order[w++] = selectedIdx;
        }
        for (int i = 0; i < total; i++) {
            if (i == selectedIdx) continue;
            order[w++] = i;
        }
        return order;
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >>> 16) & 0xFF) + 30);
        int gn = Math.min(255, ((argb >>> 8)  & 0xFF) + 30);
        int b = Math.min(255, ( argb          & 0xFF) + 30);
        return 0xFF000000 | (r << 16) | (gn << 8) | b;
    }

    private static void drawTiledMolten(GuiGraphicsExtractor g, int destX, int destY,
                                        int w, int h, float baseV, int tintArgb,
                                        net.minecraft.resources.Identifier flowTexture) {
        int yRemaining = h;
        int dy = destY;
        while (yRemaining > 0) {
            int rowH = Math.min(FLOW_FRAME_H, yRemaining);
            int xRemaining = w;
            int dx = destX;
            while (xRemaining > 0) {
                int colW = Math.min(FLOW_FRAME_W, xRemaining);
                g.blit(RenderPipelines.GUI_TEXTURED,
                        flowTexture,
                        dx, dy,
                        0f, baseV,
                        colW, rowH,
                        colW, rowH,
                        FLOW_TEX_W, FLOW_TEX_H,
                        tintArgb);
                dx += colW;
                xRemaining -= colW;
            }
            dy += rowH;
            yRemaining -= rowH;
        }
    }

    private void renderStatusStrip(GuiGraphicsExtractor g, int sx, int sy) {
        int stripY = sy + PL_Y + PL_H + 4;

        float temp = menu.getTemperatureC();
        int tempColor = menu.isForgeValid() && temp > 100f ? COL_TEMP_HOT : COL_TEXT;
        g.text(font, String.format("%.0f°C", temp), sx + PL_X + 2, stripY, tempColor, false);

        String status = menu.isForgeValid() ? "valid" : "invalid";
        int statusColor = menu.isForgeValid() ? 0xFF00AA00 : 0xFFAA0000;
        g.text(font, status, sx + PL_X + 48, stripY, statusColor, false);

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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btnX = leftPos + ALLOY_BTN_X;
        int btnY = topPos  + ALLOY_BTN_Y;
        if (event.x() >= btnX && event.x() < btnX + ALLOY_BTN_W
                && event.y() >= btnY && event.y() < btnY + ALLOY_BTN_H) {
            net.minecraft.client.Minecraft.getInstance().gameMode.handleInventoryButtonClick(
                    menu.containerId, ForgeControllerMenu.BUTTON_TOGGLE_ALLOY);
            return true;
        }

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

        int tankX = leftPos + TANK_X;
        int tankY = topPos  + TANK_Y;
        int innerX = tankX + 1;
        int innerY = tankY + 1;
        int innerW = TANK_W - 2;
        int innerH = TANK_H - 2;
        int capacity = menu.getFluidCapacityMb();
        if (capacity > 0 && event.x() >= innerX && event.x() < innerX + innerW
                && event.y() >= innerY && event.y() < innerY + innerH) {
            List<FluidLayer> layers = computeFluidLayers(innerY, innerH, capacity);
            for (FluidLayer layer : layers) {
                if (event.y() >= layer.topY && event.y() < layer.bottomY) {
                    sendOutputFluidSelection(layer);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void sendOutputFluidSelection(FluidLayer layer) {
        net.minecraft.resources.Identifier matId = layer.material.id();
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forMaterial(matId);
        if (entry == null) return;
        net.minecraft.resources.Identifier fluidId =
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
        if (fluidId == null) return;
        net.minecraft.client.multiplayer.ClientPacketListener conn =
                net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new com.soul.smithery.network.ForgeSelectOutputFluidPayload(menu.getBlockPos(), fluidId)));
        }
    }
}
