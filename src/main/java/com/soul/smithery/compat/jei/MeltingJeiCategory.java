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
import net.neoforged.neoforge.fluids.FluidType;

/**
 * JEI category for melting: input item produces a molten fluid at the material's melt temperature.
 *
 * <p>Catalyst is the forge controller; the recipe row shows the input stack, a bucket-sized
 * fluid output window, and a textual readout of the temperature and output mB.
 */
public class MeltingJeiCategory extends AbstractRecipeCategory<SmitheryJeiRecipes.JeiMelting> {
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
        super(
                SmitheryJeiTypes.MELTING,
                Component.translatable("jei." + Smithery.MODID + ".category.melting"),
                guiHelper.createDrawableItemStack(new ItemStack(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get())),
                WIDTH,
                HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmitheryJeiRecipes.JeiMelting recipe, IFocusGroup focuses) {
        builder.addInputSlot(6, 17)
                .setStandardSlotBackground()
                .add(recipe.input());

        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(recipe.material().id());
        if (entry != null) {
            builder.addOutputSlot(96, 6)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 24, 36)
                    .add(entry.source.get(), recipe.outputMb());
        }
    }

    @Override
    public void draw(SmitheryJeiRecipes.JeiMelting recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphicsExtractor guiGraphics,
                     double mouseX, double mouseY) {
        Component tempLine = Component.translatable(
                "jei." + Smithery.MODID + ".melting.temperature",
                String.format("%.0f", recipe.meltingTempCelsius())
        ).withStyle(ChatFormatting.GOLD);
        Component mbLine = Component.translatable(
                "jei." + Smithery.MODID + ".melting.amount",
                recipe.outputMb()
        ).withStyle(ChatFormatting.GRAY);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        guiGraphics.text(font, tempLine, 30, 4, 0xFFFFFFFF, false);
        guiGraphics.text(font, mbLine, 30, 18, 0xFFFFFFFF, false);
    }
}
