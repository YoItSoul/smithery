package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryFluids;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshots Smithery's three recipe domains into flat lists JEI can iterate:
 * melting (forge), casting (sand cast + molten fluid), and part-press (raw item → part).
 *
 * Built once at plugin registration time — all data lives in the Smithery API registries
 * by then. Each list is independent so JEI can register them as separate categories.
 */
public final class SmitheryJeiRecipes {

    /** One row per registered MeltingRecipe — the input item, the material it produces, and its melt temperature. */
    public record JeiMelting(ItemStack input, Material material, int outputMb, float meltingTempCelsius) {}

    /** One row per (material, non-synthetic part type) pair the player can cast. */
    public record JeiCasting(Material material, PartType partType, ItemStack castBlock, int castMb, ItemStack output) {}

    /** One row per (raw input item, non-synthetic part type) pair the part press can cut. */
    public record JeiPartPress(ItemStack input, Material material, PartType partType, ItemStack output) {}

    /**
     * One row per ToolType. Each slot holds every material variant of that slot's PartType, in
     * matched order with {@code tools}; the category focus-links the slots so cycling iterates
     * through (slot1[i], slot2[i], ..., tools[i]) as a coherent set. Equivalent to a "tag
     * recipe" — every input slot accepts any registered material's part.
     */
    public record JeiToolAssembly(ToolType toolType, List<List<ItemStack>> partsBySlot, List<ItemStack> tools) {}

    private SmitheryJeiRecipes() {}

    public static List<JeiMelting> buildMeltingRecipes() {
        List<JeiMelting> out = new ArrayList<>();
        for (MeltingRecipe recipe : SmitheryAPI.MELTING_RECIPES.values()) {
            Item item = BuiltInRegistries.ITEM.get(recipe.inputItemId())
                    .<Item>map(r -> r.value()).orElse(null);
            if (item == null || item == Items.AIR) continue;
            Material material = SmitheryAPI.MATERIALS.get(recipe.outputMaterialId());
            if (material == null) continue;
            // Skip materials without a molten fluid (meltingTemp <= 0 — e.g. wood).
            if (SmitheryFluids.forMaterial(material.id()) == null) continue;
            out.add(new JeiMelting(
                    new ItemStack(item),
                    material,
                    recipe.outputMb(),
                    material.stats().meltingTemp()));
        }
        return out;
    }

