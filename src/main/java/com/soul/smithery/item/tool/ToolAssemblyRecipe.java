package com.soul.smithery.item.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapeless crafting recipe that assembles a {@link SmitheryToolItem} from
 * {@link PartItem} inputs. Required PartType counts are derived from the
 * {@link ToolType}'s slot list, so the JSON only needs to name the tool; inputs are
 * matched as a multiset of PartTypes and the resulting tool's {@link ToolComposition}
 * is built by walking slot order and pulling each matched input's material.
 */
public final class ToolAssemblyRecipe implements CraftingRecipe {

    private final ResourceLocation toolTypeId;
    private final String group;

    /**
     * Constructs the recipe for a given tool type id and crafting-book group.
     */
    public ToolAssemblyRecipe(ResourceLocation toolTypeId, String group) {
        this.toolTypeId = toolTypeId;
        this.group = group;
    }

    /** Returns the bound ToolType id. */
    public ResourceLocation toolTypeId() { return toolTypeId; }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ToolType tt = SmitheryAPI.TOOL_TYPES.get(toolTypeId);
        if (tt == null) return false;

        Map<ResourceLocation, Integer> required = new HashMap<>();
        for (ToolType.Slot slot : tt.slots()) {
            required.merge(slot.partType().id(), 1, Integer::sum);
        }

        Map<ResourceLocation, Integer> provided = new HashMap<>();
        int nonEmpty = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            nonEmpty++;
            if (!(stack.getItem() instanceof PartItem partItem)) return false;
            if (stack.getCount() != 1) return false;
            provided.merge(partItem.partTypeId(), 1, Integer::sum);
        }

        if (nonEmpty != tt.slots().size()) return false;
        return required.equals(provided);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ToolType tt = SmitheryAPI.TOOL_TYPES.get(toolTypeId);
        if (tt == null) return ItemStack.EMPTY;

        boolean[] used = new boolean[input.size()];
        List<ResourceLocation> materials = new ArrayList<>(tt.slots().size());
        for (ToolType.Slot slot : tt.slots()) {
            ResourceLocation matched = null;
            for (int i = 0; i < input.size(); i++) {
                if (used[i]) continue;
                ItemStack stack = input.getItem(i);
                if (stack.isEmpty()) continue;
                if (!(stack.getItem() instanceof PartItem partItem)) continue;
                if (!partItem.partTypeId().equals(slot.partType().id())) continue;
                matched = partItem.materialId();
                used[i] = true;
                break;
            }
            if (matched == null) return ItemStack.EMPTY;
            materials.add(matched);
        }

        Item resultItem = BuiltInRegistries.ITEM.get(toolTypeId);
        if (resultItem == null) return ItemStack.EMPTY;

        ToolComposition comp = new ToolComposition(toolTypeId, materials);
        return ToolCompositions.apply(new ItemStack(resultItem), comp);
    }

    @Override
    public boolean showNotification() { return true; }

    @Override
    public String group() { return group; }

    @Override
    public CraftingBookCategory category() { return CraftingBookCategory.EQUIPMENT; }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return SERIALIZER; }

    @Override
    public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }

    @Override
    public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_EQUIPMENT; }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    private static final MapCodec<ToolAssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tool_type").forGetter(ToolAssemblyRecipe::toolTypeId),
            Codec.STRING.optionalFieldOf("group", "").forGetter(ToolAssemblyRecipe::group)
    ).apply(i, ToolAssemblyRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ToolAssemblyRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ToolAssemblyRecipe::toolTypeId,
                    ByteBufCodecs.STRING_UTF8, ToolAssemblyRecipe::group,
                    ToolAssemblyRecipe::new
            );

    /** Recipe serializer, registered into the recipe-serializer registry. */
    public static final RecipeSerializer<ToolAssemblyRecipe> SERIALIZER =
            new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** Returns the fixed {@code smithery:tool_assembly} serializer id. */
    public static ResourceLocation serializerId() {
        return new ResourceLocation(Smithery.MODID, "tool_assembly");
    }
}
