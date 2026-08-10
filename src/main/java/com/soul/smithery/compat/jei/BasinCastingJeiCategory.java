package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.PartItem;
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
 * JEI category for basin casting: molten fluid alone produces the material's storage block.
 *
 * <p>Catalyst is the casting basin. There is no cast shape to show — a basin has only one — so the
 * row is a tank-style fluid window sized to the cast volume, the material name and cost, and the
 * resulting block.
 */
public class BasinCastingJeiCategory extends SmitheryJeiCategory<SmitheryJeiRecipes.JeiBasinCasting> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 130;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 50;

    /**
     * Constructs the category, providing JEI with id, title, icon, and layout dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public BasinCastingJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.BASIN_CASTING,
                Component.translatable("jei." + Smithery.MODID + ".category.basin_casting"),
                new ItemStack(SmitheryBlocks.CASTING_BASIN_ITEM.get()),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiBasinCasting recipe, IFocusGroup focuses) {
        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(recipe.material().id());
        if (entry != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, 6, 6)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 36)
                    .addIngredient(ForgeTypes.FLUID_STACK,
                            new FluidStack(entry.source.get(), recipe.castMb()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 104, 17)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiBasinCasting recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Component materialLine = Component.translatable(
                PartItem.materialTranslationKey(recipe.material().id())
        ).withStyle(ChatFormatting.GOLD);
        Component mbLine = Component.translatable(
                "jei." + Smithery.MODID + ".casting.amount",
                recipe.castMb()
        ).withStyle(ChatFormatting.GRAY);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, materialLine, 30, 4, 0xFFFFFF, false);
        guiGraphics.drawString(font, mbLine, 30, 18, 0xFFFFFF, false);
    }
}
