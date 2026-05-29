package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.alloy.AlloyRecipe;
import com.soul.smithery.api.alloy.AlloyRecipes;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.api.part.PartEligibility;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tooltip handler that annotates player-facing items with their smithery-pipeline roles.
 *
 * <p>Adds two independent tooltip blocks: a "can be made into parts" list driven by
 * {@link PartEligibility}, and a "can be melted into" line (optionally followed by a "used in
 * alloys" list) driven by {@link MeltingRecipe} + {@link AlloyRecipe} lookups.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class MaterialSourceTooltipHandler {

    private MaterialSourceTooltipHandler() {}

    /**
     * Appends smithery part-source and melt-into tooltips to the hovered item's tooltip.
     *
     * @param event NeoForge's item-tooltip event whose stack is inspected
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        List<Component> tooltip = event.getToolTip();

        appendPartSourceTooltip(stack, tooltip);
        appendMeltingTooltip(stack, tooltip);
    }

    private static @Nullable Identifier resolveMaterialForTooltip(ItemStack stack) {
        if (stack.is(ItemTags.LOGS))         return SmitheryMaterials.WOOD;
        if (stack.is(Items.FLINT))           return SmitheryMaterials.FLINT;
        if (stack.is(Items.SLIME_BALL))      return SmitheryMaterials.SLIME;
        if (stack.is(Items.RESIN_CLUMP))     return SmitheryMaterials.RESIN;
        if (isCoralBlockItem(stack))         return SmitheryMaterials.CORAL;

        if (stack.is(Items.STRING))                              return SmitheryMaterials.STRING;
        if (stack.is(SmitheryItems.FLAMESTRING.get()))           return SmitheryMaterials.FLAMESTRING;
        if (stack.is(SmitheryItems.BREEZESTRING.get()))          return SmitheryMaterials.BREEZESTRING;
        if (stack.is(SmitheryItems.RED_SLIME.get()))             return SmitheryMaterials.RED_SLIME;
        if (stack.is(SmitheryItems.KELP_STRING.get()))           return SmitheryMaterials.KELP_STRING;
        return null;
    }

    private static boolean isCoralBlockItem(ItemStack stack) {
        Item it = stack.getItem();
        return it == Items.TUBE_CORAL_BLOCK    || it == Items.BRAIN_CORAL_BLOCK
            || it == Items.BUBBLE_CORAL_BLOCK  || it == Items.FIRE_CORAL_BLOCK
            || it == Items.HORN_CORAL_BLOCK
            || it == Items.DEAD_TUBE_CORAL_BLOCK    || it == Items.DEAD_BRAIN_CORAL_BLOCK
            || it == Items.DEAD_BUBBLE_CORAL_BLOCK  || it == Items.DEAD_FIRE_CORAL_BLOCK
            || it == Items.DEAD_HORN_CORAL_BLOCK;
    }

    private static void appendPartSourceTooltip(ItemStack stack, List<Component> tooltip) {
        Identifier matId = resolveMaterialForTooltip(stack);
        if (matId == null) return;

        List<PartType> eligible = new ArrayList<>();
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.syntheticCast()) continue;
            if (PartEligibility.isAllowed(pt.id(), matId)) eligible.add(pt);
        }
        if (eligible.isEmpty()) return;

        tooltip.add(Component.translatable("tooltip." + Smithery.MODID + ".source.parts_header")
                .withStyle(ChatFormatting.GRAY));
        for (PartType pt : eligible) {
            tooltip.add(Component.literal(" • ")
                    .append(Component.translatable(PartItem.partTranslationKey(pt.id())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendMeltingTooltip(ItemStack stack, List<Component> tooltip) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        MeltingRecipe recipe = SmitheryAPI.MELTING_RECIPES.get(itemId);
        if (recipe == null) return;

        Identifier outMat = recipe.outputMaterialId();
        tooltip.add(Component.translatable("tooltip." + Smithery.MODID + ".source.melt",
                Component.translatable(moltenFluidLangKey(outMat)))
                .withStyle(ChatFormatting.GRAY));

        Set<Identifier> producedAlloys = new LinkedHashSet<>();
        for (AlloyRecipe ar : AlloyRecipes.all()) {
            for (AlloyRecipe.Input in : ar.inputs()) {
                if (outMat.equals(in.material())) {
                    producedAlloys.add(ar.result().material());
                    break;
                }
            }
        }
        if (producedAlloys.isEmpty()) return;

        tooltip.add(Component.translatable("tooltip." + Smithery.MODID + ".source.alloys_header")
                .withStyle(ChatFormatting.GRAY));
        for (Identifier alloyMat : producedAlloys) {
            tooltip.add(Component.literal(" • ")
                    .append(Component.translatable(PartItem.materialTranslationKey(alloyMat)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String moltenFluidLangKey(Identifier materialId) {
        return "fluid." + materialId.getNamespace() + ".molten_" + materialId.getPath();
    }
}
