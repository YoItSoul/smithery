package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.melting.MeltingRecipe;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.synergy.SynergyDefinition;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.content.SmitheryMaterials;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolCompositions;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryFluids;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshots Smithery's recipe domains into flat lists JEI can display.
 *
 * <p>Built once at plugin registration time — every Smithery registry is fully populated by
 * then — so each list can be registered independently as its own category.
 */
public final class SmitheryJeiRecipes {

    /**
     * One entry per melting recipe.
     *
     * @param input the input item stack
     * @param material output material
     * @param outputMb output amount in mB
     * @param meltingTempCelsius required forge temperature in degrees Celsius
     */
    public record JeiMelting(ItemStack input, Material material, int outputMb, float meltingTempCelsius) {}

    /**
     * One entry per (material, non-synthetic part type) pair the player can cast.
     *
     * @param material molten input material
     * @param partType cast shape
     * @param castBlock impressed sand block for the part type
     * @param castMb mB consumed per cast
     * @param output produced item
     */
    public record JeiCasting(Material material, PartType partType, ItemStack castBlock, int castMb, ItemStack output) {}

    /**
     * One entry per (raw input item, non-synthetic part type) pair the part press accepts.
     *
     * @param input raw input stack
     * @param material material the input maps to
     * @param partType pressed shape
     * @param output produced part item
     */
    public record JeiPartPress(ItemStack input, Material material, PartType partType, ItemStack output) {}

    /**
     * One entry per tool type, with every material variant per slot in matched order with
     * {@code tools}. JEI's cycler advances all slots in lockstep so each cycle reads as a
     * single all-material composition.
     *
     * @param toolType the tool type this entry covers
     * @param partsBySlot one list per tool slot, holding every material variant
     * @param tools per-material composed tool stacks, indexed parallel to {@code partsBySlot} entries
     */
    public record JeiToolAssembly(ToolType toolType, List<List<ItemStack>> partsBySlot, List<ItemStack> tools) {}

    /**
     * One entry per registered {@link Modifier}, gathering every way a player can obtain that
     * modifier on a Smithery tool. The category renders three rows for the three acquisition
     * paths so a glance answers "how do I get this on my tool?".
     *
     * @param modifier        the registered modifier definition
     * @param anvilSources    items placed in an anvil opposite a Smithery tool that apply this modifier
     * @param materialGrants  (material, tool type) pairs whose mere presence in the tool grants this modifier
     * @param synergies       two-material pairings (per tool type) whose co-presence grants this modifier
     */
    public record JeiModifier(
            Modifier modifier,
            List<JeiAnvilSource> anvilSources,
            List<JeiMaterialGrant> materialGrants,
            List<JeiSynergyGrant> synergies
    ) {}

    /**
     * Single anvil-source row entry. The item is the displayed/dropped stack; {@code unitValue}
     * tells the player how many "level units" each item contributes (block forms are usually 4 or
     * 9 to match crafting ratios).
     *
     * @param item       the item stack the player drops in the anvil
     * @param unitValue  units this item contributes per application toward {@link Modifier#levelCost}
     * @param effect     the resolved effect (carries any source-side parameter overrides)
     */
    public record JeiAnvilSource(ItemStack item, int unitValue, ModifierEffect effect) {}

    /**
     * One material → tool-type grant entry. Using {@code material} for any part of a {@code toolType}
     * automatically attaches this modifier when the tool is composed.
     *
     * @param material   the granting material
     * @param toolType   the tool type the grant fires on
     * @param effect     the resolved effect (carries the per-grant parameter overrides)
     * @param displayItem representative item shown in the JEI slot (tool-type-appropriate part)
     */
    public record JeiMaterialGrant(Material material, ToolType toolType, ModifierEffect effect, ItemStack displayItem) {}

