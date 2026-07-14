package com.soul.smithery.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Shared boilerplate for Smithery's JEI categories: recipe type, title, a blank background of
 * the category's dimensions, and an item-stack icon (the shape JEI 15's {@code IRecipeCategory}
 * requires from every implementor).
 *
 * @param <T> the JEI recipe view type this category renders
 */
abstract class SmitheryJeiCategory<T> implements IRecipeCategory<T> {

    private final RecipeType<T> recipeType;
    private final Component title;
    private final IDrawable background;
    private final IDrawable icon;
    /** Gui helper retained for slot-background drawables in {@code setRecipe}. */
    protected final IGuiHelper guiHelper;

    protected SmitheryJeiCategory(IGuiHelper guiHelper, RecipeType<T> recipeType, Component title,
                                  ItemStack iconStack, int width, int height) {
        this.guiHelper  = guiHelper;
        this.recipeType = recipeType;
        this.title      = title;
        this.background = guiHelper.createBlankDrawable(width, height);
        this.icon       = guiHelper.createDrawableItemStack(iconStack);
    }

    @Override
    public RecipeType<T> getRecipeType() { return recipeType; }

    @Override
    public Component getTitle() { return title; }

    @Override
    public IDrawable getBackground() { return background; }

    @Override
    public IDrawable getIcon() { return icon; }
}
