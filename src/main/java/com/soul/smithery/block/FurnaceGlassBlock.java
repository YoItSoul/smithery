package com.soul.smithery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Glazing for the forge shell. Behaves as vanilla glass, but tracks which of its six
 * neighbours are also furnace glass so the blockstate can draw the cast-iron frame only
 * around the outside of a pane rather than around every block in it.
 *
 * <p>The connection is ours rather than a CTM library's — the same trick
 * {@link ForgeFuelPortBlock} uses for its stacked variants, widened to all six faces.
 * That keeps Smithery free of a hard dependency on Athena or similar.
 *
 * <p>The frame is separate geometry in the model, not painted into the texture. Six
 * booleans would otherwise mean a texture per edge-combination per dye colour; as thin
 * quads laid just outside each face, one pane texture per colour is enough, and the iron
 * stays iron under every dye for free.
 *
 * <p>Every colour of furnace glass counts as a connection for every other, so a pane can
 * mix dyes without the frame cutting it into pieces.
 */
public class FurnaceGlassBlock extends AbstractGlassBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST  = BlockStateProperties.EAST;
    public static final BooleanProperty WEST  = BlockStateProperties.WEST;
    public static final BooleanProperty UP    = BlockStateProperties.UP;
    public static final BooleanProperty DOWN  = BlockStateProperties.DOWN;

    /**
     * Constructs a furnace glass block with the given properties.
     */
    public FurnaceGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    /** The connection property for a given face. */
    public static BooleanProperty propertyFor(Direction d) {
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
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    private static boolean connects(BlockState neighbour) {
        return neighbour.getBlock() instanceof FurnaceGlassBlock;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        LevelReader level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        for (Direction d : Direction.values()) {
            state = state.setValue(propertyFor(d), connects(level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(propertyFor(direction), connects(neighbour));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return turned(state, rotation::rotate);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return turned(state, mirror::mirror);
    }

    /**
     * Carries each face's connection flag over to the face that face turns into.
     *
     * <p>Without this a structure places its glass with the frame drawn down the wrong
     * edges at three of the four jigsaw rotations: template placement rotates the state
     * but leaves properties it is not told about pointing where they were.
     */
    private static BlockState turned(BlockState state, UnaryOperator<Direction> moved) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            out = out.setValue(propertyFor(moved.apply(d)), state.getValue(propertyFor(d)));
        }
        return out;
    }

    /**
     * Hides the seam between two panes, the same way vanilla glass does. Any furnace glass
     * counts, not just an identical dye, so a mixed-colour pane still reads as one sheet of
     * glass rather than gaining an interior face at every colour change.
     */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.getBlock() instanceof FurnaceGlassBlock || super.skipRendering(state, adjacent, direction);
    }
}
