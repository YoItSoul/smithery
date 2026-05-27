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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Progressive shaped recipe for Kelp String. Matches a 3×3 with:
 *   K S K
 *   S ? S
 *   K S K
 * where {@code K} = kelp, {@code S} = vanilla string, and {@code ?} is either:
 *   - <b>empty</b> — first weave step. Output: an unfinished_kelp_string at the lowest
 *     progress tier (DAMAGE = MAX_DAMAGE - 1, i.e. one increment of "1/4 progress" baked in).
 *   - <b>an unfinished_kelp_string</b> — progress increment. Output: the same item with
 *     {@code DAMAGE -= 1} (one more pip of progress). When DAMAGE would drop below 0, the
 *     output upgrades to a finished {@link SmitheryItems#KELP_STRING}.
 *
 * <p>Why a custom recipe instead of a vanilla {@code crafting_shaped} per state: vanilla
 * recipes can't read the input's DAMAGE component and emit a stateful follow-up. We need
 * exactly that — one recipe pattern, output depends on the center item's progress.
 *
 * <p>Why not a smithing recipe or anvil: those have UI mismatches with the "place around a
 * partial" mental model the user described. Crafting table 3×3 is the right fit.
 */
public final class KelpStringProgressRecipe implements CraftingRecipe {

    private final String group;

    public KelpStringProgressRecipe(String group) { this.group = group; }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) return false;
        // Corners are kelp.
        if (!isKelp(input.getItem(0, 0))) return false;
        if (!isKelp(input.getItem(2, 0))) return false;
        if (!isKelp(input.getItem(0, 2))) return false;
        if (!isKelp(input.getItem(2, 2))) return false;
        // Edges (non-corner perimeter) are string.
        if (!isString(input.getItem(1, 0))) return false;
        if (!isString(input.getItem(0, 1))) return false;
        if (!isString(input.getItem(2, 1))) return false;
        if (!isString(input.getItem(1, 2))) return false;
        // Center is either empty (first weave) or an unfinished kelp string (advance).
        ItemStack center = input.getItem(1, 1);
        if (center.isEmpty()) return true;
        return center.getItem() == SmitheryItems.UNFINISHED_KELP_STRING.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack center = input.getItem(1, 1);
        int maxDamage = SmitheryItems.UNFINISHED_KELP_STRING_STEPS;

        if (center.isEmpty()) {
            // First weave — fresh unfinished stack at "1/4" progress (DAMAGE = maxDamage - 1).
            ItemStack fresh = new ItemStack(SmitheryItems.UNFINISHED_KELP_STRING.get());
            fresh.set(DataComponents.DAMAGE, maxDamage - 1);
            return fresh;
        }

        // Advance: read existing progress, increment, return either a more-progressed
        // unfinished stack or a finished kelp_string.
        int currentDamage = center.getOrDefault(DataComponents.DAMAGE, 0);
        int newDamage = currentDamage - 1;        // damage 0 = full (4/4); higher damage = earlier
        if (newDamage < 0) {
            // Already at 4/4 — output the finished item. (Defensive: matches() lets fully
            // progressed unfinished stacks through, in which case the player gets their
            // finished kelp string and the additional kelp+string are consumed as the
            // upgrade cost — acceptable, matches the user's "do the entire process" intent.)
            return new ItemStack(SmitheryItems.KELP_STRING.get());
        }
        if (newDamage == 0) {
            // Final step completed — emit the finished item rather than a 4/4 unfinished.
            return new ItemStack(SmitheryItems.KELP_STRING.get());
        }
        ItemStack out = center.copyWithCount(1);
        out.set(DataComponents.DAMAGE, newDamage);
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

    private static boolean isKelp(ItemStack s) { return s.getItem() == Items.KELP; }
    private static boolean isString(ItemStack s) { return s.getItem() == Items.STRING; }

    // ---- Serializer ----

    private static final MapCodec<KelpStringProgressRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(KelpStringProgressRecipe::group)
    ).apply(i, KelpStringProgressRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, KelpStringProgressRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, KelpStringProgressRecipe::group,
                    KelpStringProgressRecipe::new);

    public static final RecipeSerializer<KelpStringProgressRecipe> SERIALIZER =
            new RecipeSerializer<>(CODEC, STREAM_CODEC);

    public static Identifier serializerId() {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, "kelp_string_progress");
    }
}
