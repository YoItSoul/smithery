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
 * "Casting" — molten fluid + impressed sand cast → vanilla / smithery part item.
 * Catalyst is the Casting Table.
 */
public class CastingJeiCategory extends AbstractRecipeCategory<SmitheryJeiRecipes.JeiCasting> {
    public static final int WIDTH = 130;
    public static final int HEIGHT = 50;

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
        // Slot 1: the cast (impressed sand block). Catalyst-style — keeps its identity across the recipe.
        builder.addInputSlot(6, 17)
                .setStandardSlotBackground()
                .add(recipe.castBlock());

        // Slot 2: molten fluid input. Render an actual tank-style window.
        SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(recipe.material().id());
        if (entry != null) {
            builder.addInputSlot(34, 6)
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 36)
                    .add(entry.source.get(), recipe.castMb());
        }

        // Output: the part item (or vanilla item for synthetic casts).
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
