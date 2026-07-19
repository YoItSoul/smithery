package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryBlocks;
import mezz.jei.api.forge.ForgeTypes;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/**
 * JEI category for forge alloying: a set of molten input fluids combines into an output fluid
 * once the forge reaches the recipe's minimum temperature.
 *
 * <p>Catalyst is the forge controller. Input fluids render as a row of slots (amounts in the
 * fluid tooltips), the output as a taller fluid window on the right, with the temperature gate
 * and output mB drawn as text. Alloying fires automatically inside the forge — there is no
 * player-driven craft step — so this category is primarily a lookup aid.
 */
public class AlloyingJeiCategory extends SmitheryJeiCategory<SmitheryJeiRecipes.JeiAlloying> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 160;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 60;

    /** Max input slots rendered per row (the largest shipped alloy has 5 inputs). */
    private static final int SLOTS_PER_ROW = 6;

    /**
     * Constructs the category, providing JEI with id, title, icon, and layout dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public AlloyingJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.ALLOYING,
                Component.translatable("jei." + Smithery.MODID + ".category.alloying"),
                new ItemStack(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get()),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiAlloying recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            FluidStack input = recipe.inputs().get(i);
            int col = i % SLOTS_PER_ROW;
            int row = i / SLOTS_PER_ROW;
            builder.addSlot(RecipeIngredientRole.INPUT, 6 + col * 19, 8 + row * 19)
                    .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 16)
                    .addIngredient(ForgeTypes.FLUID_STACK, input);
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 128, 8)
                .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 24, 36)
                .addIngredient(ForgeTypes.FLUID_STACK, recipe.output());
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiAlloying recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        Component mbLine = Component.translatable(
                "jei." + Smithery.MODID + ".alloying.amount",
                recipe.output().getAmount()
        ).withStyle(ChatFormatting.GRAY);
        guiGraphics.drawString(font, mbLine, 6, 48, 0xFFFFFF, false);

        if (recipe.minTempC() > 0) {
            Component tempLine = Component.translatable(
                    "jei." + Smithery.MODID + ".alloying.temperature",
                    String.format("%.0f", recipe.minTempC())
            ).withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(font, tempLine, 6, 30, 0xFFFFFF, false);
        }
    }
}
