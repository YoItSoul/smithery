package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeDrainBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Molten fluid output port for the Forge. The BlockEntity stores a back-reference to its
 * Forge Controller (set during controller validation) and exposes a passthrough
 * ResourceHandler so pipes / other mods can drain molten metal directly from the
 * controller's storage.
 */
public class ForgeDrainBlock extends Block implements EntityBlock {
    public ForgeDrainBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeDrainBlockEntity(pos, state);
    }
}
