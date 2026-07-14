package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryArrowItem;
import com.soul.smithery.item.tool.SmitheryBowItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item registration root for Smithery-namespaced items.
 *
 * <p>Auto-generates one {@link PartItem} per (Material × PartType) pair in the
 * {@code smithery:} namespace. Modder mods that contribute their own materials should call
 * {@link #registerPartsFor(Identifier, DeferredRegister.Items)} from their constructor with
 * their own item register.
 */
public final class SmitheryItems {
    /** Deferred register for Smithery-namespaced items. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Smithery.MODID);

    private static final Map<String, DeferredItem<PartItem>> BUILT_IN_PART_ITEMS = new LinkedHashMap<>();

    /** Shared equipment asset for all smithery armor: grayscale layers dyed by core material. */
    private static final ResourceKey<EquipmentAsset> ARMOR_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, id("armor"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    /**
     * Registers a non-stacking {@link SmitheryToolItem} whose item name doubles as its
     * ToolType path — the invariant the assembly recipe's item lookup relies on.
     */
    private static DeferredItem<SmitheryToolItem> registerTool(String name) {
        return ITEMS.registerItem(name,
                props -> new SmitheryToolItem(props, id(name)),
                p -> p.stacksTo(1));
    }

    /**
     * Registers a {@link SmitheryArmorItem} bound to the shared {@link #ARMOR_ASSET} so the
     * worn model renders; per-material color arrives via the stack's DYED_COLOR component,
     * written at compose time.
     */
    private static DeferredItem<SmitheryArmorItem> registerArmor(String name, EquipmentSlot slot) {
        return ITEMS.registerItem(name,
                props -> new SmitheryArmorItem(props, id(name)),
                p -> p.stacksTo(1).component(DataComponents.EQUIPPABLE,
                        Equippable.builder(slot).setAsset(ARMOR_ASSET).build()));
    }

    /** Smithery sword tool item; per-stack composition lives in the {@code tool_composition} component. */
    public static final DeferredItem<SmitheryToolItem> SWORD = registerTool("sword");
    /** Smithery pickaxe tool item; per-stack composition lives in the {@code tool_composition} component. */
    public static final DeferredItem<SmitheryToolItem> PICKAXE = registerTool("pickaxe");
    /** Smithery axe tool item; per-stack composition lives in the {@code tool_composition} component. */
    public static final DeferredItem<SmitheryToolItem> AXE = registerTool("axe");
    /** Smithery shovel tool item; per-stack composition lives in the {@code tool_composition} component. */
    public static final DeferredItem<SmitheryToolItem> SHOVEL = registerTool("shovel");
    /** Smithery hoe tool item; per-stack composition lives in the {@code tool_composition} component. */
    public static final DeferredItem<SmitheryToolItem> HOE = registerTool("hoe");
    /** Smithery spear tool item; per-stack composition lives in the {@code tool_composition} component. */
    public static final DeferredItem<SmitheryToolItem> SPEAR = registerTool("spear");
    /** Smithery broadsword; heavy two-hand blade — slow swing, big hits. */
    public static final DeferredItem<SmitheryToolItem> BROADSWORD = registerTool("broadsword");
    /** Smithery rapier; fast thrusts that partially bypass the target's armor. */
    public static final DeferredItem<SmitheryToolItem> RAPIER = registerTool("rapier");
    /** Smithery paxel; pickaxe + axe + shovel + hoe in one head assembly. */
    public static final DeferredItem<SmitheryToolItem> PAXEL = registerTool("paxel");
    /** Smithery mining hammer; breaks a 3x3 face of stone at once. */
    public static final DeferredItem<SmitheryToolItem> MINING_HAMMER = registerTool("mining_hammer");

    /** Smithery bow tool item; uses a {@link SmitheryBowItem} for draw-frame model swaps. */
    public static final DeferredItem<SmitheryBowItem> BOW = ITEMS.registerItem("bow",
            props -> new SmitheryBowItem(props, id("bow")),
            p -> p.stacksTo(1));

    /** Smithery kama; shears abilities plus 3x3 double-yield crop harvesting. */
    public static final DeferredItem<SmitheryToolItem> KAMA = registerTool("kama");
    /** Smithery cleaver; slow, massive hits with an innate chance to behead. */
    public static final DeferredItem<SmitheryToolItem> CLEAVER = registerTool("cleaver");
    /** Smithery lumberaxe; fells the whole connected tree from one log. */
    public static final DeferredItem<SmitheryToolItem> LUMBERAXE = registerTool("lumberaxe");
    /** Smithery excavator; digs a 3x3 face of shovel material at once. */
    public static final DeferredItem<SmitheryToolItem> EXCAVATOR = registerTool("excavator");

    /** Smithery shuriken; stackable thrown weapon assembled from four blades. */
    public static final DeferredItem<com.soul.smithery.item.tool.SmitheryShurikenItem> SHURIKEN =
            ITEMS.registerItem("shuriken",
                    props -> new com.soul.smithery.item.tool.SmitheryShurikenItem(props, id("shuriken")));

    /** Smithery battlesign; a melee weapon that partially blocks while raised. */
    public static final DeferredItem<SmitheryToolItem> BATTLESIGN = registerTool("battlesign");

    /** Smithery trident; vanilla charge/throw pipeline, composition-driven melee stats. */
    public static final DeferredItem<com.soul.smithery.item.tool.SmitheryTridentItem> TRIDENT =
            ITEMS.registerItem("trident",
                    props -> new com.soul.smithery.item.tool.SmitheryTridentItem(props, id("trident")),
                    p -> p.stacksTo(1));

    /** Smithery crossbow; vanilla charge/store/fire pipeline with smithery arrow scaling. */
    public static final DeferredItem<com.soul.smithery.item.tool.SmitheryCrossbowItem> CROSSBOW =
            ITEMS.registerItem("crossbow",
                    props -> new com.soul.smithery.item.tool.SmitheryCrossbowItem(props, id("crossbow")),
                    p -> p.stacksTo(1));

    /** Smithery arrow tool item; stays stackable (default 64) since vanilla forbids stackables from carrying DAMAGE. */
    public static final DeferredItem<SmitheryArrowItem> ARROW = ITEMS.registerItem("arrow",
            props -> new SmitheryArrowItem(props, id("arrow")));

    /** Smithery helmet; HEAD-slot armor item, composition writes per-stack defense/toughness. */
    public static final DeferredItem<SmitheryArmorItem> HELMET = registerArmor("helmet", EquipmentSlot.HEAD);
    /** Smithery chestplate; CHEST-slot armor item. */
    public static final DeferredItem<SmitheryArmorItem> CHESTPLATE = registerArmor("chestplate", EquipmentSlot.CHEST);
    /** Smithery leggings; LEGS-slot armor item. */
    public static final DeferredItem<SmitheryArmorItem> LEGGINGS = registerArmor("leggings", EquipmentSlot.LEGS);
    /** Smithery boots; FEET-slot armor item. */
    public static final DeferredItem<SmitheryArmorItem> BOOTS = registerArmor("boots", EquipmentSlot.FEET);

    /** Flame-string crafting item; part-press input that produces flamestring material parts. */
    public static final DeferredItem<net.minecraft.world.item.Item> FLAMESTRING =
            ITEMS.registerItem("flamestring", net.minecraft.world.item.Item::new);
    /** Breeze-string crafting item; part-press input that produces breezestring material parts. */
    public static final DeferredItem<net.minecraft.world.item.Item> BREEZESTRING =
            ITEMS.registerItem("breezestring", net.minecraft.world.item.Item::new);
    /** Red slime crafting item; part-press input that produces red_slime material parts. */
    public static final DeferredItem<net.minecraft.world.item.Item> RED_SLIME =
            ITEMS.registerItem("red_slime", net.minecraft.world.item.Item::new);
    /** Kelp string crafting item; part-press input that produces kelp_string material parts. */
    public static final DeferredItem<net.minecraft.world.item.Item> KELP_STRING =
            ITEMS.registerItem("kelp_string", net.minecraft.world.item.Item::new);

    /** Unfinished kelp string tier I; first stage of the kelp-string weave progression. */
    public static final DeferredItem<net.minecraft.world.item.Item> UNFINISHED_KELP_STRING_1 =
            ITEMS.registerItem("unfinished_kelp_string_1", net.minecraft.world.item.Item::new);
    /** Unfinished kelp string tier II; second stage of the kelp-string weave progression. */
    public static final DeferredItem<net.minecraft.world.item.Item> UNFINISHED_KELP_STRING_2 =
            ITEMS.registerItem("unfinished_kelp_string_2", net.minecraft.world.item.Item::new);
    /** Unfinished kelp string tier III; third stage of the kelp-string weave progression. */
    public static final DeferredItem<net.minecraft.world.item.Item> UNFINISHED_KELP_STRING_3 =
            ITEMS.registerItem("unfinished_kelp_string_3", net.minecraft.world.item.Item::new);

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
     * namespace the supplied {@link DeferredRegister.Items} uses.
     *
     * @param materialId the {@link Material} the parts should reference
     * @param targetItems the deferred items register that should own the new entries
     */
    public static void registerPartsFor(Identifier materialId, DeferredRegister.Items targetItems) {
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.syntheticCast()) continue;
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

    /**
     * Looks up a built-in {@link PartItem} by material id and part-type id.
     *
     * @param materialId id of a Material registered in the {@code smithery:} namespace
     * @param partTypeId id of a {@link PartType}
     * @return the matching DeferredItem, or null when the pair is not a built-in part
     */
    public static DeferredItem<PartItem> getBuiltInPart(Identifier materialId, Identifier partTypeId) {
        return BUILT_IN_PART_ITEMS.get(materialId.getPath() + "_" + partTypeId.getPath());
    }

    /**
     * All built-in {@link PartItem} registrations keyed by their {@code "<material>_<part>"} path.
     *
     * @return unmodifiable view of the built-in parts map
     */
    public static Map<String, DeferredItem<PartItem>> builtInParts() {
        return java.util.Collections.unmodifiableMap(BUILT_IN_PART_ITEMS);
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
