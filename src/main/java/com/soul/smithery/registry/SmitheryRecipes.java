package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.tool.ToolAssemblyRecipe;
import com.soul.smithery.recipe.KelpStringCombineRecipe;
import com.soul.smithery.recipe.KelpStringProgressRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for our custom recipe serializers. RecipeType.CRAFTING (the vanilla type) is
 * reused since ToolAssemblyRecipe implements CraftingRecipe.
 */
public final class SmitheryRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Smithery.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ToolAssemblyRecipe>>
            TOOL_ASSEMBLY = SERIALIZERS.register("tool_assembly", () -> ToolAssemblyRecipe.SERIALIZER);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<KelpStringProgressRecipe>>
            KELP_STRING_PROGRESS = SERIALIZERS.register("kelp_string_progress",
                    () -> KelpStringProgressRecipe.SERIALIZER);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<KelpStringCombineRecipe>>
            KELP_STRING_COMBINE = SERIALIZERS.register("kelp_string_combine",
                    () -> KelpStringCombineRecipe.SERIALIZER);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    private SmitheryRecipes() {}
}
