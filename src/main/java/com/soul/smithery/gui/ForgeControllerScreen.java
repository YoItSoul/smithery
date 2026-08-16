package com.soul.smithery.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.forge.ForgeFuels;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.network.ForgeSelectOutputFluidPayload;
import com.soul.smithery.network.SmitheryPayloads;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Screen for the forge controller.
 *
 * <p>The left panel is a scrollable list of forge item slots (click to take, scroll to scroll).
 * The right panel is a stacked molten-fluid tank, with the currently-selected output fluid
 * highlighted at the bottom and click-to-select on each layer, and a narrow fuel column beside it
 * sharing the tank's recess so the forge's two liquids read as a pair. Both name themselves on
 * hover rather than on their face.
 *
 * <p>The title bar carries what used to sit in a status strip below the panels: the temperature,
 * right-aligned so a five-digit reading grows away from the title, and a validity lamp that reports
 * the structural fault on hover. Dropping the strip gave its height back to the panels, which is
 * where it was worth spending. A small toggle in the top-right pauses or resumes the auto-alloy loop.
 *
 * <p>Forge item slots live off-screen on the menu side and are rendered here manually.
 */
public class ForgeControllerScreen extends AbstractContainerScreen<ForgeControllerMenu> {

    private static final int IMG_W = 248;
    private static final int IMG_H = 232;

    private static final int PL_X = 7;
    private static final int PL_Y = 18;
    private static final int PL_W = 116;
    private static final int PL_H = 122;

    private static final int PR_X = 127;
    private static final int PR_Y = 18;
    private static final int PR_W = 114;
    private static final int PR_H = 122;

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
    /** Fuel column width; narrow because its detail lives on hover, not on the face of it. */
    private static final int FUEL_COL_W   = 10;
    private static final int FUEL_GAP     = 8;
    /**
     * The fuel column and the molten tank are centred together rather than separately, so the pair
     * reads as one instrument: the two liquids this forge holds, side by side.
     */
    private static final int TANK_GROUP_W = FUEL_COL_W + FUEL_GAP + TANK_W;
    private static final int TANK_GROUP_X = PRC_X + (PRC_W - TANK_GROUP_W) / 2;
    private static final int FUEL_COL_X   = TANK_GROUP_X;
    private static final int TANK_X       = TANK_GROUP_X + FUEL_COL_W + FUEL_GAP;
    private static final int TANK_Y       = PRC_Y + FLUID_HEADER_H + 2;
    private static final int TANK_BOTTOM  = PR_Y + PR_H - 4;
    private static final int TANK_H       = TANK_BOTTOM - TANK_Y;
    private static final int COL_TANK_EMPTY = 0xFF1A1A1A;

    /** Validity lamp, centred over the two panels in the title bar. */
    private static final int LAMP_R  = 5;
    private static final int LAMP_CX = (PL_X + PR_X + PR_W) / 2;
    private static final int LAMP_CY = 11;
    private static final int COL_LAMP_ON       = 0xFF39D74E;
    private static final int COL_LAMP_OFF      = 0xFFD93A32;
    private static final int COL_LAMP_WARN     = 0xFFE0B32E;
    private static final int COL_LAMP_ON_DIM   = 0xFF1F7A2A;
    private static final int COL_LAMP_OFF_DIM  = 0xFF7A1F1A;
    private static final int COL_LAMP_WARN_DIM = 0xFF7A6018;
    private static final int COL_LAMP_BEZEL    = 0xFF4A4A4A;
    private static final int COL_LAMP_GLINT    = 0xAAFFFFFF;

