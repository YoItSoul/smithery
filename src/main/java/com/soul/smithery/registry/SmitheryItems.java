package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartEligibility;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryArrowItem;
import com.soul.smithery.item.tool.SmitheryBowItem;
import com.soul.smithery.item.tool.SmitheryCrossbowItem;
import com.soul.smithery.item.tool.SmitheryShurikenItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.SmitheryTridentItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item registration root for Smithery-namespaced items.
 *
 * <p>Auto-generates one {@link PartItem} per (Material × PartType) pair in the
 * {@code smithery:} namespace. Mods that contribute their own materials should call
 * {@link #registerPartsFor(ResourceLocation, DeferredRegister)} from their constructor with
 * their own item register.
 */
public final class SmitheryItems {
    /** Deferred register for Smithery-namespaced items. */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Smithery.MODID);

    private static final Map<String, RegistryObject<PartItem>> BUILT_IN_PART_ITEMS = new LinkedHashMap<>();

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Smithery.MODID, path);
    }

    /**
     * Registers a non-stacking {@link SmitheryToolItem} whose item name doubles as its
     * ToolType path — the invariant the assembly recipe's item lookup relies on.
     *
     * <p>The 1-point durability is a damageable-item placeholder: the real composed value is
     * served by the item's stack-sensitive {@code getMaxDamage} override, and being damageable
     * is what keeps the tool unstackable, exactly like vanilla tools.
     */
    private static RegistryObject<SmitheryToolItem> registerTool(String name) {
        return ITEMS.register(name,
                () -> new SmitheryToolItem(new Item.Properties().durability(1), id(name)));
    }

    /**
     * Registers a {@link SmitheryArmorItem} for the given armor slot. Per-material color and
     * per-stack defense values arrive via the stack's NBT, written at compose time.
     */
    private static RegistryObject<SmitheryArmorItem> registerArmor(String name, ArmorItem.Type type) {
        return ITEMS.register(name,
                () -> new SmitheryArmorItem(type, new Item.Properties(), id(name)));
    }

    /** Smithery sword tool item; per-stack composition lives in the stack's {@code tool_composition} NBT. */
    public static final RegistryObject<SmitheryToolItem> SWORD = registerTool("sword");
    /** Smithery pickaxe tool item; per-stack composition lives in the stack's {@code tool_composition} NBT. */
    public static final RegistryObject<SmitheryToolItem> PICKAXE = registerTool("pickaxe");
    /** Smithery axe tool item; per-stack composition lives in the stack's {@code tool_composition} NBT. */
    public static final RegistryObject<SmitheryToolItem> AXE = registerTool("axe");
    /** Smithery shovel tool item; per-stack composition lives in the stack's {@code tool_composition} NBT. */
    public static final RegistryObject<SmitheryToolItem> SHOVEL = registerTool("shovel");
    /** Smithery hoe tool item; per-stack composition lives in the stack's {@code tool_composition} NBT. */
    public static final RegistryObject<SmitheryToolItem> HOE = registerTool("hoe");
    /** Smithery spear tool item; per-stack composition lives in the stack's {@code tool_composition} NBT. */
    public static final RegistryObject<SmitheryToolItem> SPEAR = registerTool("spear");
    /** Smithery broadsword; heavy two-hand blade — slow swing, big hits. */
    public static final RegistryObject<SmitheryToolItem> BROADSWORD = registerTool("broadsword");
    /** Smithery rapier; fast thrusts that partially bypass the target's armor. */
    public static final RegistryObject<SmitheryToolItem> RAPIER = registerTool("rapier");
    /** Smithery paxel; pickaxe + axe + shovel + hoe in one head assembly. */
    public static final RegistryObject<SmitheryToolItem> PAXEL = registerTool("paxel");
    /** Smithery mining hammer; breaks a 3x3 face of stone at once. */
    public static final RegistryObject<SmitheryToolItem> MINING_HAMMER = registerTool("mining_hammer");

    /** Smithery bow tool item; uses a {@link SmitheryBowItem} for draw-frame model swaps. */
    public static final RegistryObject<SmitheryBowItem> BOW = ITEMS.register("bow",
            () -> new SmitheryBowItem(new Item.Properties().durability(1), id("bow")));

    /** Smithery kama; shears abilities plus 3x3 double-yield crop harvesting. */
    public static final RegistryObject<SmitheryToolItem> KAMA = registerTool("kama");
    public static final RegistryObject<SmitheryToolItem> SCYTHE = registerTool("scythe");
    public static final RegistryObject<SmitheryToolItem> SCEPTRE = registerTool("sceptre");
    /** Smithery cleaver; slow, massive hits with an innate chance to behead. */
    public static final RegistryObject<SmitheryToolItem> CLEAVER = registerTool("cleaver");
    /** Smithery lumberaxe; fells the whole connected tree from one log. */
    public static final RegistryObject<SmitheryToolItem> LUMBERAXE = registerTool("lumberaxe");
    /** Smithery excavator; digs a 3x3 face of shovel material at once. */
    public static final RegistryObject<SmitheryToolItem> EXCAVATOR = registerTool("excavator");

    /** Smithery shuriken; stackable thrown weapon assembled from four blades. */
    public static final RegistryObject<SmitheryShurikenItem> SHURIKEN = ITEMS.register("shuriken",
            () -> new SmitheryShurikenItem(new Item.Properties(), id("shuriken")));

    /** Smithery battlesign; a melee weapon that partially blocks while raised. */
    public static final RegistryObject<SmitheryToolItem> BATTLESIGN = registerTool("battlesign");

    /** Smithery trident; vanilla charge/throw pipeline, composition-driven melee stats. */
    public static final RegistryObject<SmitheryTridentItem> TRIDENT = ITEMS.register("trident",
            () -> new SmitheryTridentItem(new Item.Properties().durability(1), id("trident")));

    /** Smithery crossbow; vanilla charge/store/fire pipeline with smithery arrow scaling. */
    public static final RegistryObject<SmitheryCrossbowItem> CROSSBOW = ITEMS.register("crossbow",
            () -> new SmitheryCrossbowItem(new Item.Properties().durability(1), id("crossbow")));

    /** Smithery arrow tool item; stays stackable (default 64) since vanilla forbids stackables from taking damage. */
    public static final RegistryObject<SmitheryArrowItem> ARROW = ITEMS.register("arrow",
            () -> new SmitheryArrowItem(new Item.Properties(), id("arrow")));

    /** Smithery helmet; HEAD-slot armor item, composition writes per-stack defense/toughness. */
    public static final RegistryObject<SmitheryArmorItem> HELMET = registerArmor("helmet", ArmorItem.Type.HELMET);
    /** Smithery chestplate; CHEST-slot armor item. */
    public static final RegistryObject<SmitheryArmorItem> CHESTPLATE = registerArmor("chestplate", ArmorItem.Type.CHESTPLATE);
    /** Smithery leggings; LEGS-slot armor item. */
    public static final RegistryObject<SmitheryArmorItem> LEGGINGS = registerArmor("leggings", ArmorItem.Type.LEGGINGS);
    /** Smithery boots; FEET-slot armor item. */
    public static final RegistryObject<SmitheryArmorItem> BOOTS = registerArmor("boots", ArmorItem.Type.BOOTS);

    /** Flame-string crafting item; part-press input that produces flamestring material parts. */
    public static final RegistryObject<Item> FLAMESTRING =
            ITEMS.register("flamestring", () -> new Item(new Item.Properties()));
    /** Breeze-string crafting item; part-press input that produces breezestring material parts. */
    public static final RegistryObject<Item> BREEZESTRING =
            ITEMS.register("breezestring", () -> new Item(new Item.Properties()));
    /** Red slime crafting item; part-press input that produces red_slime material parts. */
    public static final RegistryObject<Item> RED_SLIME =
            ITEMS.register("red_slime", () -> new Item(new Item.Properties()));
    /** Kelp string crafting item; part-press input that produces kelp_string material parts. */
    public static final RegistryObject<Item> KELP_STRING =
            ITEMS.register("kelp_string", () -> new Item(new Item.Properties()));

    /** Unfinished kelp string tier I; first stage of the kelp-string weave progression. */
    public static final RegistryObject<Item> UNFINISHED_KELP_STRING_1 =
            ITEMS.register("unfinished_kelp_string_1", () -> new Item(new Item.Properties()));
    /** Unfinished kelp string tier II; second stage of the kelp-string weave progression. */
    public static final RegistryObject<Item> UNFINISHED_KELP_STRING_2 =
            ITEMS.register("unfinished_kelp_string_2", () -> new Item(new Item.Properties()));
    /** Unfinished kelp string tier III; third stage of the kelp-string weave progression. */
    public static final RegistryObject<Item> UNFINISHED_KELP_STRING_3 =
            ITEMS.register("unfinished_kelp_string_3", () -> new Item(new Item.Properties()));

    /**
     * Iterates over all currently-registered Materials × PartTypes (both in the
     * {@code smithery:} namespace) and queues one {@link PartItem} registration per pair.
     *
     * <p>Must run AFTER materials and part types have been registered into
     * {@link SmitheryAPI} but BEFORE the mod event bus receives the {@link #ITEMS} register
     * call in Smithery's constructor.
     */
    public static void registerBuiltInParts() {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            if (!Smithery.MODID.equals(mat.id().getNamespace())) continue;
            if (mat.stats().castOnly()) continue;
            registerPartsFor(mat.id(), ITEMS);
        }
    }

    /**
     * Modder-facing helper. Registers one {@link PartItem} per registered {@link PartType}
     * for the given material id, using the supplied item register.
     *
     * <p>Item path format: {@code "<material_path>_<part_path>"}. Items land in whichever
     * namespace the supplied item register uses.
     *
     * @param materialId the {@link Material} the parts should reference
     * @param targetItems the deferred items register that should own the new entries
     */
    public static void registerPartsFor(ResourceLocation materialId, DeferredRegister<Item> targetItems) {
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.syntheticCast()) continue;
            if (!PartEligibility.isAllowed(pt.id(), materialId)) continue;
            ResourceLocation ptId = pt.id();
            String itemPath = materialId.getPath() + "_" + ptId.getPath();
            RegistryObject<PartItem> item = targetItems.register(
                    itemPath,
                    () -> new PartItem(new Item.Properties(), materialId, ptId)
            );
            if (targetItems == ITEMS) {
                BUILT_IN_PART_ITEMS.put(itemPath, item);
            }
        }
    }

    /**
     * Looks up a built-in {@link PartItem} by material id and part-type id.
     *
     * @param materialId id of a Material registered in the {@code smithery:} namespace
     * @param partTypeId id of a {@link PartType}
     * @return the matching registry object, or null when the pair is not a built-in part
     */
    public static RegistryObject<PartItem> getBuiltInPart(ResourceLocation materialId, ResourceLocation partTypeId) {
        return BUILT_IN_PART_ITEMS.get(materialId.getPath() + "_" + partTypeId.getPath());
    }

    /**
     * All built-in {@link PartItem} registrations keyed by their {@code "<material>_<part>"} path.
     *
     * @return unmodifiable view of the built-in parts map
     */
    public static Map<String, RegistryObject<PartItem>> builtInParts() {
        return Collections.unmodifiableMap(BUILT_IN_PART_ITEMS);
    }

    /**
     * Looks up any registered {@link PartItem} by material and part-type ids, regardless of
     * which mod registered it. The item's registry name is
     * {@code <materialNamespace>:<materialPath>_<partTypePath>}.
     *
     * @return the matching PartItem, or null if not registered
     */
    public static PartItem findPart(ResourceLocation materialId, ResourceLocation partTypeId) {
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(
                materialId.getNamespace(),
                materialId.getPath() + "_" + partTypeId.getPath());
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
        return (item instanceof PartItem p) ? p : null;
    }

    /**
     * Binds the deferred items register to the mod event bus.
     *
     * @param modEventBus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private SmitheryItems() {}
}
