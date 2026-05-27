package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryToolTypes;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryArrowItem;
import com.soul.smithery.item.tool.SmitheryBowItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item registration root for Smithery-namespaced items.
 *
 * Auto-generates a PartItem for every (Material × PartType) pair where both IDs are in
 * the smithery: namespace. Modder mods that add materials in their own namespace should
 * use their own DeferredRegister.Items and call {@link #registerPartsFor} from their
 * mod constructor.
 */
public final class SmitheryItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Smithery.MODID);

    /** Lookup: "<material_path>_<part_path>" → DeferredItem. Only populated for built-in parts. */
    private static final Map<String, DeferredItem<PartItem>> BUILT_IN_PART_ITEMS = new LinkedHashMap<>();

    // Tools. Single Item instance per ToolType; per-stack data lives in ToolComposition.
    public static final DeferredItem<SmitheryToolItem> SWORD = ITEMS.registerItem("sword",
            props -> new SmitheryToolItem(props, SmitheryToolTypes.SWORD.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    public static final DeferredItem<SmitheryToolItem> PICKAXE = ITEMS.registerItem("pickaxe",
            props -> new SmitheryToolItem(props, SmitheryToolTypes.PICKAXE.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    public static final DeferredItem<SmitheryToolItem> AXE = ITEMS.registerItem("axe",
            props -> new SmitheryToolItem(props, SmitheryToolTypes.AXE.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    public static final DeferredItem<SmitheryToolItem> SHOVEL = ITEMS.registerItem("shovel",
            props -> new SmitheryToolItem(props, SmitheryToolTypes.SHOVEL.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    public static final DeferredItem<SmitheryToolItem> HOE = ITEMS.registerItem("hoe",
            props -> new SmitheryToolItem(props, SmitheryToolTypes.HOE.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    public static final DeferredItem<SmitheryToolItem> SPEAR = ITEMS.registerItem("spear",
            props -> new SmitheryToolItem(props, SmitheryToolTypes.SPEAR.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    public static final DeferredItem<SmitheryBowItem> BOW = ITEMS.registerItem("bow",
            props -> new SmitheryBowItem(props, SmitheryToolTypes.BOW.id()),
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>) (p -> p.stacksTo(1)));

    // Arrow stays stackable (default vanilla 64). Damage/durability components are intentionally
    // not written by applyComposition for arrows — see the early-return in SmitheryToolItem —
    // because vanilla forbids stackable items from carrying DAMAGE.
    public static final DeferredItem<SmitheryArrowItem> ARROW = ITEMS.registerItem("arrow",
            props -> new SmitheryArrowItem(props, SmitheryToolTypes.ARROW.id()));

    // ─── Bowstring-class crafting items ───
    //
    // Each of these is a player-facing crafted item that the part press converts to its
    // material id (FLAMESTRING / BREEZESTRING / RED_SLIME / KELP_STRING). They're simple
    // ItemStack carriers — no per-stack data — and stack like any other resource. Recipes
    // live under data/smithery/recipe/.
    //
    // Uses {@code registerItem(...)} (not {@code register(...)}) — the latter doesn't propagate
    // the item-id into Properties, which trips {@code NullPointerException: Item id not set}
    // during the RegisterEvent in 26.1.x. The (props -> new Item(props)) form lets the helper
    // stamp the ID before the Item ctor runs.
    public static final DeferredItem<net.minecraft.world.item.Item> FLAMESTRING =
            ITEMS.registerItem("flamestring", net.minecraft.world.item.Item::new);
    public static final DeferredItem<net.minecraft.world.item.Item> BREEZESTRING =
            ITEMS.registerItem("breezestring", net.minecraft.world.item.Item::new);
    public static final DeferredItem<net.minecraft.world.item.Item> RED_SLIME =
            ITEMS.registerItem("red_slime", net.minecraft.world.item.Item::new);
    public static final DeferredItem<net.minecraft.world.item.Item> KELP_STRING =
            ITEMS.registerItem("kelp_string", net.minecraft.world.item.Item::new);

    // Unfinished kelp string: carries its weave progress as a durability bar. Each
    // progressive craft (4 kelp + 4 string + this in the center of a 3×3) hurts the stack
    // by 1, displaying as progress on the bar. When DAMAGE would exceed MAX_DAMAGE the
    // KelpStringProgressRecipe outputs a finished KELP_STRING instead. Combining two
    // unfinished stacks sums their progress via the dedicated combine recipe.
    //
    // MAX_DAMAGE = 4 represents the 4-step craft. Fresh stack starts at DAMAGE=3 (one
    // increment of progress already baked in by the very first recipe), so the visible bar
    // reads "1/4 progress" right after the initial craft.
    public static final int UNFINISHED_KELP_STRING_STEPS = 4;
    public static final DeferredItem<net.minecraft.world.item.Item> UNFINISHED_KELP_STRING =
            ITEMS.registerItem("unfinished_kelp_string",
                    net.minecraft.world.item.Item::new,
                    (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>)
                            (p -> p.stacksTo(1).durability(UNFINISHED_KELP_STRING_STEPS)));

    /**
     * Iterate all currently-registered Materials × PartTypes (both in the smithery: namespace)
     * and queue one PartItem registration per pair. Must run AFTER materials and part types
     * have been registered into SmitheryAPI but BEFORE the mod event bus receives our ITEMS
     * register call (which happens in Smithery's constructor).
     */
    public static void registerBuiltInParts() {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            if (!Smithery.MODID.equals(mat.id().getNamespace())) continue;
            // Cast-only materials (e.g. ender) exist in fluid form only — they don't
            // produce smithery PartItems. Their cast outputs are wired via CastResults.
            if (mat.stats().castOnly()) continue;
            registerPartsFor(mat.id(), ITEMS);
        }
    }

    /**
     * Modder-facing helper. Registers one PartItem per registered PartType for the given
     * material, using the supplied DeferredRegister.Items.
     *
     * Item path format: "<material_path>_<part_path>". Items are placed in whichever namespace
     * the supplied DeferredRegister.Items uses.
     */
    public static void registerPartsFor(Identifier materialId, DeferredRegister.Items targetItems) {
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            // Synthetic cast targets (ingot/nugget/pearl/etc) don't produce smithery
            // PartItems — their cast outcome resolves through CastResults to whatever
            // item the modder registered (typically vanilla or another mod's item).
            // The impression sand block variant still gets created.
            if (pt.syntheticCast()) continue;
            // Part-eligibility allow-list (PartEligibility) gates which materials produce a
            // PartItem for restricted parts (e.g. bowstring). Unrestricted parts pass through;
            // restricted parts only emit items for whitelisted materials. The check uses the
            // CODE_REGISTRY only here because data-pack entries (DATA_REGISTRY) don't exist
            // yet at deferred-register time — they're loaded on /reload, well after item
            // registration. This means data packs cannot dynamically introduce new (material
            // × restricted part) PartItems; they can only constrain further or rely on the
            // built-in code allow-list.
            if (!com.soul.smithery.api.part.PartEligibility.isAllowed(pt.id(), materialId)) continue;
            Identifier ptId = pt.id();
            String itemPath = materialId.getPath() + "_" + ptId.getPath();
            DeferredItem<PartItem> di = targetItems.registerItem(
                    itemPath,
                    props -> new PartItem(props, materialId, ptId)
            );
            if (targetItems == ITEMS) {
                BUILT_IN_PART_ITEMS.put(itemPath, di);
            }
        }
    }

    /** Lookup a built-in PartItem by material + part type. Returns null for non-built-in materials. */
    public static DeferredItem<PartItem> getBuiltInPart(Identifier materialId, Identifier partTypeId) {
        return BUILT_IN_PART_ITEMS.get(materialId.getPath() + "_" + partTypeId.getPath());
    }

    public static Map<String, DeferredItem<PartItem>> builtInParts() {
        return java.util.Collections.unmodifiableMap(BUILT_IN_PART_ITEMS);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private SmitheryItems() {}
}
