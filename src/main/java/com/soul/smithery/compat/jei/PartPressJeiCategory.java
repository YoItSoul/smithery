package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category for the part press: a raw non-meltable input (logs, flint, slime, coral)
 * yields a tool part item.
 *
 * <p>Catalyst is the part press block; the row shows the input stack, the output part stack,
 * and a centered part-name label.
 */
public class PartPressJeiCategory extends SmitheryJeiCategory<SmitheryJeiRecipes.JeiPartPress> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 130;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 36;

    /**
     * Constructs the category, providing JEI with id, title, icon, and layout dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public PartPressJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.PART_PRESS,
                Component.translatable("jei." + Smithery.MODID + ".category.part_press"),
                new ItemStack(SmitheryBlocks.PART_PRESS_ITEM.get()),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiPartPress recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 10)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.input());

        builder.addSlot(RecipeIngredientRole.OUTPUT, 104, 10)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiPartPress recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Component partLine = Component.translatable("smithery.part.smithery." + recipe.partType().id().getPath())
                .withStyle(ChatFormatting.GOLD);

        var font = Minecraft.getInstance().font;
        int textW = font.width(partLine);
        guiGraphics.drawString(font, partLine, (WIDTH - textW) / 2, 1, 0xFFFFFF, false);
    }
}
