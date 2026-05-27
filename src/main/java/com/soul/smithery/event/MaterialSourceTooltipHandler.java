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
 * Adds smithery-context tooltips to player-facing items that have a role in the smithery
 * pipeline. Two independent tooltip blocks:
 *
 * <ol>
 *   <li><b>"Can be made into parts: …"</b> — appears on any item that maps to a smithery
 *       material (via part press input OR via a hand-craft bowstring source). Lists every
 *       part type the material is eligible for, after applying both sides of
 *       {@link PartEligibility} restrictions.</li>
 *   <li><b>"Can be melted into: &lt;molten name&gt;"</b> (+ optional "Used in alloys: …" line)
 *       — appears on any item that's a {@link MeltingRecipe} input. The alloy line is added
 *       only when the resulting material is the input side of one or more registered
 *       {@link AlloyRecipe} entries.</li>
 * </ol>
 *
 * <p>Lookup is cheap-ish: melting is an O(1) map hit; alloys iterate the (small) registered
 * recipe set; parts iterate the (small) PartType list per resolved material. Tooltip events
 * fire only when an item is hovered, so the per-hover cost is fine without caching.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class MaterialSourceTooltipHandler {

    private MaterialSourceTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        List<Component> tooltip = event.getToolTip();

        appendPartSourceTooltip(stack, tooltip);
        appendMeltingTooltip(stack, tooltip);
    }

    // ---------------------------------------------------------------------
    //  "Can be made into parts: ..."
    // ---------------------------------------------------------------------

    /**
     * Resolves the smithery material this item is a source for, or null if it's not a
     * recognised smithery source. Mirrors {@code PartPressBlockEntity.resolveMaterialFor}
     * for press inputs and adds the bowstring hand-craft sources.
     */
    private static @Nullable Identifier resolveMaterialForTooltip(ItemStack stack) {
        if (stack.is(ItemTags.LOGS))         return SmitheryMaterials.WOOD;
        if (stack.is(Items.FLINT))           return SmitheryMaterials.FLINT;
        if (stack.is(Items.SLIME_BALL))      return SmitheryMaterials.SLIME;
        if (stack.is(Items.RESIN_CLUMP))     return SmitheryMaterials.RESIN;
        if (isCoralBlockItem(stack))         return SmitheryMaterials.CORAL;

        // Bowstring hand-craft sources. The 1:1 shaped recipes in data/smithery/recipe/
        // turn each of these into the corresponding bowstring PartItem.
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

        // Walk every registered PartType and keep the ones where (part × material) is allowed
        // by PartEligibility. Synthetic-cast parts (ingot / nugget) are skipped — those are
        // already "raw resource" outputs and don't make sense in a "made into parts" list.
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

    // ---------------------------------------------------------------------
    //  "Can be melted into: ..." + "Used in alloys: ..."
    // ---------------------------------------------------------------------

    private static void appendMeltingTooltip(ItemStack stack, List<Component> tooltip) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        MeltingRecipe recipe = SmitheryAPI.MELTING_RECIPES.get(itemId);
        if (recipe == null) return;

        Identifier outMat = recipe.outputMaterialId();
        // "Can be melted into: <molten material name>"
        tooltip.add(Component.translatable("tooltip." + Smithery.MODID + ".source.melt",
                Component.translatable(moltenFluidLangKey(outMat)))
                .withStyle(ChatFormatting.GRAY));

        // Alloys: any registered alloy that takes outMat as one of its inputs.
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

    /** Lang key for the molten fluid label that matches the SmitheryFluids registration. */
    private static String moltenFluidLangKey(Identifier materialId) {
        return "fluid." + materialId.getNamespace() + ".molten_" + materialId.getPath();
    }
}
