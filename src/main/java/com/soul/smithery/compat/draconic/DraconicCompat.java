package com.soul.smithery.compat.draconic;

import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draconic Evolution integration gate. This class never references DE classes, so it is safe
 * to load whether or not DE is installed; everything DE-typed lives behind {@link #LOADED}
 * checks in {@link DraconicItemFactory} and friends, which are only classloaded when DE exists.
 *
 * <p>When DE is present, Smithery tools and armor are registered as DE-aware subclasses that
 * implement {@code IModularItem}: tools composed from a registered draconic-tier material gain a
 * DE module grid (opened with DE's own keybind), accept DE modules, and store DE energy.
 *
 * <p>Material tiers are contributed by integrating mods via {@link #registerMaterialTier} —
 * Smithery itself maps nothing. Tier indices follow BrandonsCore's {@code TechLevel} ordinals:
 * 1 = WYVERN, 2 = DRACONIC, 3 = CHAOTIC. (0 = DRACONIUM exists but DE itself has no
 * draconium-tier gear, and its module-grid config has no dimensions for it — don't use it.)
 */
public final class DraconicCompat {

    /** True when Draconic Evolution is in the mod list. Fixed at classload. */
    public static final boolean LOADED = ModList.get().isLoaded("draconicevolution");

    private static final Map<ResourceLocation, Integer> MATERIAL_TIERS = new LinkedHashMap<>();

    private DraconicCompat() {}

    /**
     * Maps a Smithery material to a DE tech tier, enabling the module grid on gear composed
     * from it. Call any time before world load (mod construction is fine).
     *
     * @param materialId the Smithery material id (e.g. {@code soa_additions:draconic_metal})
     * @param techLevelIndex 0 = DRACONIUM, 1 = WYVERN, 2 = DRACONIC, 3 = CHAOTIC.
     *        DE ships no draconium-tier gear, so draconium hosts use the wyvern grid/energy
     *        dimensions with a DRACONIUM host tech level (gates module tiers accordingly).
     */
    public static void registerMaterialTier(ResourceLocation materialId, int techLevelIndex) {
        if (techLevelIndex < 0 || techLevelIndex > 3) {
            throw new IllegalArgumentException("techLevelIndex must be 0 (DRACONIUM), 1 (WYVERN), 2 (DRACONIC) or 3 (CHAOTIC)");
        }
        MATERIAL_TIERS.put(materialId, techLevelIndex);
    }

    /** The registered material→tier view. Read by the DE-side host builder. */
    public static Map<ResourceLocation, Integer> materialTiers() {
        return MATERIAL_TIERS;
    }

    /** Tool factory: DE-aware subclass when DE is loaded, plain {@link SmitheryToolItem} otherwise. */
    public static SmitheryToolItem newTool(Item.Properties props, ResourceLocation toolTypeId) {
        if (LOADED) {
            return DraconicItemFactory.newTool(props, toolTypeId);
        }
        return new SmitheryToolItem(props, toolTypeId);
    }

    /** Armor factory: DE-aware subclass when DE is loaded, plain {@link SmitheryArmorItem} otherwise. */
    public static SmitheryArmorItem newArmor(ArmorItem.Type type, Item.Properties props, ResourceLocation id) {
        if (LOADED) {
            return DraconicItemFactory.newArmor(type, props, id);
        }
        return new SmitheryArmorItem(type, props, id);
    }

    /** Registers DE-side event handlers; call once from mod construction when {@link #LOADED}. */
    public static void init() {
        if (LOADED) {
            DraconicItemFactory.initEvents();
        }
    }
}
