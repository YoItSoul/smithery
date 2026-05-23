package com.soul.smithery.block;

import com.soul.smithery.block.entity.ForgeDrainBlockEntity;
import com.soul.smithery.registry.SmitheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Molten fluid output port for the Forge. The BlockEntity:
 *   - holds a back-reference to its controller (set during controller validation)
 *   - exposes a passthrough fluid capability for buckets / external mods
 *   - runs a server tick that walks the adjacent pipe network and pumps the
 *     controller's selected output fluid into discovered sinks (casting tables,
 *     fuel ports, foreign tanks, etc).
 */
public class ForgeDrainBlock extends Block implements EntityBlock {
    public ForgeDrainBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeDrainBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != SmitheryBlockEntities.FORGE_DRAIN.get()) return null;
        return (lvl, pos, st, be) -> ((ForgeDrainBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }
}
