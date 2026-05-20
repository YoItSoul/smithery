package com.soul.smithery.block;

import net.minecraft.world.level.block.Block;

/**
 * Lava input port for the Forge. Stores fuel internally. Logic-free for now — the
 * BlockEntity hookup happens once heat/fuel simulation is added.
 */
public class ForgeFuelPortBlock extends Block {
    public ForgeFuelPortBlock(Properties properties) {
        super(properties);
    }
}
