package com.soul.smithery.block;

import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * Dumb fluid-channel block. Pipes auto-connect to other pipes and to fluid containers
 * by neighbour-type detection; the forge drain is the active pump, while this block
 * just wires the path. Each face carries a {@link FluidPipeFaceVisual} blockstate
 * property recomputed from neighbour blocks on place and on neighbour changes.
 */
public class FluidPipeBlock extends Block implements EntityBlock {

    /** North-face visual property. */
    public static final EnumProperty<FluidPipeFaceVisual> NORTH = EnumProperty.create("north", FluidPipeFaceVisual.class);
    /** East-face visual property. */
    public static final EnumProperty<FluidPipeFaceVisual> EAST  = EnumProperty.create("east",  FluidPipeFaceVisual.class);
    /** South-face visual property. */
    public static final EnumProperty<FluidPipeFaceVisual> SOUTH = EnumProperty.create("south", FluidPipeFaceVisual.class);
    /** West-face visual property. */
    public static final EnumProperty<FluidPipeFaceVisual> WEST  = EnumProperty.create("west",  FluidPipeFaceVisual.class);
    /** Up-face visual property. */
    public static final EnumProperty<FluidPipeFaceVisual> UP    = EnumProperty.create("up",    FluidPipeFaceVisual.class);
    /** Down-face visual property. */
    public static final EnumProperty<FluidPipeFaceVisual> DOWN  = EnumProperty.create("down",  FluidPipeFaceVisual.class);

    private static final VoxelShape SHAPE = Shapes.box(6.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
                                                       10.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0);

    /**
     * Constructs the fluid pipe with the given block properties and the all-NONE default state.
     */
    public FluidPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, FluidPipeFaceVisual.NONE)
                .setValue(EAST,  FluidPipeFaceVisual.NONE)
                .setValue(SOUTH, FluidPipeFaceVisual.NONE)
                .setValue(WEST,  FluidPipeFaceVisual.NONE)
                .setValue(UP,    FluidPipeFaceVisual.NONE)
                .setValue(DOWN,  FluidPipeFaceVisual.NONE));
    }

    /**
     * Returns the per-face visual property corresponding to the given direction.
     */
    public static EnumProperty<FluidPipeFaceVisual> propertyFor(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            case UP    -> UP;
            case DOWN  -> DOWN;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) return;
        refreshConnections(level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        refreshConnections(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;
        refreshConnections(level, pos);
    }

    /**
     * Re-evaluates every face's visual at {@code pos} from its current neighbour and
     * issues a single setBlock when the resulting state differs from the current one.
     */
    public static void refreshConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FluidPipeBlock)) return;

        BlockState newState = state;
        for (Direction dir : Direction.values()) {
            FluidPipeFaceVisual visual = computeVisual(level, pos, dir);
            newState = newState.setValue(propertyFor(dir), visual);
        }
        if (newState != state) {
            level.setBlock(pos, newState, 3);
        }
    }

    private static FluidPipeFaceVisual computeVisual(Level level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof FluidPipeBlock) {
            return FluidPipeFaceVisual.ARM_OPEN;
        }
        if (neighborState.is(SmitheryBlocks.CASTING_TABLE.get())) {
            return dir == Direction.DOWN ? FluidPipeFaceVisual.ARM_OPEN : FluidPipeFaceVisual.NONE;
        }
        if (neighborState.is(SmitheryBlocks.FORGE_DRAIN.get())) {
            return FluidPipeFaceVisual.ARM_TOOTHER;
        }
        if (neighborState.isAir()) {
            return FluidPipeFaceVisual.NONE;
        }
        ResourceHandler<FluidResource> handler = level.getCapability(
                Capabilities.Fluid.BLOCK, neighborPos, dir.getOpposite());
        if (handler != null && handler.size() > 0) {
            return FluidPipeFaceVisual.ARM_TOOTHER;
        }
        return FluidPipeFaceVisual.NONE;
    }

    /**
     * Tests whether the given blockstate is a fluid pipe; used by the drain's BFS walker.
     */
    public static boolean isPipe(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != SmitheryBlockEntities.FLUID_PIPE.get()) return null;
        return (lvl, p, st, be) -> ((FluidPipeBlockEntity) be).serverTick((ServerLevel) lvl, p, st);
    }
}
