package com.soul.smithery.block;

import net.minecraft.world.level.block.Block;

/**
 * Molten fluid output port for the Forge. Exposes the IFluidHandler capability so pipes
 * from other mods can drain molten metal. Logic-free for now.
 */
public class ForgeDrainBlock extends Block {
    public ForgeDrainBlock(Properties properties) {
        super(properties);
    }
}
