package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.Identifier;

/**
 * Holds the JEI {@code IRecipeType} ids for each Smithery recipe domain.
 *
 * <p>Plugin-public so category, recipe, and catalyst registrations all reference the same ids.
 */
public final class SmitheryJeiTypes {
    /** Recipe type for the melting category. */
    public static final IRecipeType<SmitheryJeiRecipes.JeiMelting> MELTING =
            IRecipeType.create(id("melting"), SmitheryJeiRecipes.JeiMelting.class);

    /** Recipe type for the casting category. */
    public static final IRecipeType<SmitheryJeiRecipes.JeiCasting> CASTING =
            IRecipeType.create(id("casting"), SmitheryJeiRecipes.JeiCasting.class);

    /** Recipe type for the part-press category. */
    public static final IRecipeType<SmitheryJeiRecipes.JeiPartPress> PART_PRESS =
            IRecipeType.create(id("part_press"), SmitheryJeiRecipes.JeiPartPress.class);

    /** Recipe type for the tool-assembly category. */
    public static final IRecipeType<SmitheryJeiRecipes.JeiToolAssembly> TOOL_ASSEMBLY =
            IRecipeType.create(id("tool_assembly"), SmitheryJeiRecipes.JeiToolAssembly.class);

    /** Recipe type for the modifiers reference category. */
    public static final IRecipeType<SmitheryJeiRecipes.JeiModifier> MODIFIER =
            IRecipeType.create(id("modifier"), SmitheryJeiRecipes.JeiModifier.class);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryJeiTypes() {}
}
