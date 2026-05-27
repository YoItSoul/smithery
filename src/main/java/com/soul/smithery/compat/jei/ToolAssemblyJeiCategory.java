package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * "Tool Assembly" — N PartItems → assembled tool, via the vanilla crafting table.
 *
 * <p>Each input slot's display is overridden every cycle to a different material's part (per-slot
 * offset, advanced from wall clock seconds), so the parts visibly look like a random mix. The
 * output slot's display is the tool composed from the parts currently shown — never one of the
 * all-same-material tools. The all-same-material tools are intentionally NOT added to the output
 * slot's ingredient list, so JEI's natural cycler has nothing to fall back to and the display is
 * driven entirely by the overrides we apply in {@link #onDisplayedIngredientsUpdate}.
 *
 * <p>Trade-off: JEI's "find recipe that produces Iron Pickaxe" lookup won't match this category
 * (no per-material tool listed in the output). The vanilla crafting category still covers it via
 * the JSON {@code ToolAssemblyRecipe}.
 *
 * <p>Shift-to-pause is handled natively by JEI: {@code CycleTicker.tick()} early-exits when
 * {@code Minecraft.hasShiftDown()} is true, so the displayed overrides freeze in place until
 * Shift is released.
 */
public class ToolAssemblyJeiCategory extends AbstractRecipeCategory<SmitheryJeiRecipes.JeiToolAssembly> {
    public static final int WIDTH = 150;
    public static final int HEIGHT = 46;

    private static final int PART_X = 6;
    private static final int PART_Y = 22;
    private static final int OUTPUT_X = 126;
    private static final int OUTPUT_Y = 22;

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
        // Inputs: keep every material's part in the slot so JEI material-based search still
        // works ("show me recipes using Iron Pick Head"). Display is driven by overrides below.
        int x = PART_X;
        for (List<ItemStack> stacksForSlot : recipe.partsBySlot()) {
            IRecipeSlotBuilder slot = builder.addInputSlot(x, PART_Y).setStandardSlotBackground();
            for (ItemStack stack : stacksForSlot) slot.add(stack);
            x += 18;
        }

        // Output: a single bare-tool placeholder. We deliberately do NOT add the pre-composed
        // all-same-material tools — the displayed tool is always whatever onDisplayedIngredientsUpdate
        // computes from the currently-shown parts.
        IRecipeSlotBuilder output = builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).setStandardSlotBackground();
        Item toolItem = BuiltInRegistries.ITEM.get(recipe.toolType().id())
                .<Item>map(r -> r.value())
                .orElse(null);
        if (toolItem != null) output.add(new ItemStack(toolItem));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, SmitheryJeiRecipes.JeiToolAssembly recipe, IFocusGroup focuses) {
        // Initial overrides so the very first frame already shows mixed materials.
        applyRandomMixOverrides(recipe, builder.getRecipeSlots().getSlots());
    }

    @Override
    public void onDisplayedIngredientsUpdate(SmitheryJeiRecipes.JeiToolAssembly recipe,
                                             List<IRecipeSlotDrawable> recipeSlots,
                                             IFocusGroup focuses) {
        applyRandomMixOverrides(recipe, recipeSlots);
    }

    /**
     * Force each input slot to show a different material's part (deterministic from wall clock +
     * per-slot offset), then compute the corresponding tool and force it onto the output slot.
     * Re-applied every cycle tick because {@code RecipeLayout.tick()} clears all overrides before
     * calling {@link #onDisplayedIngredientsUpdate}.
     */
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

        // Wall clock seconds advance ~1Hz, matching JEI's cycle cadence. Per-slot offset (37 is
        // a small prime coprime with typical material counts) guarantees different indices.
        long tick = System.currentTimeMillis() / 1000L;

        List<Identifier> matIds = new ArrayList<>(inputs.size());
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
        ItemStack tool = SmitheryToolItem.applyComposition(new ItemStack(toolItem), comp);

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
