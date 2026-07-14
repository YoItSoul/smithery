package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI category for tool assembly: a set of part items crafts into an assembled tool on the
 * vanilla crafting table.
 *
 * <p>Each input slot lists one part per material and the output slot lists the matching
 * composed tools, all joined with a focus link so JEI cycles every slot in lockstep — each
 * cycle reads as one all-of-a-material composition. Mixed-material tools remain craftable in
 * game; the vanilla crafting category surfaces per-material tool lookups via the JSON
 * {@code ToolAssemblyRecipe}.
 */
public class ToolAssemblyJeiCategory extends SmitheryJeiCategory<SmitheryJeiRecipes.JeiToolAssembly> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 150;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 46;

    private static final int PART_X = 6;
    private static final int PART_Y = 22;
    private static final int OUTPUT_X = 126;
    private static final int OUTPUT_Y = 22;

    /**
     * Constructs the category, providing JEI with id, title, icon, and layout dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public ToolAssemblyJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.TOOL_ASSEMBLY,
                Component.translatable("jei." + Smithery.MODID + ".category.tool_assembly"),
                new ItemStack(Items.CRAFTING_TABLE),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiToolAssembly recipe, IFocusGroup focuses) {
        List<IRecipeSlotBuilder> linked = new ArrayList<>();
        int x = PART_X;
        for (List<ItemStack> stacksForSlot : recipe.partsBySlot()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, x, PART_Y)
                    .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                    .addItemStacks(stacksForSlot);
            linked.add(slot);
            x += 18;
        }

        IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredients(VanillaTypes.ITEM_STACK, recipe.tools());
        linked.add(output);

        // Every slot lists exactly one variant per material (see buildToolAssemblyRecipes),
        // so a focus link cycles them in lockstep as coherent compositions.
        builder.createFocusLink(linked.toArray(new IRecipeSlotBuilder[0]));
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiToolAssembly recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Component title = Component.translatable("smithery.tool.smithery." + recipe.toolType().id().getPath())
                .copy()
                .withStyle(ChatFormatting.GOLD);

        var font = Minecraft.getInstance().font;
        int textW = font.width(title);
        guiGraphics.drawString(font, title, (WIDTH - textW) / 2, 6, 0xFFFFFF, false);
    }
}
