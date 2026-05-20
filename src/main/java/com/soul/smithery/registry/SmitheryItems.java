package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryToolTypes;
import com.soul.smithery.item.PartItem;
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

    /**
     * Iterate all currently-registered Materials × PartTypes (both in the smithery: namespace)
     * and queue one PartItem registration per pair. Must run AFTER materials and part types
     * have been registered into SmitheryAPI but BEFORE the mod event bus receives our ITEMS
     * register call (which happens in Smithery's constructor).
     */
    public static void registerBuiltInParts() {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            if (!Smithery.MODID.equals(mat.id().getNamespace())) continue;
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
