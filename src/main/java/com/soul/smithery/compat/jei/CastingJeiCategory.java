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
 * JEI category for casting: molten fluid + impressed sand cast produces a part or vanilla item.
 *
 * <p>Catalyst is the casting table; the recipe row shows the cast block, a tank-style fluid
 * window sized to the cast volume, and the resulting output stack.
 */
public class CastingJeiCategory extends SmitheryJeiCategory<SmitheryJeiRecipes.JeiCasting> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 130;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 50;

    /**
     * Constructs the category, providing JEI with id, title, icon, and layout dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public CastingJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.CASTING,
                Component.translatable("jei." + Smithery.MODID + ".category.casting"),
                new ItemStack(SmitheryBlocks.CASTING_TABLE_ITEM.get()),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiCasting recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 17)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.castBlock());

        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(recipe.material().id());
        if (entry != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, 34, 6)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 36)
                    .addIngredient(ForgeTypes.FLUID_STACK,
                            new FluidStack(entry.source.get(), recipe.castMb()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 104, 17)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiCasting recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Component mbLine = Component.translatable(
                "jei." + Smithery.MODID + ".casting.amount",
                recipe.castMb()
        ).withStyle(ChatFormatting.GRAY);
        Component partLine = Component.translatable("smithery.part.smithery." + recipe.partType().id().getPath())
                .withStyle(ChatFormatting.GOLD);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, partLine, 56, 4, 0xFFFFFF, false);
        guiGraphics.drawString(font, mbLine, 56, 18, 0xFFFFFF, false);
    }
}
