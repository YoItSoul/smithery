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

    // Right-panel header takes two stacked lines: "Molten Metals" + capacity readout.
    private static final int FLUID_HEADER_H = 20;

    // Tinkers-style vertical fluid tank: each registered molten material is a
    // colored band stacked bottom-up, height proportional to its mB / total capacity.
    // Centered in the right panel, with a 1px dark border. The "ceiling" inside
    // the border (above the topmost layer) stays dark to read as empty headroom.
    private static final int TANK_W       = 40;
    private static final int TANK_X       = PRC_X + (PRC_W - TANK_W) / 2; // centered in inner area
    private static final int TANK_Y       = PRC_Y + FLUID_HEADER_H + 2;
    private static final int TANK_BOTTOM  = PR_Y + PR_H - 4;
    private static final int TANK_H       = TANK_BOTTOM - TANK_Y;
    private static final int COL_TANK_EMPTY = 0xFF1A1A1A;

    // Animated lava-flow sprite used as the layer fill texture, tinted per-material.
    // The PNG is the same one used for the actual world fluid; we drive the animation
    // manually here because raw blit() doesn't honor .mcmeta — it sees only the sheet.
    private static final net.minecraft.resources.Identifier MOLTEN_FLOW_TEXTURE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    com.soul.smithery.Smithery.MODID, "textures/block/molten_flow.png");
    private static final int FLOW_FRAME_W      = 32;
    private static final int FLOW_FRAME_H      = 32;
    private static final int FLOW_FRAME_COUNT  = 16;       // sheet is 32 × (32 × 16) = 32×512
    private static final int FLOW_TEX_W        = 32;
    private static final int FLOW_TEX_H        = 512;
    private static final int FLOW_FRAMETIME_MS = 150;      // mcmeta frametime = 3 ticks → 150 ms

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
        this.inventoryLabelX = 44;  // align with leftmost inventory slot interior (centered inv)
        this.inventoryLabelY = 134; // 9px above player inventory at y=143
    }

    // ---- Render pipeline (extract* pattern) ----
    //
    // Critical: in MC 26.1.2, AbstractContainerScreen.extractRenderState calls
    // extractContents → which in turn calls super.extractRenderState (drawing
    // the screen background via extractBackground), translates the matrix by
    // (leftPos, topPos), then calls extractLabels and extractSlots.
    //
    // So:
    //   - Override extractBackground (drawn in ABSOLUTE screen coords) for our
    //     custom panels/textures/etc. This is what ContainerScreen (vanilla
    //     chest GUI) does. Overriding extractContents instead suppresses the
    //     extractSlots call, which is why item icons stopped rendering.
    //   - Override extractLabels (drawn in coords RELATIVE to leftPos/topPos
    //     since the matrix is already translated) for our title and
    //     "Inventory" label.

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick); // dim overlay / blur

        int x = leftPos, y = topPos;

        // Main background fill.
        g.fill(x, y, x + IMG_W, y + IMG_H, COL_BG);

        // Top panels.
        drawPanel(g, x + PL_X, y + PL_Y, PL_W, PL_H);
        drawPanel(g, x + PR_X, y + PR_Y, PR_W, PR_H);

        // Player inventory slot region from vanilla inventory.png.
        drawPlayerInvSlots(g, x, y);

        // Panel contents (forge slot list, fluid list, status strip).
        renderForgeSlots(g, x, y, mouseX, mouseY);
        renderFluidTank(g, x, y, mouseX, mouseY);
        renderStatusStrip(g, x, y);

        // Forge slot tooltip — vanilla's extractTooltip doesn't fire for our
        // off-screen forge slots, so we set the tooltip ourselves. Includes
        // detailed melt status: not-meltable / too-cool / melting / forge-full.
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

    // ---- Melt state computation (client-side, mirrors the server logic) ----

    /** Per-slot melt state used by both the in-row progress bar and the tooltip. */
    private static final class MeltState {
        enum Status { NOT_MELTABLE, TOO_COOL, FORGE_FULL, MELTING }
        Status status;
        float  meltingTempC;
        int    progressMb;
        int    maxMb;
        int    barColor;
        Material material; // nullable
    }

    /**
     * Mirrors {@link com.soul.smithery.block.entity.ForgeControllerBlockEntity#meltFromSlots}
     * gating logic on the client so the GUI can label why a slot is or isn't melting.
     */
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
        // Matrix is translated by (leftPos, topPos) before this is called.
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
     * Aligns to the menu's slot positions: first row at y=143, hotbar at y=201,
     * left edge at x=43 (centered in the 248px screen).
     */
    private void drawPlayerInvSlots(GuiGraphicsExtractor g, int sx, int sy) {
        g.blit(RenderPipelines.GUI_TEXTURED,
                AbstractContainerScreen.INVENTORY_LOCATION,
                sx + 43, sy + 142,   // dest top-left (1px above first slot at y=143)
                7f, 83f,              // src u, v in inventory.png
                162, 76,              // width × height
                256, 256);            // texture sheet size
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
                g.text(font, name, rx + 20, ry + 2, COL_TEXT, false);

                // Per-slot melt progress bar in the bottom half of the row.
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

    // ---- Right panel: stacked-fluid tank (Tinkers-style) ----

    private void renderFluidTank(GuiGraphicsExtractor g, int sx, int sy, int mouseX, int mouseY) {
        int cx = sx + PRC_X;
        int cy = sy + PRC_Y;

        // Header.
        g.text(font, "Molten Metals", cx, cy, COL_TEXT, false);

        int capacity = menu.getFluidCapacityMb();
        int totalMb  = menu.getTotalFluidMb();
        if (capacity > 0) {
            String cap = totalMb + " / " + capacity + " mB";
            g.text(font, cap, sx + PR_X + PR_W - 2 - font.width(cap), cy + 10, COL_GRAY, false);
        }

        // Tank frame (1px border + dark empty interior).
        int tankX = sx + TANK_X;
        int tankY = sy + TANK_Y;
        g.fill(tankX, tankY, tankX + TANK_W, tankY + TANK_H, COL_BORDER);
        g.fill(tankX + 1, tankY + 1, tankX + TANK_W - 1, tankY + TANK_H - 1, COL_TANK_EMPTY);
        if (capacity <= 0) return;

        // Stacked fluid layers — each filled with the animated molten_flow sprite,
        // tinted by the material color. Approach mirrors Tinkers' GuiUtil.drawGuiTank:
        // pick a single time-driven frame, then tile that frame at its native 32×32
        // size across the layer, cropping the partial tiles at the right/top edges.
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
            drawTiledMolten(g, innerX, layer.topY, innerW, layerH, baseV, color);
            // 1px highlight along the top of each layer so adjacent layers don't blur together.
            g.fill(innerX, layer.topY, innerX + innerW, layer.topY + 1, brighten(color));
            // Selected layer: 1px outline + a subtle inner overlay so it reads as "active".
            if (layer.selected) {
                int outline = 0xFFFFD060;
                g.fill(innerX, layer.topY, innerX + innerW, layer.topY + 1, outline);
                g.fill(innerX, layer.bottomY - 1, innerX + innerW, layer.bottomY, outline);
                g.fill(innerX, layer.topY, innerX + 1, layer.bottomY, outline);
                g.fill(innerX + innerW - 1, layer.topY, innerX + innerW, layer.bottomY, outline);
            }
        }

        // Hover tooltip: which layer is the mouse over?
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

    /**
     * Pixel-resolved layer used by both the tank renderer and its hover tooltip.
     * Layers stack from {@code TANK_BOTTOM} upwards. The currently-selected output
     * fluid (if any) is iterated first so it lands at the bottom of the stack —
     * that's the visual cue "this is what the drains are pumping". {@code matIdx}
     * tracks the source material index so click handling can identify which layer
     * the player hit and ship the right id to the server.
     */
    private record FluidLayer(Material material, int matIdx, int storedMb,
                              int topY, int bottomY, boolean selected) {}

    private List<FluidLayer> computeFluidLayers(int innerY, int innerH, int capacity) {
        List<FluidLayer> out = new ArrayList<>();
        if (capacity <= 0) return out;
        int bottomY = innerY + innerH;
        int cumPx = 0;
        List<Material> materials = menu.getMaterials();
        int selectedIdx = menu.getOutputFluidMaterialIndex();

        // Build iteration order: selected material first (renders at bottom), then the rest
        // in registration order. Layers are stacked from the bottom up, so the first
        // iterated entry occupies the lowest band of pixels.
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

    /** Top-edge highlight: brighten an ARGB color by ~30 per channel, clamped. */
    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >>> 16) & 0xFF) + 30);
        int gn = Math.min(255, ((argb >>> 8)  & 0xFF) + 30);
        int b = Math.min(255, ( argb          & 0xFF) + 30);
        return 0xFF000000 | (r << 16) | (gn << 8) | b;
    }

    /**
     * Tile the molten_flow sprite at native 32×32 size across (destX..destX+w, destY..destY+h),
     * cropping the partial tile at the right/bottom edges when the area isn't a clean
     * multiple of the frame size. {@code baseV} pins us to a single animation frame (caller
     * computes that once per draw call so all tiles in the tank stay in sync).
     */
    private static void drawTiledMolten(GuiGraphicsExtractor g, int destX, int destY,
                                        int w, int h, float baseV, int tintArgb) {
        int yRemaining = h;
        int dy = destY;
        while (yRemaining > 0) {
            int rowH = Math.min(FLOW_FRAME_H, yRemaining);
            int xRemaining = w;
            int dx = destX;
            while (xRemaining > 0) {
                int colW = Math.min(FLOW_FRAME_W, xRemaining);
                // 13-arg blit: (pipe, id, x, y, u, v, destW, destH, srcW, srcH, texW, texH, tint).
                // Passing destW=srcW and destH=srcH means no stretching — just cropping
                // at the partial edges, matching Tinkers' putTiledTextureQuads behavior.
                g.blit(RenderPipelines.GUI_TEXTURED,
                        MOLTEN_FLOW_TEXTURE,
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

        // Fluid tank click — select / deselect output fluid.
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

    /**
     * Maps the clicked material → fluid id and sends the C2S packet. Server interprets
     * "click the already-selected layer" as "clear selection" — no special sentinel needed.
     */
    private void sendOutputFluidSelection(FluidLayer layer) {
        net.minecraft.resources.Identifier matId = layer.material.id();
        com.soul.smithery.registry.SmitheryFluids.Entry entry =
                com.soul.smithery.registry.SmitheryFluids.forMaterial(matId);
        if (entry == null) return;
        net.minecraft.resources.Identifier fluidId =
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.source.get());
        if (fluidId == null) return;
        // NeoForge 26.1.x removed PacketDistributor.sendToServer; route the C2S payload through
        // the client's network handler directly.
        net.minecraft.client.multiplayer.ClientPacketListener conn =
                net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new com.soul.smithery.network.ForgeSelectOutputFluidPayload(menu.getBlockPos(), fluidId)));
        }
    }
}
