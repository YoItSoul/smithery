package com.soul.smithery.datagen;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Placeholder recipe provider — emits vanilla {@code stonecutting} recipes for every
 * (non-meltable material × part type) pair. Lets players actually build wood, flint,
 * slime, resin, and coral parts via the stonecutter while the proper in-world non-meltable
 * pipeline gets designed. One recipe per pair, 1:1 input → 1 part. Tag-based ingredient
 * where vanilla supplies a sensible tag (logs, coral blocks), plain item otherwise.
 * Re-running {@code ./gradlew runData} regenerates everything.
 */
public final class SmitheryRecipeProvider extends RecipeProvider {

    /** Either a tag-of-items or a list of explicit items. Mutually exclusive. */
    private record Input(java.util.List<Item> items, @org.jspecify.annotations.Nullable TagKey<Item> tag) {
        static Input of(Item... items)  { return new Input(java.util.List.of(items), null); }
        static Input of(TagKey<Item> t) { return new Input(java.util.List.of(), t); }
        Ingredient ingredient(HolderLookup.Provider registries) {
            if (tag != null) {
                return Ingredient.of(registries.lookupOrThrow(Registries.ITEM).getOrThrow(tag));
            }
            return Ingredient.of(items.toArray(new Item[0]));
        }
        String unlockKey() {
            if (tag != null) return "has_" + tag.location().getPath();
            return "has_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items.getFirst()).getPath();
        }
    }

    private static Map<Identifier, Input> nonMeltableInputs() {
        Map<Identifier, Input> map = new LinkedHashMap<>();
        map.put(SmitheryMaterials.WOOD,  Input.of(ItemTags.LOGS));
        map.put(SmitheryMaterials.FLINT, Input.of(Items.FLINT));
        map.put(SmitheryMaterials.SLIME, Input.of(Items.SLIME_BALL));
        map.put(SmitheryMaterials.RESIN, Input.of(Items.RESIN_CLUMP));
        // No vanilla coral_blocks ItemTag (only BlockTags has it). Enumerate the live + dead
        // coral block items directly — five species × two states.
        map.put(SmitheryMaterials.CORAL, Input.of(
                Items.TUBE_CORAL_BLOCK, Items.BRAIN_CORAL_BLOCK,
                Items.BUBBLE_CORAL_BLOCK, Items.FIRE_CORAL_BLOCK,
                Items.HORN_CORAL_BLOCK,
                Items.DEAD_TUBE_CORAL_BLOCK, Items.DEAD_BRAIN_CORAL_BLOCK,
                Items.DEAD_BUBBLE_CORAL_BLOCK, Items.DEAD_FIRE_CORAL_BLOCK,
                Items.DEAD_HORN_CORAL_BLOCK));
        return map;
    }

    public SmitheryRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        Map<Identifier, Input> inputs = nonMeltableInputs();
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            Input in = inputs.get(mat.id());
            if (in == null) continue;
            // Safety net — a non-meltable material in the map shouldn't have a melt temp.
            if (mat.stats().meltingTemp() > 0f) continue;

            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (pt.syntheticCast()) continue;
                var di = SmitheryItems.getBuiltInPart(mat.id(), pt.id());
                if (di == null) continue;
                Item partItem = di.get();
                Identifier recipeId = Identifier.fromNamespaceAndPath(
                        Smithery.MODID,
                        "stonecutting/" + mat.id().getPath() + "_" + pt.id().getPath());

                SingleItemRecipeBuilder builder = SingleItemRecipeBuilder
                        .stonecutting(in.ingredient(this.registries), RecipeCategory.MISC, partItem, 1);
                if (in.tag() != null) {
                    builder.unlockedBy(in.unlockKey(), this.has(in.tag()));
                } else {
                    builder.unlockedBy(in.unlockKey(), this.has(in.items().getFirst()));
                }
                builder.save(this.output, ResourceKey.create(Registries.RECIPE, recipeId));
            }
        }
    }

    public static final class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup) {
            super(packOutput, lookup);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new SmitheryRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Smithery Recipes";
        }
    }
}
