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
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration root for Smithery.
 *
 * <p>Loaded by JEI through {@link JeiPlugin}; the rest of the mod has no hard dependency on
 * JEI so it stays optional. Registers categories for melting, casting, basin casting, part press,
 * and tool assembly, plus their recipes and catalyst blocks, and prunes hidden materials' part items
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
                new BasinCastingJeiCategory(guiHelper),
                new PartPressJeiCategory(guiHelper),
                new ToolAssemblyJeiCategory(guiHelper),
                new ModifierJeiCategory(guiHelper),
                new AlloyingJeiCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SmitheryJeiTypes.MELTING,       SmitheryJeiRecipes.buildMeltingRecipes());
        registration.addRecipes(SmitheryJeiTypes.CASTING,       SmitheryJeiRecipes.buildCastingRecipes());
        registration.addRecipes(SmitheryJeiTypes.BASIN_CASTING, SmitheryJeiRecipes.buildBasinCastingRecipes());
        registration.addRecipes(SmitheryJeiTypes.PART_PRESS,    SmitheryJeiRecipes.buildPartPressRecipes());
        registration.addRecipes(SmitheryJeiTypes.TOOL_ASSEMBLY, SmitheryJeiRecipes.buildToolAssemblyRecipes());
        registration.addRecipes(SmitheryJeiTypes.MODIFIER,      SmitheryJeiRecipes.buildModifierRecipes());
        registration.addRecipes(SmitheryJeiTypes.ALLOYING,      SmitheryJeiRecipes.buildAlloyingRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get()), SmitheryJeiTypes.MELTING);
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.CASTING_TABLE_ITEM.get()),    SmitheryJeiTypes.CASTING);
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.CASTING_BASIN_ITEM.get()),    SmitheryJeiTypes.BASIN_CASTING);
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.PART_PRESS_ITEM.get()),       SmitheryJeiTypes.PART_PRESS);
        registration.addRecipeCatalyst(new ItemStack(Items.CRAFTING_TABLE),                       SmitheryJeiTypes.TOOL_ASSEMBLY);
        registration.addRecipeCatalyst(new ItemStack(Items.ANVIL),                                SmitheryJeiTypes.MODIFIER);
        registration.addRecipeCatalyst(new ItemStack(SmitheryBlocks.FORGE_CONTROLLER_ITEM.get()), SmitheryJeiTypes.ALLOYING);
    }

    /**
     * The live runtime, held so {@link #refreshDataDrivenRecipes()} can reach the recipe manager
     * after startup. Null until JEI finishes loading, and reset when it reloads.
     */
    private static @Nullable IJeiRuntime runtime;

    /** The alloy rows currently pushed into JEI's runtime, so a resync can retract them. */
    private static List<SmitheryJeiRecipes.JeiAlloying> pushedAlloys = List.of();
    /** The basin-cast rows currently pushed into JEI's runtime, so a resync can retract them. */
    private static List<SmitheryJeiRecipes.JeiBasinCasting> pushedBasinCasts = List.of();
    /** The modifier rows currently pushed into JEI's runtime, so a resync can retract them. */
    private static List<SmitheryJeiRecipes.JeiModifier> pushedModifiers = List.of();

    /**
     * Rebuilds the categories that read data-pack registries, after the server has shipped its data
     * layer over.
     *
     * <p>{@code registerRecipes} runs once at JEI startup, reading registries that on a dedicated
     * server are still empty at that point — {@code AddReloadListenerEvent} listeners only ever run
     * on the logical server. Without this the alloying screen would be blank on every multiplayer
     * server, since all built-in alloys ship as JSON, and pack-added basin casts and anvil modifier
     * sources would never appear.
     *
     * <p>Safe to call before JEI is ready: it no-ops, and JEI's own startup then reads the
     * already-applied data. Called from the client handler for {@code SmitheryDataSyncPayload}.
     */
    public static void refreshDataDrivenRecipes() {
        IJeiRuntime jei = runtime;
        if (jei == null) return;
        IRecipeManager recipes = jei.getRecipeManager();

        // Retract the exact objects previously handed over rather than diffing: the row records
        // hold FluidStacks and ItemStacks, neither of which compares by value, so a freshly built
        // row never equals the one it replaces.
        if (!pushedAlloys.isEmpty()) recipes.hideRecipes(SmitheryJeiTypes.ALLOYING, pushedAlloys);
        if (!pushedBasinCasts.isEmpty()) recipes.hideRecipes(SmitheryJeiTypes.BASIN_CASTING, pushedBasinCasts);
        if (!pushedModifiers.isEmpty()) recipes.hideRecipes(SmitheryJeiTypes.MODIFIER, pushedModifiers);

        pushedAlloys = SmitheryJeiRecipes.buildAlloyingRecipes();
        pushedBasinCasts = SmitheryJeiRecipes.buildBasinCastingRecipes();
        pushedModifiers = SmitheryJeiRecipes.buildModifierRecipes();
        if (!pushedAlloys.isEmpty()) recipes.addRecipes(SmitheryJeiTypes.ALLOYING, pushedAlloys);
        if (!pushedBasinCasts.isEmpty()) recipes.addRecipes(SmitheryJeiTypes.BASIN_CASTING, pushedBasinCasts);
        if (!pushedModifiers.isEmpty()) recipes.addRecipes(SmitheryJeiTypes.MODIFIER, pushedModifiers);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // JEI has just rebuilt from the current registries, so nothing of ours is outstanding.
        pushedAlloys = List.of();
        pushedBasinCasts = List.of();
        pushedModifiers = List.of();

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
