package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.item.tool.ToolAssemblyRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for Smithery's custom recipe serializers.
 *
 * <p>Reuses the vanilla {@code RecipeType.CRAFTING} type since {@link ToolAssemblyRecipe}
 * implements {@code CraftingRecipe}.
 */
public final class SmitheryRecipes {
    /** Deferred register for Smithery-namespaced recipe serializers. */
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Smithery.MODID);

    /** Serializer for {@link ToolAssemblyRecipe}; assembles a Smithery tool from its parts. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ToolAssemblyRecipe>>
            TOOL_ASSEMBLY = SERIALIZERS.register("tool_assembly", () -> ToolAssemblyRecipe.SERIALIZER);

    /**
     * Binds the deferred register to the mod event bus.
     *
     * @param modEventBus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    private SmitheryRecipes() {}
}
