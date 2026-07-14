package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolCompositions;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI category for tool assembly: a set of part items crafts into an assembled tool on the
 * vanilla crafting table.
 *
 * <p>Each input slot's displayed ingredient is overridden every cycle to a different material's
 * part, and the output slot's displayed ingredient is the tool composed from whatever parts
 * are currently shown — never a single all-same-material tool. Pre-composed tools are
 * intentionally absent from the output ingredient list so JEI's natural cycler does not fight
 * the override; the vanilla crafting category continues to surface per-material tool lookups
 * via the JSON {@code ToolAssemblyRecipe}. Shift-to-pause is honoured by JEI's own ticker.
 */
public class ToolAssemblyJeiCategory extends AbstractRecipeCategory<SmitheryJeiRecipes.JeiToolAssembly> {
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
        super(
                SmitheryJeiTypes.TOOL_ASSEMBLY,
                Component.translatable("jei." + Smithery.MODID + ".category.tool_assembly"),
                guiHelper.createDrawableItemStack(new ItemStack(Items.CRAFTING_TABLE)),
                WIDTH,
                HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiToolAssembly recipe, IFocusGroup focuses) {
        int x = PART_X;
        for (List<ItemStack> stacksForSlot : recipe.partsBySlot()) {
            IRecipeSlotBuilder slot = builder.addInputSlot(x, PART_Y).setStandardSlotBackground();
            for (ItemStack stack : stacksForSlot) slot.add(stack);
            x += 18;
        }

        IRecipeSlotBuilder output = builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).setStandardSlotBackground();
        Item toolItem = BuiltInRegistries.ITEM.get(recipe.toolType().id())
                .<Item>map(r -> r.value())
                .orElse(null);
        if (toolItem != null) output.add(new ItemStack(toolItem));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, SmitheryJeiRecipes.JeiToolAssembly recipe, IFocusGroup focuses) {
        applyRandomMixOverrides(recipe, builder.getRecipeSlots().getSlots());
    }

    @Override
    public void onDisplayedIngredientsUpdate(SmitheryJeiRecipes.JeiToolAssembly recipe,
                                             List<IRecipeSlotDrawable> recipeSlots,
                                             IFocusGroup focuses) {
        applyRandomMixOverrides(recipe, recipeSlots);
    }

    private static void applyRandomMixOverrides(SmitheryJeiRecipes.JeiToolAssembly recipe,
                                                List<IRecipeSlotDrawable> slots) {
        List<IRecipeSlotDrawable> inputs = new ArrayList<>();
        IRecipeSlotDrawable output = null;
        for (IRecipeSlotDrawable s : slots) {
            if (s.getRole() == RecipeIngredientRole.INPUT) inputs.add(s);
            else if (s.getRole() == RecipeIngredientRole.OUTPUT) output = s;
        }
        if (output == null || inputs.isEmpty()) return;
        if (inputs.size() != recipe.partsBySlot().size()) return;

        long tick = System.currentTimeMillis() / 1000L;

        List<ResourceLocation> matIds = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            IRecipeSlotDrawable slot = inputs.get(i);
            List<ItemStack> slotParts = recipe.partsBySlot().get(i);
            if (slotParts.isEmpty()) return;
            int materialIdx = (int) Math.floorMod(tick + i * 37L, slotParts.size());
            ItemStack part = slotParts.get(materialIdx);

            slot.clearDisplayOverrides();
            slot.createDisplayOverrides().add(part);

            if (!(part.getItem() instanceof PartItem partItem)) {
                output.clearDisplayOverrides();
                return;
            }
            matIds.add(partItem.materialId());
        }

        if (matIds.size() != recipe.toolType().slots().size()) return;

        Item toolItem = BuiltInRegistries.ITEM.get(recipe.toolType().id())
                .<Item>map(r -> r.value())
                .orElse(null);
        if (toolItem == null) return;

        ToolComposition comp = new ToolComposition(recipe.toolType().id(), matIds);
        ItemStack tool = ToolCompositions.apply(new ItemStack(toolItem), comp);

        output.clearDisplayOverrides();
        output.createDisplayOverrides().add(tool);
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiToolAssembly recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphicsExtractor guiGraphics,
                     double mouseX, double mouseY) {
        Component title = Component.translatable("smithery.tool.smithery." + recipe.toolType().id().getPath())
                .copy()
                .withStyle(ChatFormatting.GOLD);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textW = font.width(title);
        guiGraphics.text(font, title, (WIDTH - textW) / 2, 6, 0xFFFFFFFF, false);
    }
}
