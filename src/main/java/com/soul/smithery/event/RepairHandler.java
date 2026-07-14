package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolStats;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Vanilla anvil hook that repairs smithery gear with cast stone parts: sharpening stones for
 * tools, polishing stones for armor. Both are tier-gated — the stone's material must match or
 * exceed the gear's harvest level (for armor, the max across its core and trim materials). Each
 * stone restores 25% of max durability; the anvil consumes only as many stones as the damage
 * actually needs.
 *
 * <p>Repair never touches modifiers or composition — it only lowers DAMAGE. Broken armor
 * revives automatically once repaired because its attribute strip is computed live from the
 * damage value.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class RepairHandler {
    private RepairHandler() {}

    private static final float REPAIR_FRACTION = 0.25f;
    private static final int REPAIR_XP_COST = 1;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack gear = event.getLeft();
        ItemStack source = event.getRight();
        if (gear.isEmpty() || source.isEmpty()) return;
        if (!(source.getItem() instanceof PartItem stone)) return;
        if (!gear.isDamageableItem() || gear.getDamageValue() <= 0) return;

        boolean isTool = gear.getItem() instanceof SmitheryToolItem;
        boolean isArmor = gear.getItem() instanceof SmitheryArmorItem;
        if (isTool && !stone.partTypeId().equals(SmitheryPartTypes.SHARPENING_STONE.id())) return;
        if (isArmor && !stone.partTypeId().equals(SmitheryPartTypes.POLISHING_STONE.id())) return;
        if (!isTool && !isArmor) return;

        if (!stoneTierCovers(stone, gear)) return;

        int repairPerStone = Math.max(1, Math.round(gear.getMaxDamage() * REPAIR_FRACTION));
        int stonesNeeded = (gear.getDamageValue() + repairPerStone - 1) / repairPerStone;
        int toConsume = Math.min(source.getCount(), stonesNeeded);
        if (toConsume <= 0) return;

        ItemStack output = gear.copy();
        output.setDamageValue(Math.max(0, gear.getDamageValue() - repairPerStone * toConsume));

        event.setOutput(output);
        event.setXpCost(REPAIR_XP_COST);
        event.setMaterialCost(toConsume);
    }

    /**
     * True when the stone material's harvest level meets or exceeds the gear's — for tools
     * that's the max across head slots, for armor the max across core and trim. A wooden
     * stone can't work netherite, whether it's holding an edge or buffing out a dent.
     */
    private static boolean stoneTierCovers(PartItem stone, ItemStack gear) {
        Material stoneMaterial = SmitheryAPI.MATERIALS.get(stone.materialId());
        if (stoneMaterial == null) return false;
        ToolComposition comp = gear.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null || !comp.isValid()) return false;
        return stoneMaterial.stats().harvestLevel() >= ToolStats.compute(comp).harvestLevel;
    }
}