    /**
     * One synergy → tool-type entry. Both {@code materialA} and {@code materialB} present anywhere
     * in the tool grants this modifier — no specific part-slot is required.
     *
     * @param synergy      the synergy definition
     * @param toolType     the tool type this synergy's effect applies to
     * @param effect       the resolved effect (carries the synergy's parameter overrides)
     * @param itemA        representative item for material A (vanilla ingot when available, else a part)
     * @param itemB        representative item for material B
     */
    public record JeiSynergyGrant(SynergyDefinition synergy, ToolType toolType, ModifierEffect effect,
                                  ItemStack itemA, ItemStack itemB) {}

    private SmitheryJeiRecipes() {}

    /**
     * Returns whether the given material is hidden from every JEI surface.
     *
     * <p>Hidden materials are excluded from these snapshot lists and their part items are pruned
     * from the ingredient sidebar by {@link SmitheryJeiPlugin#onRuntimeAvailable}. NeoForgium is
     * currently the only hidden material — discoverable only by experiment.
     *
     * @param materialId material identifier to check
     * @return {@code true} if the material should not appear anywhere in JEI
     */
    public static boolean isHiddenFromJei(net.minecraft.resources.ResourceLocation materialId) {
        if (materialId == null) return false;
        return Smithery.MODID.equals(materialId.getNamespace())
                && "neoforgium".equals(materialId.getPath());
    }

    /**
     * Builds the snapshot list of melting recipes, skipping hidden materials and materials
     * without a registered molten fluid.
     *
     * @return list of melting category rows
     */
    public static List<JeiMelting> buildMeltingRecipes() {
        List<JeiMelting> out = new ArrayList<>();
        for (MeltingRecipe recipe : SmitheryAPI.MELTING_RECIPES.values()) {
            if (isHiddenFromJei(recipe.outputMaterialId())) continue;
            Item item = BuiltInRegistries.ITEM.get(recipe.inputItemId());
            if (item == Items.AIR) continue;
            Material material = SmitheryAPI.MATERIALS.get(recipe.outputMaterialId());
            if (material == null) continue;
            if (SmitheryFluids.forMaterial(material.id()) == null) continue;
            out.add(new JeiMelting(
                    new ItemStack(item),
                    material,
                    recipe.outputMb(),
                    material.stats().meltingTemp()));
        }
        return out;
    }

