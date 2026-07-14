package com.soul.smithery.item.tool;

import com.google.gson.JsonObject;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

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

    private final ResourceLocation id;
    private final ResourceLocation toolTypeId;
    private final String group;

    /**
     * Constructs the recipe for a given tool type id and crafting-book group.
     */
    public ToolAssemblyRecipe(ResourceLocation id, ResourceLocation toolTypeId, String group) {
        this.id = id;
        this.toolTypeId = toolTypeId;
        this.group = group;
    }

    /** Returns the bound ToolType id. */
    public ResourceLocation toolTypeId() { return toolTypeId; }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        ToolType tt = SmitheryAPI.TOOL_TYPES.get(toolTypeId);
        if (tt == null) return false;

        Map<ResourceLocation, Integer> required = new HashMap<>();
        for (ToolType.Slot slot : tt.slots()) {
            required.merge(slot.partType().id(), 1, Integer::sum);
        }

        Map<ResourceLocation, Integer> provided = new HashMap<>();
        int nonEmpty = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
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
    public ItemStack assemble(CraftingContainer input, RegistryAccess registryAccess) {
        ToolType tt = SmitheryAPI.TOOL_TYPES.get(toolTypeId);
        if (tt == null) return ItemStack.EMPTY;

        boolean[] used = new boolean[input.getContainerSize()];
        List<ResourceLocation> materials = new ArrayList<>(tt.slots().size());
        for (ToolType.Slot slot : tt.slots()) {
            ResourceLocation matched = null;
            for (int i = 0; i < input.getContainerSize(); i++) {
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

        Item resultItem = ForgeRegistries.ITEMS.getValue(toolTypeId);
        if (resultItem == null || resultItem == Items.AIR) return ItemStack.EMPTY;

        ToolComposition comp = new ToolComposition(toolTypeId, materials);
        return ToolCompositions.apply(new ItemStack(resultItem), comp);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The preview result depends on the actual part inputs, so the static result is empty;
     * JEI integration surfaces representative material combinations instead.
     */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        ToolType tt = SmitheryAPI.TOOL_TYPES.get(toolTypeId);
        return tt != null && width * height >= tt.slots().size();
    }

    @Override
    public boolean showNotification() { return true; }

    @Override
    public String getGroup() { return group; }

    @Override
    public CraftingBookCategory category() { return CraftingBookCategory.EQUIPMENT; }

    @Override
    public RecipeSerializer<?> getSerializer() { return SERIALIZER; }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        return NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
    }

    /** Recipe serializer, registered into the recipe-serializer registry. */
    public static final RecipeSerializer<ToolAssemblyRecipe> SERIALIZER = new Serializer();

    /**
     * 1.20.1-style serializer: hand-written JSON parse plus symmetric network round-trip
     * (this recipe is fully described by its tool-type id and group).
     */
    private static final class Serializer implements RecipeSerializer<ToolAssemblyRecipe> {
        @Override
        public ToolAssemblyRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            ResourceLocation toolTypeId = new ResourceLocation(GsonHelper.getAsString(json, "tool_type"));
            String group = GsonHelper.getAsString(json, "group", "");
            return new ToolAssemblyRecipe(recipeId, toolTypeId, group);
        }

        @Override
        public ToolAssemblyRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buf) {
            ResourceLocation toolTypeId = buf.readResourceLocation();
            String group = buf.readUtf();
            return new ToolAssemblyRecipe(recipeId, toolTypeId, group);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ToolAssemblyRecipe recipe) {
            buf.writeResourceLocation(recipe.toolTypeId);
            buf.writeUtf(recipe.group);
        }
    }

    /** Returns the fixed {@code smithery:tool_assembly} serializer id. */
    public static ResourceLocation serializerId() {
        return new ResourceLocation(Smithery.MODID, "tool_assembly");
    }
}
