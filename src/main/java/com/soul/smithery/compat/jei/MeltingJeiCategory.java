package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryFluids;
import mezz.jei.api.constants.VanillaTypes;
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
 * JEI category for melting: input item produces a molten fluid at the material's melt temperature.
 *
 * <p>Catalyst is the forge controller; the recipe row shows the input stack, a bucket-sized
 * fluid output window, and a textual readout of the temperature and output mB.
 */
public class MeltingJeiCategory extends SmitheryJeiCategory<SmitheryJeiRecipes.JeiMelting> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 130;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 50;

    /**
     * Constructs the category, providing JEI with id, title, icon, and layout dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public MeltingJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.MELTING,
                Component.translatable("jei." + Smithery.MODID + ".category.melting"),
                new ItemStack(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get()),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiMelting recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 17)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.input());

        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(recipe.material().id());
        if (entry != null) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 6)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 24, 36)
                    .addIngredient(ForgeTypes.FLUID_STACK,
                            new FluidStack(entry.source.get(), recipe.outputMb()));
        }
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiMelting recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Component tempLine = Component.translatable(
                "jei." + Smithery.MODID + ".melting.temperature",
                String.format("%.0f", recipe.meltingTempCelsius())
        ).withStyle(ChatFormatting.GOLD);
        Component mbLine = Component.translatable(
                "jei." + Smithery.MODID + ".melting.amount",
                recipe.outputMb()
        ).withStyle(ChatFormatting.GRAY);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, tempLine, 30, 4, 0xFFFFFF, false);
        guiGraphics.drawString(font, mbLine, 30, 18, 0xFFFFFF, false);
    }
}
