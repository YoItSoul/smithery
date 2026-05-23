package com.soul.smithery.block;

import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * A dumb fluid channel. Pipes auto-connect to other pipes (and to fluid containers /
 * casting tables) by neighbour-type detection — no BlockEntity, no per-face mode, no
 * stick interaction. The forge drain is the active pump that pulls fluid from the
 * controller and pushes it through pipe networks into sinks; this block just wires
 * the path.
 *
 * Per-face EnumProperty&lt;FluidPipeFaceVisual&gt; carries the rendered geometry, recomputed
 * by {@link #onPlace} and {@link #neighborChanged} from the actual neighbour blocks.
 * 3 visuals × 6 faces = 729 blockstates.
 */
public class FluidPipeBlock extends Block {

    public static final EnumProperty<FluidPipeFaceVisual> NORTH = EnumProperty.create("north", FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> EAST  = EnumProperty.create("east",  FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> SOUTH = EnumProperty.create("south", FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> WEST  = EnumProperty.create("west",  FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> UP    = EnumProperty.create("up",    FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> DOWN  = EnumProperty.create("down",  FluidPipeFaceVisual.class);

    /** Centre cube hitbox — arms extend visually but the cube is the click target. */
    private static final VoxelShape SHAPE = Shapes.box(6.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
                                                       10.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0);

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

    // ---- Lifecycle: recompute connections on place + neighbor changes ----

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
     * Re-evaluates each face's visual from the actual neighbour block at that position.
     * Issues a single setBlock when the computed state differs from current; otherwise
     * skips to avoid spurious chunk re-meshes.
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

    /**
     * Per-face visual rule:
     *   1. Neighbour is another pipe                            → ARM_OPEN (continuous tube)
     *   2. DOWN face above a casting table                      → ARM_OPEN (free drip)
     *   3. Neighbour exposes a real fluid handler (size&gt;0)      → ARM_TOOTHER (flange)
     *   4. Anything else (air, inert walls, empty proxy)        → NONE (no geometry)
     *
     * Skips the capability lookup when the neighbour is air to avoid NeoForge 26.1's
     * non-null empty proxy handler that gets returned for waterloggable vanilla blocks.
     */
    private static FluidPipeFaceVisual computeVisual(Level level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof FluidPipeBlock) {
            return FluidPipeFaceVisual.ARM_OPEN;
        }
        if (dir == Direction.DOWN && neighborState.is(SmitheryBlocks.CASTING_TABLE.get())) {
            return FluidPipeFaceVisual.ARM_OPEN;
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

    /** Test helper used by ForgeDrainBlockEntity's network walker. */
    public static boolean isPipe(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock;
    }
}
