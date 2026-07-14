package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration root for Smithery.
 *
 * <p>Loaded by JEI through {@link JeiPlugin}; the rest of the mod has no hard dependency on
 * JEI so it stays optional. Registers categories for melting, casting, part press, and tool
 * assembly, plus their recipes and catalyst blocks, and prunes hidden materials' part items
 * from the ingredient sidebar once the runtime is available.
 */
@JeiPlugin
public class SmitheryJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MeltingJeiCategory(guiHelper),
                new CastingJeiCategory(guiHelper),
                new PartPressJeiCategory(guiHelper),
                new ToolAssemblyJeiCategory(guiHelper),
                new ModifierJeiCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SmitheryJeiTypes.MELTING,       SmitheryJeiRecipes.buildMeltingRecipes());
        registration.addRecipes(SmitheryJeiTypes.CASTING,       SmitheryJeiRecipes.buildCastingRecipes());
        registration.addRecipes(SmitheryJeiTypes.PART_PRESS,    SmitheryJeiRecipes.buildPartPressRecipes());
        registration.addRecipes(SmitheryJeiTypes.TOOL_ASSEMBLY, SmitheryJeiRecipes.buildToolAssemblyRecipes());
        registration.addRecipes(SmitheryJeiTypes.MODIFIER,      SmitheryJeiRecipes.buildModifierRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get()), SmitheryJeiTypes.MELTING);
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.CASTING_TABLE_ITEM.get()),    SmitheryJeiTypes.CASTING);
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.PART_PRESS_ITEM.get()),       SmitheryJeiTypes.PART_PRESS);
        registration.addRecipeCatalyst(new ItemStack(Items.CRAFTING_TABLE),                       SmitheryJeiTypes.TOOL_ASSEMBLY);
        registration.addRecipeCatalyst(new ItemStack(Items.ANVIL),                                SmitheryJeiTypes.MODIFIER);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        List<ItemStack> hidden = new ArrayList<>();
        for (Material material : SmitheryAPI.MATERIALS.all()) {
            if (!SmitheryJeiRecipes.isHiddenFromJei(material.id())) continue;
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                var di = SmitheryItems.getBuiltInPart(material.id(), pt.id());
                if (di == null) continue;
                hidden.add(new ItemStack(di.get()));
            }
        }
        if (!hidden.isEmpty()) {
            jeiRuntime.getIngredientManager()
                    .removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }
}
