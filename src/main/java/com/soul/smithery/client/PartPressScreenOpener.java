package com.soul.smithery.client;

import com.soul.smithery.block.entity.PartPressBlockEntity;
import com.soul.smithery.gui.PartPressScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-only entry point for opening the Part Press shape picker.
 *
 * <p>Kept apart from {@link com.soul.smithery.block.PartPressBlock} — which is a common class —
 * and invoked behind a dist check, so no screen class ever loads on a dedicated server.
 */
public final class PartPressScreenOpener {

    private PartPressScreenOpener() {}

    /**
     * Opens the picker for the press at {@code pos}, using the block entity the client already has
     * to show which shape is currently selected. Does nothing if the block entity is missing.
     */
    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(mc.level.getBlockEntity(pos) instanceof PartPressBlockEntity press)) return;
        mc.setScreen(new PartPressScreen(pos, press.selectedPartType()));
    }
}
