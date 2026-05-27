package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.Identifier;

/**
 * JEI {@link IRecipeType} ids for each Smithery recipe domain. Plugin-public so other code can
 * reference the same ids when registering recipes or catalysts.
 */
public final class SmitheryJeiTypes {
    public static final IRecipeType<SmitheryJeiRecipes.JeiMelting> MELTING =
            IRecipeType.create(id("melting"), SmitheryJeiRecipes.JeiMelting.class);

    public static final IRecipeType<SmitheryJeiRecipes.JeiCasting> CASTING =
            IRecipeType.create(id("casting"), SmitheryJeiRecipes.JeiCasting.class);

    public static final IRecipeType<SmitheryJeiRecipes.JeiPartPress> PART_PRESS =
            IRecipeType.create(id("part_press"), SmitheryJeiRecipes.JeiPartPress.class);

    public static final IRecipeType<SmitheryJeiRecipes.JeiToolAssembly> TOOL_ASSEMBLY =
            IRecipeType.create(id("tool_assembly"), SmitheryJeiRecipes.JeiToolAssembly.class);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryJeiTypes() {}
}
