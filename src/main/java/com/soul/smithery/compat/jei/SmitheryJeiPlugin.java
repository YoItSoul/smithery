package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * JEI integration root. Loaded automatically by JEI via {@link JeiPlugin}; loadable iff the
 * JEI runtime classes are present, so the rest of the mod has no hard dependency on JEI.
 *
 * Registers three recipe categories:
 *   - smithery:melting     — input item → molten fluid + temperature (Forge Controller catalyst)
 *   - smithery:casting     — molten fluid + impressed sand cast → part / vanilla item (Casting Table catalyst)
 *   - smithery:part_press  — raw non-meltable input → part item (Part Press catalyst)
 */
@JeiPlugin
public class SmitheryJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Smithery.MODID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MeltingJeiCategory(guiHelper),
                new CastingJeiCategory(guiHelper),
                new PartPressJeiCategory(guiHelper),
                new ToolAssemblyJeiCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SmitheryJeiTypes.MELTING,       SmitheryJeiRecipes.buildMeltingRecipes());
        registration.addRecipes(SmitheryJeiTypes.CASTING,       SmitheryJeiRecipes.buildCastingRecipes());
        registration.addRecipes(SmitheryJeiTypes.PART_PRESS,    SmitheryJeiRecipes.buildPartPressRecipes());
        registration.addRecipes(SmitheryJeiTypes.TOOL_ASSEMBLY, SmitheryJeiRecipes.buildToolAssemblyRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(SmitheryJeiTypes.MELTING,       SmitheryBlocks.FORGE_CONTROLLER_ITEM.get());
        registration.addCraftingStation(SmitheryJeiTypes.CASTING,       SmitheryBlocks.CASTING_TABLE_ITEM.get());
        registration.addCraftingStation(SmitheryJeiTypes.PART_PRESS,    SmitheryBlocks.PART_PRESS_ITEM.get());
        registration.addCraftingStation(SmitheryJeiTypes.TOOL_ASSEMBLY, Items.CRAFTING_TABLE);
    }
}
