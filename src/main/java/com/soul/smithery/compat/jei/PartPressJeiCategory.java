package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "Part Press" — raw non-meltable input (logs, flint, slime, resin, coral) → tool part item.
 * Catalyst is the Part Press block.
 */
public class PartPressJeiCategory extends AbstractRecipeCategory<SmitheryJeiRecipes.JeiPartPress> {
    public static final int WIDTH = 130;
    public static final int HEIGHT = 36;

    public PartPressJeiCategory(IGuiHelper guiHelper) {
        super(
                SmitheryJeiTypes.PART_PRESS,
                Component.translatable("jei." + Smithery.MODID + ".category.part_press"),
                guiHelper.createDrawableItemStack(new ItemStack(SmitheryBlocks.PART_PRESS_ITEM.get())),
                WIDTH,
                HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiPartPress recipe, IFocusGroup focuses) {
        builder.addInputSlot(6, 10)
                .setStandardSlotBackground()
                .add(recipe.input());

        builder.addOutputSlot(104, 10)
                .setStandardSlotBackground()
                .add(recipe.output());
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiPartPress recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphicsExtractor guiGraphics,
                     double mouseX, double mouseY) {
        Component partLine = Component.translatable("smithery.part.smithery." + recipe.partType().id().getPath())
                .withStyle(ChatFormatting.GOLD);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textW = font.width(partLine);
        guiGraphics.text(font, partLine, (WIDTH - textW) / 2, 1, 0xFFFFFFFF, false);
    }
}
