package com.soul.smithery.gui;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.block.entity.PartPressBlockEntity;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.network.PartPressSelectPartPayload;
import com.soul.smithery.network.SmitheryPayloads;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shape picker for the Part Press: every shape it can cut, laid out as a grid of the part each
 * would produce, with the current one highlighted.
 *
 * <p>Deliberately not an {@code AbstractContainerScreen}. The press has no inventory to show — its
 * input and output are handled by clicking the block itself — so a menu would exist only to carry a
 * single enum choice. This screen is client-only and reports the pick with one packet.
 *
 * <p>It also skips {@code renderBackground}, so the world stays visible behind a translucent panel
 * rather than the usual dimmed overlay: the press is a machine you configure while standing at it,
 * and the panel is small enough that blacking out the room would be the odd choice.
 */
public class PartPressScreen extends Screen {

    /** Side of one grid cell, matching a vanilla inventory slot. */
    private static final int CELL = 18;
    /** Cells per row before wrapping. */
    private static final int COLUMNS = 6;
    /** Padding between the panel edge and the grid. */
    private static final int PADDING = 8;
    /** Height reserved above the grid for the title. */
    private static final int TITLE_H = 14;

    private static final int COL_PANEL     = 0xC0101010;
    private static final int COL_BORDER    = 0xFF5A5A5A;
    private static final int COL_CELL      = 0x40FFFFFF;
    private static final int COL_HOVER     = 0x60FFFFFF;
    private static final int COL_SELECTED  = 0xFFE8A33D;
    private static final int COL_TITLE     = 0xFFE0E0E0;

    private final BlockPos pressPos;
    private final @Nullable PartType initialSelection;
    private final List<PartType> shapes;
    private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    /**
     * Constructs the picker.
     *
     * @param pressPos         position of the press being configured
     * @param initialSelection shape the press is set to now, highlighted in the grid; may be null
     */
    public PartPressScreen(BlockPos pressPos, @Nullable PartType initialSelection) {
        super(Component.translatable("gui." + Smithery.MODID + ".part_press.title"));
        this.pressPos = pressPos;
        this.initialSelection = initialSelection;
        this.shapes = PartPressBlockEntity.selectablePartTypes();
    }

    @Override
    protected void init() {
        int rows = Math.max(1, (shapes.size() + COLUMNS - 1) / COLUMNS);
        panelW = PADDING * 2 + COLUMNS * CELL;
        panelH = PADDING * 2 + TITLE_H + rows * CELL;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        // One icon per shape, resolved once: the grid is the same every frame, and findPart walks
        // the material registry until it finds one that actually has this part.
        icons.clear();
        for (PartType pt : shapes) {
            icons.put(pt.id(), iconFor(pt));
        }
    }

    /**
     * Picks a stack to stand for a shape: the part itself in whichever material owns one. Falls back
     * to nothing rather than a placeholder, so a shape with no part item anywhere reads as a blank
     * cell instead of a lie about what the press would produce.
     */
    private static ItemStack iconFor(PartType partType) {
        for (Material material : SmitheryAPI.MATERIALS.all()) {
            PartItem part = SmitheryItems.findPart(material.id(), partType.id());
            if (part != null) return new ItemStack(part);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No renderBackground call: the point of this screen is that the press stays visible.
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, COL_BORDER);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        g.drawString(font, title, panelX + PADDING, panelY + PADDING, COL_TITLE, false);

        int hovered = cellAt(mouseX, mouseY);
        for (int i = 0; i < shapes.size(); i++) {
            PartType pt = shapes.get(i);
            int cx = cellX(i);
            int cy = cellY(i);

            boolean selected = initialSelection != null && initialSelection.id().equals(pt.id());
            if (selected) {
                g.fill(cx - 1, cy - 1, cx + CELL - 1, cy + CELL - 1, COL_SELECTED);
            }
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, i == hovered ? COL_HOVER : COL_CELL);

            ItemStack icon = icons.getOrDefault(pt.id(), ItemStack.EMPTY);
            if (!icon.isEmpty()) {
                g.renderItem(icon, cx + 1, cy + 1);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (hovered >= 0) {
            g.renderTooltip(font,
                    Component.translatable(PartItem.partTranslationKey(shapes.get(hovered).id())),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cellAt((int) mouseX, (int) mouseY);
        if (index >= 0) {
            SmitheryPayloads.sendToServer(
                    new PartPressSelectPartPayload(pressPos, shapes.get(index).id()));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Index of the grid cell under the cursor, or -1. */
    private int cellAt(int mouseX, int mouseY) {
        for (int i = 0; i < shapes.size(); i++) {
            int cx = cellX(i);
            int cy = cellY(i);
            if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                return i;
            }
        }
        return -1;
    }

    private int cellX(int index) {
        return panelX + PADDING + (index % COLUMNS) * CELL;
    }

    private int cellY(int index) {
        return panelY + PADDING + TITLE_H + (index / COLUMNS) * CELL;
    }

    // The press sits in the world and the pick is a single click; pausing a singleplayer game for
    // it would stall the very forge that is feeding the press.
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