    private static final ResourceLocation MOLTEN_FLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "textures/gui/molten_flow.png");
    private static final ResourceLocation WATER_FLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "textures/gui/water_flow.png");
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
        super(menu, playerInventory, title);
        this.imageWidth      = IMG_W;
        this.imageHeight     = IMG_H;
        this.titleLabelX     = PL_X + 2;
        this.titleLabelY     = 6;
        this.inventoryLabelX = 44;
        this.inventoryLabelY = 144;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderCustomTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        g.fill(x, y, x + IMG_W, y + IMG_H, COL_BG);

        drawPanel(g, x + PL_X, y + PL_Y, PL_W, PL_H);
        drawPanel(g, x + PR_X, y + PR_Y, PR_W, PR_H);

        drawPlayerInvSlots(g, x, y);

        renderForgeSlots(g, x, y, mouseX, mouseY);
        renderFuelColumn(g, x, y);
        renderFluidTank(g, x, y);
        renderTitleBar(g, x, y);
        renderAlloyToggleButton(g, x, y);
    }

    /** Draws the hover tooltips for the manually-rendered widgets, above every other layer. */
    private void renderCustomTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        int listTopY  = y + PLC_Y + LIST_HEADER_H;
        int listLeftX = x + PLC_X;
        if (mouseX >= listLeftX && mouseX < listLeftX + ROW_W
                && mouseY >= listTopY && mouseY < listTopY + SLOTS_VISIBLE * ROW_H) {
            int row = (mouseY - listTopY) / ROW_H;
            int slotIdx = scrollOffset + row;
            if (slotIdx >= 0 && slotIdx < menu.getForgeSlotCount()) {
                ItemStack stack = menu.getSlot(slotIdx).getItem();
                if (!stack.isEmpty()) {
                    g.renderTooltip(font, buildSlotTooltip(stack, slotIdx), Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
        }

        int bx = x + ALLOY_BTN_X;
        int by = y + ALLOY_BTN_Y;
        if (mouseX >= bx && mouseX < bx + ALLOY_BTN_W && mouseY >= by && mouseY < by + ALLOY_BTN_H) {
            Component tip = menu.isAlloyEnabled()
                    ? Component.translatable("tooltip.smithery.forge.alloy_enabled")
                    : Component.translatable("tooltip.smithery.forge.alloy_disabled");
            g.renderTooltip(font, List.of(tip), Optional.empty(), mouseX, mouseY);
            return;
        }

        renderFluidTankTooltip(g, mouseX, mouseY);
        renderFuelTooltip(g, mouseX, mouseY);
        renderLampTooltip(g, mouseX, mouseY);
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

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, COL_TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COL_TEXT, false);
    }

    private void renderAlloyToggleButton(GuiGraphics g, int x, int y) {
        int bx = x + ALLOY_BTN_X;
        int by = y + ALLOY_BTN_Y;
        boolean enabled = menu.isAlloyEnabled();
        int fill = enabled ? COL_ALLOY_ON : COL_ALLOY_OFF;
        g.fill(bx, by, bx + ALLOY_BTN_W, by + ALLOY_BTN_H, COL_BORDER);
        g.fill(bx + 1, by + 1, bx + ALLOY_BTN_W - 1, by + ALLOY_BTN_H - 1, fill);
        String label = "A";
        int textW = font.width(label);
        g.drawString(font, label,
                bx + (ALLOY_BTN_W - textW) / 2,
                by + (ALLOY_BTN_H - 8) / 2,
                0xFFFFFF, false);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_INNER);
    }

    private void drawPlayerInvSlots(GuiGraphics g, int sx, int sy) {
        g.blit(INVENTORY_LOCATION, sx + 43, sy + 152, 7, 83, 162, 76);
    }

    private void renderForgeSlots(GuiGraphics g, int sx, int sy, int mouseX, int mouseY) {
        int slotCount = menu.getForgeSlotCount();
        int cx = sx + PLC_X;
        int cy = sy + PLC_Y;
        int listTop = cy + LIST_HEADER_H;

        g.drawString(font, "Forge Items", cx, cy, COL_TEXT, false);

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
                g.drawString(font, "Empty", rx + 3, ry + 5, COL_GRAY, false);
            } else {
                g.renderItem(stack, rx + 1, ry + 1);
                g.renderItemDecorations(font, stack, rx + 1, ry + 1);

                String name = stack.getHoverName().getString();
                int maxW = ROW_W - 22;
                while (name.length() > 1 && font.width(name) > maxW) {
                    name = name.substring(0, name.length() - 1);
                }
                g.drawString(font, name, rx + 20, ry + 2, COL_TEXT, false);

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

    private void renderFluidTank(GuiGraphics g, int sx, int sy) {
        int cx = sx + PRC_X;
        int cy = sy + PRC_Y;

        g.drawString(font, "Molten Metals", cx, cy, COL_TEXT, false);

        int capacity = menu.getFluidCapacityMb();
        int totalMb  = menu.getTotalFluidMb();
        if (capacity > 0) {
            String cap = totalMb + " / " + capacity + " mB";
            g.drawString(font, cap, sx + PR_X + PR_W - 2 - font.width(cap), cy + 10, COL_GRAY, false);
        }

        int tankX = sx + TANK_X;
        int tankY = sy + TANK_Y;
        g.fill(tankX, tankY, tankX + TANK_W, tankY + TANK_H, COL_BORDER);
        g.fill(tankX + 1, tankY + 1, tankX + TANK_W - 1, tankY + TANK_H - 1, COL_TANK_EMPTY);
        if (capacity <= 0) return;

        int innerX = tankX + 1;
        int innerW = TANK_W - 2;
        int innerH = TANK_H - 2;
        List<FluidLayer> layers = computeFluidLayers(tankY + 1, innerH, capacity);
        int frame  = (int)((System.currentTimeMillis() / FLOW_FRAMETIME_MS) % FLOW_FRAME_COUNT);
        int baseV  = frame * FLOW_FRAME_H;
        for (FluidLayer layer : layers) {
            int color = layer.material.stats().moltenColor() | 0xFF000000;
            int layerH = layer.bottomY - layer.topY;
            ResourceLocation flowTex =
                    layer.material.stats().fluidBase() == MaterialStats.FluidBase.WATER
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
    }

    private void renderFluidTankTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int capacity = menu.getFluidCapacityMb();
        if (capacity <= 0) return;
        int innerX = leftPos + TANK_X + 1;
        int innerY = topPos + TANK_Y + 1;
        int innerW = TANK_W - 2;
        int innerH = TANK_H - 2;
        if (mouseX < innerX || mouseX >= innerX + innerW
                || mouseY < innerY || mouseY >= innerY + innerH) {
            return;
        }
        for (FluidLayer layer : computeFluidLayers(innerY, innerH, capacity)) {
            if (mouseY >= layer.topY && mouseY < layer.bottomY) {
                String matName = layer.material.id().getPath();
                if (!matName.isEmpty()) {
                    matName = Character.toUpperCase(matName.charAt(0)) + matName.substring(1);
                }
                int pct = (int)((long) layer.storedMb * 100 / capacity);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal("Molten " + matName).withStyle(ChatFormatting.WHITE));
                lines.add(Component.literal(layer.storedMb + " mB (" + pct + "% of tank)")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal(layer.selected
                                ? "Active drain output — click to clear"
                                : "Click to set as drain output")
                        .withStyle(layer.selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
                g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
                return;
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

    /**
     * Tiles the animated flow texture across the layer rectangle, tinted with the material's
     * molten color via the shader color (1.20.1 blits have no per-call tint argument).
     */
    private static void drawTiledMolten(GuiGraphics g, int destX, int destY,
                                        int w, int h, int baseV, int tintArgb,
                                        ResourceLocation flowTexture) {
        RenderSystem.setShaderColor(
                ((tintArgb >>> 16) & 0xFF) / 255f,
                ((tintArgb >>> 8)  & 0xFF) / 255f,
                ( tintArgb         & 0xFF) / 255f,
                1.0f);
        int yRemaining = h;
        int dy = destY;
        while (yRemaining > 0) {
            int rowH = Math.min(FLOW_FRAME_H, yRemaining);
            int xRemaining = w;
            int dx = destX;
            while (xRemaining > 0) {
                int colW = Math.min(FLOW_FRAME_W, xRemaining);
                g.blit(flowTexture, dx, dy, 0, baseV, colW, rowH, FLOW_TEX_W, FLOW_TEX_H);
                dx += colW;
                xRemaining -= colW;
            }
            dy += rowH;
            yRemaining -= rowH;
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Title-bar readouts: the temperature, right-aligned so a five-digit reading grows away from
     * the title instead of into it, and the validity lamp centred over the two panels.
     */
    private void renderTitleBar(GuiGraphics g, int sx, int sy) {
        float temp = menu.getTemperatureC();
        Component tempText = Component.translatable(
                "gui." + Smithery.MODID + ".forge.temperature", Math.round(temp));
        int tempColor = menu.isForgeValid() && temp > 100f ? COL_TEMP_HOT : COL_TEXT;
        g.drawString(font, tempText,
                sx + ALLOY_BTN_X - 6 - font.width(tempText), sy + 6, tempColor, false);

        // Red / amber / green, matching the controller block's own three states: no
        // structure, structure but no fuel, running. Amber is the one that tells a
        // player their build was right and only the fuel is missing.
        boolean ok = menu.isForgeValid();
        boolean fuelled = menu.getFuelMb() > 0;
        int lamp = !ok ? COL_LAMP_OFF : fuelled ? COL_LAMP_ON : COL_LAMP_WARN;
        int lampDim = !ok ? COL_LAMP_OFF_DIM : fuelled ? COL_LAMP_ON_DIM : COL_LAMP_WARN_DIM;
        int cx = sx + LAMP_CX, cy = sy + LAMP_CY;
        g.fill(cx - LAMP_R, cy - LAMP_R, cx + LAMP_R, cy + LAMP_R, COL_LAMP_BEZEL);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, lampDim);
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, lamp);
        // One lit pixel in the corner is what separates a lamp from a coloured square.
        g.fill(cx - 3, cy - 3, cx - 1, cy - 1, COL_LAMP_GLINT);
    }

    /**
     * Fuel level as a column beside the molten tank, sharing the tank's recessed treatment so the
     * two liquids read as a pair. Everything else about the fuel — what it is, how much, how hot it
     * burns — is on hover, the same bargain the tank already makes.
     */
    private void renderFuelColumn(GuiGraphics g, int sx, int sy) {
        int x = sx + FUEL_COL_X, y = sy + TANK_Y;
        g.fill(x, y, x + FUEL_COL_W, y + TANK_H, COL_BORDER);
        g.fill(x + 1, y + 1, x + FUEL_COL_W - 1, y + TANK_H - 1, COL_TANK_EMPTY);

        int cap = menu.getFuelCapacityMb();
        if (cap <= 0) return;
        int inner = TANK_H - 2;
        int filled = Math.max(0, Math.min(inner, (int) ((long) menu.getFuelMb() * inner / cap)));
        if (filled <= 0) return;
        int top = y + 1 + inner - filled;
        int color = fuelColor();
        g.fill(x + 1, top, x + FUEL_COL_W - 1, y + TANK_H - 1, color);
        g.fill(x + 1, top, x + FUEL_COL_W - 1, top + 1, brighten(color));
    }

    /**
     * Colour for the burning fuel: its own molten colour when Smithery owns the fluid, otherwise
     * the generic fuel orange, which is where vanilla lava lands.
     */
    private int fuelColor() {
        Fluid fuel = menu.getFuelFluid();
        if (fuel != null) {
            SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fuel);
            if (entry != null) return entry.material.stats().moltenColor() | 0xFF000000;
        }
        return COL_FUEL;
    }

    /**
     * Display name for a fuel fluid.
     *
     * <p>Smithery's own molten fluids name themselves through their FluidType. Everything else is
     * named from its block instead, because Forge ships a translation for exactly one vanilla fluid
     * type ({@code milk}) — asking lava for its FluidType description yields the raw key.
     */
    private static Component fuelName(Fluid fuel) {
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fuel);
        if (entry != null) return SmitheryFluids.moltenName(entry.materialId);
        Block block = fuel.defaultFluidState().createLegacyBlock().getBlock();
        if (block != Blocks.AIR) return block.getName();
        return fuel.getFluidType().getDescription();
    }

    /** Hover text for the fuel column: what is burning, how much of it, and how hot it gets. */
    private void renderFuelTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + FUEL_COL_X, y = topPos + TANK_Y;
        if (mouseX < x || mouseX >= x + FUEL_COL_W || mouseY < y || mouseY >= y + TANK_H) return;

        String base = "gui." + Smithery.MODID + ".forge.";
        List<Component> lines = new ArrayList<>();
        Fluid fuel = menu.getFuelFluid();
        int cap = menu.getFuelCapacityMb();
        if (fuel == null || menu.getFuelMb() <= 0) {
            lines.add(Component.translatable(base + "fuel_empty").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(fuelName(fuel).copy().withStyle(ChatFormatting.WHITE));
            int pct = cap > 0 ? (int) ((long) menu.getFuelMb() * 100 / cap) : 0;
            lines.add(Component.translatable(base + "fuel_amount", menu.getFuelMb(), cap, pct)
                    .withStyle(ChatFormatting.GOLD));
            ForgeFuels.Profile profile = ForgeFuels.get(fuel);
            if (profile != null) {
                lines.add(Component.translatable(base + "fuel_burns_at",
                                Math.round(profile.targetTemperatureC()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /**
     * Hover text for the validity lamp. On a whole forge it reports the chamber it built; on a
     * broken one it names the fault and what to do about it — which until now existed only as a
     * server-side string no player could ever see.
     */
    private void renderLampTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int cx = leftPos + LAMP_CX, cy = topPos + LAMP_CY;
        if (mouseX < cx - LAMP_R || mouseX >= cx + LAMP_R
                || mouseY < cy - LAMP_R || mouseY >= cy + LAMP_R) {
            return;
        }
        String base = "gui." + Smithery.MODID + ".forge.";
        List<Component> lines = new ArrayList<>();
        if (menu.isForgeValid()) {
            boolean fuelled = menu.getFuelMb() > 0;
            // An amber lamp has to say why it is amber, or it just reads as a fault.
            lines.add(Component.translatable(base + (fuelled ? "ready" : "unfuelled"))
                    .withStyle(fuelled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            lines.add(Component.translatable(base + "ready_detail", menu.getFluidCapacityMb())
                    .withStyle(ChatFormatting.GRAY));
            if (!fuelled) {
                lines.add(Component.translatable(base + "unfuelled.hint")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            lines.add(Component.translatable(base + "incomplete").withStyle(ChatFormatting.RED));
            String key = base + "invalid." + menu.getInvalidReason().name().toLowerCase(Locale.ROOT);
            lines.add(Component.translatable(key, menu.getReasonDetail())
                    .withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY));
        }
        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int px = leftPos + PL_X, py = topPos + PL_Y;
        if (mouseX >= px && mouseX < px + PL_W && mouseY >= py && mouseY < py + PL_H) {
            int maxScroll = Math.max(0, menu.getForgeSlotCount() - SLOTS_VISIBLE);
            scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int btnX = leftPos + ALLOY_BTN_X;
        int btnY = topPos  + ALLOY_BTN_Y;
        if (mouseX >= btnX && mouseX < btnX + ALLOY_BTN_W
                && mouseY >= btnY && mouseY < btnY + ALLOY_BTN_H) {
            Minecraft.getInstance().gameMode.handleInventoryButtonClick(
                    menu.containerId, ForgeControllerMenu.BUTTON_TOGGLE_ALLOY);
            return true;
        }

        int listTopY  = topPos  + PLC_Y + LIST_HEADER_H;
        int listLeftX = leftPos + PLC_X;
        if (mouseX >= listLeftX && mouseX < listLeftX + ROW_W
                && mouseY >= listTopY && mouseY < listTopY + SLOTS_VISIBLE * ROW_H) {
            int row     = (int)((mouseY - listTopY) / ROW_H);
            int slotIdx = scrollOffset + row;
            if (slotIdx >= 0 && slotIdx < menu.getForgeSlotCount()) {
                ClickType type = Screen.hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
                slotClicked(menu.getSlot(slotIdx), slotIdx, button, type);
                return true;
            }
        }

        int innerX = leftPos + TANK_X + 1;
        int innerY = topPos  + TANK_Y + 1;
        int innerW = TANK_W - 2;
        int innerH = TANK_H - 2;
        int capacity = menu.getFluidCapacityMb();
        if (capacity > 0 && mouseX >= innerX && mouseX < innerX + innerW
                && mouseY >= innerY && mouseY < innerY + innerH) {
            List<FluidLayer> layers = computeFluidLayers(innerY, innerH, capacity);
            for (FluidLayer layer : layers) {
                if (mouseY >= layer.topY && mouseY < layer.bottomY) {
                    sendOutputFluidSelection(layer);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendOutputFluidSelection(FluidLayer layer) {
        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(layer.material.id());
        if (entry == null) return;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(entry.source.get());
        if (fluidId == null) return;
        SmitheryPayloads.sendToServer(
                new ForgeSelectOutputFluidPayload(menu.getBlockPos(), fluidId));
    }
}
