package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import mezz.jei.api.recipe.RecipeType;

/**
 * Holds the JEI {@code RecipeType} ids for each Smithery recipe domain.
 *
 * <p>Plugin-public so category, recipe, and catalyst registrations all reference the same ids.
 */
public final class SmitheryJeiTypes {
    /** Recipe type for the melting category. */
    public static final RecipeType<SmitheryJeiRecipes.JeiMelting> MELTING =
            RecipeType.create(Smithery.MODID, "melting", SmitheryJeiRecipes.JeiMelting.class);

    /** Recipe type for the casting category. */
    public static final RecipeType<SmitheryJeiRecipes.JeiCasting> CASTING =
            RecipeType.create(Smithery.MODID, "casting", SmitheryJeiRecipes.JeiCasting.class);

    /** Recipe type for the part-press category. */
    public static final RecipeType<SmitheryJeiRecipes.JeiPartPress> PART_PRESS =
            RecipeType.create(Smithery.MODID, "part_press", SmitheryJeiRecipes.JeiPartPress.class);

    /** Recipe type for the tool-assembly category. */
    public static final RecipeType<SmitheryJeiRecipes.JeiToolAssembly> TOOL_ASSEMBLY =
            RecipeType.create(Smithery.MODID, "tool_assembly", SmitheryJeiRecipes.JeiToolAssembly.class);

    /** Recipe type for the modifiers reference category. */
    public static final RecipeType<SmitheryJeiRecipes.JeiModifier> MODIFIER =
            RecipeType.create(Smithery.MODID, "modifier", SmitheryJeiRecipes.JeiModifier.class);

    private SmitheryJeiTypes() {}
}
