package com.soul.smithery.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.Smithery;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shapeless recipe that combines two or more {@link SmitheryItems#UNFINISHED_KELP_STRING}
 * stacks into one whose progress is the sum of contributors'. If the summed progress hits
 * the max (4), the output is a finished {@link SmitheryItems#KELP_STRING} instead.
 *
 * <p>"Progress" lives as the inverse of DAMAGE: damage 0 = 4/4, damage 3 = 1/4. So combining
 * two damaged-3 stacks (1 progress each) yields a damage-2 stack (2 progress).
 *
 * <p>Counted in progress space — combine math: {@code summedProgress = Σ (maxDamage - damage_i)}.
 * If {@code summedProgress >= maxDamage}, emit finished item. Otherwise emit unfinished at
 * {@code damage = maxDamage - summedProgress}.
 */
public final class KelpStringCombineRecipe implements CraftingRecipe {

    private final String group;

    public KelpStringCombineRecipe(String group) { this.group = group; }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int unfinishedSeen = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() != SmitheryItems.UNFINISHED_KELP_STRING.get()) return false;
            unfinishedSeen++;
        }
        // At least two unfinished — otherwise this is just identity and would conflict with
        // simple pickup. A single unfinished in the grid doesn't trigger the combine.
        return unfinishedSeen >= 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        int maxDamage = SmitheryItems.UNFINISHED_KELP_STRING_STEPS;
        int summedProgress = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() != SmitheryItems.UNFINISHED_KELP_STRING.get()) continue;
            int damage = s.getOrDefault(DataComponents.DAMAGE, 0);
            summedProgress += Math.max(0, maxDamage - damage);
        }
        if (summedProgress <= 0) return ItemStack.EMPTY;
        if (summedProgress >= maxDamage) {
            return new ItemStack(SmitheryItems.KELP_STRING.get());
        }
        ItemStack out = new ItemStack(SmitheryItems.UNFINISHED_KELP_STRING.get());
        out.set(DataComponents.DAMAGE, maxDamage - summedProgress);
        return out;
    }

    @Override
    public boolean showNotification() { return true; }

    @Override
    public String group() { return group; }

    @Override
    public CraftingBookCategory category() { return CraftingBookCategory.MISC; }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return SERIALIZER; }

    @Override
    public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }

    @Override
    public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    // ---- Serializer ----

    private static final MapCodec<KelpStringCombineRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(KelpStringCombineRecipe::group)
    ).apply(i, KelpStringCombineRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, KelpStringCombineRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, KelpStringCombineRecipe::group,
                    KelpStringCombineRecipe::new);

    public static final RecipeSerializer<KelpStringCombineRecipe> SERIALIZER =
            new RecipeSerializer<>(CODEC, STREAM_CODEC);

    public static Identifier serializerId() {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, "kelp_string_combine");
    }
}
