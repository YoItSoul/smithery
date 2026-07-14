package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolCompositions;
import com.soul.smithery.item.tool.SmitheryToolData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AnvilUpdateEvent;

/**
 * Vanilla anvil hook that embosses smithery gear with a donor material: place any smithery
 * part (except repair stones — those belong to {@link RepairHandler}) opposite a tool or armor
 * piece and its material's traits graft onto the gear without touching stats. Re-embossing
 * replaces the previous donor, so gear upgrades alongside the player's material progression —
 * each swap consumes the new donor part.
 *
 * <p>Donor traits never override effects the gear already has (see the collection order in
 * {@code ToolStats.compute}); embossment is flavor grafting, not a power multiplier.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class EmbossmentHandler {
    private EmbossmentHandler() {}

    private static final int EMBOSS_XP_COST = 3;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack gear = event.getLeft();
        ItemStack source = event.getRight();
        if (gear.isEmpty() || source.isEmpty()) return;
        if (!ToolCompositions.isComposable(gear)) return;
        if (!(source.getItem() instanceof PartItem part)) return;
        if (part.partTypeId().equals(SmitheryPartTypes.SHARPENING_STONE.id())
                || part.partTypeId().equals(SmitheryPartTypes.POLISHING_STONE.id())) return;

        ToolComposition comp = SmitheryToolData.getComposition(gear);
        if (comp == null || !comp.isValid()) return;
        if (SmitheryAPI.MATERIALS.get(part.materialId()) == null) return;
        if (comp.embossedMaterial().map(part.materialId()::equals).orElse(false)) return;

        ItemStack output = gear.copy();
        ToolCompositions.apply(output, comp.withEmbossment(part.materialId()),
                event.getPlayer() != null ? event.getPlayer().level().registryAccess() : null);

        event.setOutput(output);
        event.setXpCost(EMBOSS_XP_COST);
        event.setMaterialCost(1);
    }
}