    public static List<JeiCasting> buildCastingRecipes() {
        List<JeiCasting> out = new ArrayList<>();
        for (Material material : SmitheryAPI.MATERIALS.all()) {
            SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(material.id());
            if (entry == null) continue; // material doesn't melt → can't be cast
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                ItemStack output = resolveCastOutput(material, pt);
                if (output.isEmpty()) continue;
                var castBlock = SmitheryBlocks.getImpressedSandItem(pt.id());
                if (castBlock == null) continue;
                out.add(new JeiCasting(
                        material,
                        pt,
                        new ItemStack(castBlock.get()),
                        pt.castMb(),
                        output));
            }
        }
        return out;
    }

    private static ItemStack resolveCastOutput(Material material, PartType partType) {
        // Synthetic cast targets (ingot/nugget/pearl) resolve through CastResults — they don't
        // produce smithery PartItems.
        if (partType.syntheticCast()) {
            Item result = CastResults.resolve(material.id(), partType.id());
            return result != null ? new ItemStack(result) : ItemStack.EMPTY;
        }
        if (material.stats().castOnly()) return ItemStack.EMPTY;
        if (!Smithery.MODID.equals(material.id().getNamespace())) return ItemStack.EMPTY;
        var di = SmitheryItems.getBuiltInPart(material.id(), partType.id());
        return di == null ? ItemStack.EMPTY : new ItemStack(di.get());
    }

    public static List<JeiPartPress> buildPartPressRecipes() {
        List<JeiPartPress> out = new ArrayList<>();
        // Inputs the press accepts. Mirrors PartPressBlockEntity.resolveMaterialFor.
        Map<Item, Identifier> simpleInputs = new LinkedHashMap<>();
        simpleInputs.put(Items.FLINT,       SmitheryMaterials.FLINT);
        simpleInputs.put(Items.SLIME_BALL,  SmitheryMaterials.SLIME);
        simpleInputs.put(Items.RESIN_CLUMP, SmitheryMaterials.RESIN);
        // Bowstring-class materials intentionally absent — bowstring PartItems are crafted
        // by hand via shaped recipes, not produced by the press.

        Item[] coralBlocks = new Item[] {
                Items.TUBE_CORAL_BLOCK, Items.BRAIN_CORAL_BLOCK, Items.BUBBLE_CORAL_BLOCK,
                Items.FIRE_CORAL_BLOCK, Items.HORN_CORAL_BLOCK,
                Items.DEAD_TUBE_CORAL_BLOCK, Items.DEAD_BRAIN_CORAL_BLOCK,
                Items.DEAD_BUBBLE_CORAL_BLOCK, Items.DEAD_FIRE_CORAL_BLOCK,
                Items.DEAD_HORN_CORAL_BLOCK,
        };

        // Logs — enumerate every item in the LOGS tag so JEI can show a concrete input for each.
        List<Item> logItems = new ArrayList<>();
        BuiltInRegistries.ITEM.getOrThrow(ItemTags.LOGS).forEach(holder -> logItems.add(holder.value()));

        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.syntheticCast()) continue;
            // Wood / logs.
            for (Item log : logItems) {
                addPressEntry(out, new ItemStack(log), SmitheryMaterials.WOOD, pt);
            }
            // Flint, slime, resin.
            for (var entry : simpleInputs.entrySet()) {
                addPressEntry(out, new ItemStack(entry.getKey()), entry.getValue(), pt);
            }
            // Coral.
            for (Item coral : coralBlocks) {
                addPressEntry(out, new ItemStack(coral), SmitheryMaterials.CORAL, pt);
            }
        }
        return out;
    }

    private static void addPressEntry(List<JeiPartPress> out, ItemStack input, Identifier materialId, PartType pt) {
        if (materialId == null) return;
        Material material = SmitheryAPI.MATERIALS.get(materialId);
        if (material == null) return;
        var di = SmitheryItems.getBuiltInPart(materialId, pt.id());
        if (di == null) return;
        out.add(new JeiPartPress(input, material, pt, new ItemStack(di.get())));
    }

    /**
     * Tool assembly recipes — one row per ToolType. Each slot lists all material variants of its
     * PartType in matched order with the output tools list, so the category can focus-link them
     * and cycle materials in sync.
     *
     * Skips materials that don't have all required PartItems (e.g. castOnly materials, or
     * non-smithery namespaces without registered parts).
     */
    public static List<JeiToolAssembly> buildToolAssemblyRecipes() {
        List<JeiToolAssembly> out = new ArrayList<>();
        for (ToolType toolType : SmitheryAPI.TOOL_TYPES.all()) {
            Item toolItem = BuiltInRegistries.ITEM.get(toolType.id())
                    .<Item>map(r -> r.value()).orElse(null);
            if (toolItem == null) continue;

            List<List<ItemStack>> partsBySlot = new ArrayList<>(toolType.slots().size());
            for (int i = 0; i < toolType.slots().size(); i++) partsBySlot.add(new ArrayList<>());
            List<ItemStack> tools = new ArrayList<>();

            for (Material material : SmitheryAPI.MATERIALS.all()) {
                if (material.stats().castOnly()) continue;
                if (!Smithery.MODID.equals(material.id().getNamespace())) continue;

                List<ItemStack> slotParts = new ArrayList<>(toolType.slots().size());
                boolean missingPart = false;
                for (var slot : toolType.slots()) {
                    var di = SmitheryItems.getBuiltInPart(material.id(), slot.partType().id());
                    if (di == null) { missingPart = true; break; }
                    slotParts.add(new ItemStack(di.get()));
                }
                if (missingPart) continue;

                ToolComposition comp = new ToolComposition(
                        toolType.id(),
                        toolType.slots().stream().map(s -> material.id()).toList());
                ItemStack tool = SmitheryToolItem.applyComposition(new ItemStack(toolItem), comp);

                for (int i = 0; i < slotParts.size(); i++) {
                    partsBySlot.get(i).add(slotParts.get(i));
                }
                tools.add(tool);
            }

            if (!tools.isEmpty()) {
                out.add(new JeiToolAssembly(toolType, partsBySlot, tools));
            }
        }
        return out;
    }
}
