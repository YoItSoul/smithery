package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryFluids;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidType;

/**
 * JEI category for casting: molten fluid + impressed sand cast produces a part or vanilla item.
 *
 * <p>Catalyst is the casting table; the recipe row shows the cast block, a tank-style fluid
 * window sized to the cast volume, and the resulting output stack.
 */
public class CastingJeiCategory extends AbstractRecipeCategory<SmitheryJeiRecipes.JeiCasting> {
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
        super(
                SmitheryJeiTypes.CASTING,
                Component.translatable("jei." + Smithery.MODID + ".category.casting"),
                guiHelper.createDrawableItemStack(new ItemStack(SmitheryBlocks.CASTING_TABLE_ITEM.get())),
                WIDTH,
                HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiCasting recipe, IFocusGroup focuses) {
        builder.addInputSlot(6, 17)
                .setStandardSlotBackground()
                .add(recipe.castBlock());

        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(recipe.material().id());
        if (entry != null) {
            builder.addInputSlot(34, 6)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 36)
                    .add(entry.source.get(), recipe.castMb());
        }

        builder.addOutputSlot(104, 17)
                .setStandardSlotBackground()
                .add(recipe.output());
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiCasting recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphicsExtractor guiGraphics,
                     double mouseX, double mouseY) {
        Component mbLine = Component.translatable(
                "jei." + Smithery.MODID + ".casting.amount",
                recipe.castMb()
        ).withStyle(ChatFormatting.GRAY);
        Component partLine = Component.translatable("smithery.part.smithery." + recipe.partType().id().getPath())
                .withStyle(ChatFormatting.GOLD);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        guiGraphics.text(font, partLine, 56, 4, 0xFFFFFFFF, false);
        guiGraphics.text(font, mbLine, 56, 18, 0xFFFFFFFF, false);
    }
}
