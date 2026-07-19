package com.soul.smithery.compat.draconic;

import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.JumpData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge-event bridges for DE module effects on Smithery gear that no item override can serve.
 *
 * <p>Currently: JUMP_BOOST modules on worn Smithery armor add upward velocity on jump,
 * mirroring DE's own jump boost strength (+~0.1 blocks-per-tick of launch velocity per
 * multiplier point). Sneaking suppresses the boost, same as DE.
 */
public final class DraconicEvents {

    @SubscribeEvent
    public void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isShiftKeyDown()) return;
        double multiplier = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof DraconicSmitheryArmorItem)) continue;
            JumpData data = ModularSupport.moduleData(stack, ModuleTypes.JUMP_BOOST);
            if (data != null) multiplier += data.multiplier();
        }
        if (multiplier <= 0) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, motion.y + 0.1 * multiplier, motion.z);
    }
}