    /**
     * Builds the snapshot list of casting recipes, covering every meltable material crossed
     * with every part type that has a defined cast output.
     *
     * @return list of casting category rows
     */
    public static List<JeiCasting> buildCastingRecipes() {
        List<JeiCasting> out = new ArrayList<>();
        for (Material material : SmitheryAPI.MATERIALS.all()) {
            if (isHiddenFromJei(material.id())) continue;
            SmitheryFluids.Entry entry = SmitheryFluids.forMaterial(material.id());
            if (entry == null) continue;
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
        if (partType.syntheticCast()) {
            Item result = CastResults.resolve(material.id(), partType.id());
            return result != null ? new ItemStack(result) : ItemStack.EMPTY;
        }
        if (material.stats().castOnly()) return ItemStack.EMPTY;
        var part = SmitheryItems.findPart(material.id(), partType.id());
        return part != null ? new ItemStack(part) : ItemStack.EMPTY;
    }

    /**
     * Builds the snapshot list of part-press recipes. Mirrors the input-acceptance set of
     * {@code PartPressBlockEntity} (logs, flint, slime, coral); each accepted input is
     * paired with every non-synthetic part type.
     *
     * @return list of part-press category rows
     */
    public static List<JeiPartPress> buildPartPressRecipes() {
        List<JeiPartPress> out = new ArrayList<>();
        Map<Item, ResourceLocation> simpleInputs = new LinkedHashMap<>();
        simpleInputs.put(Items.FLINT,       SmitheryMaterials.FLINT);
        simpleInputs.put(Items.SLIME_BALL,  SmitheryMaterials.SLIME);

        Item[] coralBlocks = new Item[] {
                Items.TUBE_CORAL_BLOCK, Items.BRAIN_CORAL_BLOCK, Items.BUBBLE_CORAL_BLOCK,
                Items.FIRE_CORAL_BLOCK, Items.HORN_CORAL_BLOCK,
                Items.DEAD_TUBE_CORAL_BLOCK, Items.DEAD_BRAIN_CORAL_BLOCK,
                Items.DEAD_BUBBLE_CORAL_BLOCK, Items.DEAD_FIRE_CORAL_BLOCK,
                Items.DEAD_HORN_CORAL_BLOCK,
        };

        List<Item> logItems = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOGS).forEach(holder -> logItems.add(holder.value()));

        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.syntheticCast()) continue;
            for (Item log : logItems) {
                addPressEntry(out, new ItemStack(log), SmitheryMaterials.WOOD, pt);
            }
            for (var entry : simpleInputs.entrySet()) {
                addPressEntry(out, new ItemStack(entry.getKey()), entry.getValue(), pt);
            }
            for (Item coral : coralBlocks) {
                addPressEntry(out, new ItemStack(coral), SmitheryMaterials.CORAL, pt);
            }
        }
        return out;
    }

    private static void addPressEntry(List<JeiPartPress> out, ItemStack input, ResourceLocation materialId, PartType pt) {
        if (materialId == null) return;
        Material material = SmitheryAPI.MATERIALS.get(materialId);
        if (material == null) return;
        var part = SmitheryItems.findPart(materialId, pt.id());
        if (part == null) return;
        out.add(new JeiPartPress(input, material, pt, new ItemStack(part)));
    }

    /**
     * Builds the snapshot list of tool assembly recipes — one row per tool type, with every
     * eligible material populating each slot in lockstep with the composed-tool output list.
     * Skips materials that cannot supply every required part, that are cast-only, or that are
     * hidden from JEI.
     *
     * @return list of tool-assembly category rows
     */
    public static List<JeiToolAssembly> buildToolAssemblyRecipes() {
        List<JeiToolAssembly> out = new ArrayList<>();
        for (ToolType toolType : SmitheryAPI.TOOL_TYPES.all()) {
            Item toolItem = BuiltInRegistries.ITEM.get(toolType.id());
            if (toolItem == Items.AIR) continue;

            List<List<ItemStack>> partsBySlot = new ArrayList<>(toolType.slots().size());
            for (int i = 0; i < toolType.slots().size(); i++) partsBySlot.add(new ArrayList<>());
            List<ItemStack> tools = new ArrayList<>();

            for (Material material : SmitheryAPI.MATERIALS.all()) {
                if (material.stats().castOnly()) continue;
                if (isHiddenFromJei(material.id())) continue;

                List<ItemStack> slotParts = new ArrayList<>(toolType.slots().size());
                boolean missingPart = false;
                for (var slot : toolType.slots()) {
                    var part = SmitheryItems.findPart(material.id(), slot.partType().id());
                    if (part == null) { missingPart = true; break; }
                    slotParts.add(new ItemStack(part));
                }
                if (missingPart) continue;

                ToolComposition comp = new ToolComposition(
                        toolType.id(),
                        toolType.slots().stream().map(s -> material.id()).toList());
                ItemStack tool = ToolCompositions.apply(new ItemStack(toolItem), comp);

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

    /**
     * Builds the snapshot list of modifier entries — one per registered {@link Modifier}, with
     * every anvil source / material grant / synergy that produces it gathered into a single row.
     *
     * <p>Modifiers with zero acquisition paths (e.g. internal debug entries) are still emitted so
     * their name and description surface in JEI; the category's three-row layout simply renders
     * empty for the missing paths.
     *
     * @return list of modifier category rows, one per registered modifier
     */
    public static List<JeiModifier> buildModifierRecipes() {
        List<JeiModifier> out = new ArrayList<>();
        for (Modifier modifier : SmitheryAPI.MODIFIERS.all()) {
            ResourceLocation modId = modifier.id();

            List<JeiAnvilSource> anvilSources = new ArrayList<>();
            for (Map.Entry<Item, ModifierSources.Entry> entry : ModifierSources.all().entrySet()) {
                ModifierSources.Entry source = entry.getValue();
                if (!modId.equals(source.effect().modifierId())) continue;
                anvilSources.add(new JeiAnvilSource(
                        new ItemStack(entry.getKey()),
                        source.unitValue(),
                        source.effect()));
            }

            List<JeiMaterialGrant> materialGrants = new ArrayList<>();
            for (Material material : SmitheryAPI.MATERIALS.all()) {
                if (isHiddenFromJei(material.id())) continue;
                if (material.stats().castOnly()) continue;
                for (ToolType toolType : SmitheryAPI.TOOL_TYPES.all()) {
                    for (ModifierEffect effect : material.stats().modifiersFor(toolType)) {
                        if (!modId.equals(effect.modifierId())) continue;
                        ItemStack display = representativeGrantItem(material, toolType);
                        materialGrants.add(new JeiMaterialGrant(material, toolType, effect, display));
                    }
                }
            }

            List<JeiSynergyGrant> synergies = new ArrayList<>();
            for (SynergyDefinition synergy : SmitheryAPI.SYNERGIES.all()) {
                for (Map.Entry<ResourceLocation, ModifierEffect> e : synergy.effectsPerToolType().entrySet()) {
                    ModifierEffect effect = e.getValue();
                    if (!modId.equals(effect.modifierId())) continue;
                    ToolType toolType = SmitheryAPI.TOOL_TYPES.get(e.getKey());
                    if (toolType == null) continue;
                    if (isHiddenFromJei(synergy.materialA()) || isHiddenFromJei(synergy.materialB())) continue;
                    ItemStack iconA = representativeMaterialItem(synergy.materialA());
                    ItemStack iconB = representativeMaterialItem(synergy.materialB());
                    if (iconA.isEmpty() || iconB.isEmpty()) continue;
                    synergies.add(new JeiSynergyGrant(synergy, toolType, effect, iconA, iconB));
                }
            }

            out.add(new JeiModifier(modifier, anvilSources, materialGrants, synergies));
        }
        return out;
    }

    /**
     * Picks the display item for a material × tool-type grant. The first ADDITIVE part slot of the
     * tool type is the natural visual — sword grants render their blade, pickaxe grants render the
     * head — and the same material's part item is looked up for that slot. Falls back to the
     * material's representative item if no part exists for the slot.
     */
    private static ItemStack representativeGrantItem(Material material, ToolType toolType) {
        PartType primary = null;
        for (ToolType.Slot s : toolType.slots()) {
            if (s.role() == DurabilityRole.ADDITIVE) { primary = s.partType(); break; }
        }
        if (primary != null) {
            var part = SmitheryItems.findPart(material.id(), primary.id());
            if (part != null) return new ItemStack(part);
        }
        return representativeMaterialItem(material.id());
    }

    /**
     * Picks a representative single-item stack for a material. Prefers the cast-result ingot when
     * registered (iron → iron ingot, copper → copper ingot, gold → gold ingot), then falls back to
     * the material's binder part item, then to any built-in part item. Returns {@link ItemStack#EMPTY}
     * if nothing usable exists.
     */
    private static ItemStack representativeMaterialItem(ResourceLocation materialId) {
        Item ingot = CastResults.resolve(materialId, SmitheryPartTypes.INGOT.id());
        if (ingot != null) return new ItemStack(ingot);
        var binder = SmitheryItems.findPart(materialId, SmitheryPartTypes.BINDER.id());
        if (binder != null) return new ItemStack(binder);
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            var part = SmitheryItems.findPart(materialId, pt.id());
            if (part != null) return new ItemStack(part);
        }
        return ItemStack.EMPTY;
    }
}
