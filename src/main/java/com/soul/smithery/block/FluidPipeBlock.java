package com.soul.smithery.block;

import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.registry.SmitheryBlockEntities;
import com.soul.smithery.registry.SmitheryBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One-way fluid transport block. Six per-face EnumProperty&lt;FluidPipeFaceVisual&gt; values
 * pick which sub-model renders for each face; the BlockEntity owns the behavioural
 * face mode (Connected/Disconnected/In/Out) and the actual fluid storage.
 *
 * 4 visual states × 6 faces = 4096 blockstate combinations. Multipart blockstate JSON
 * keeps the model registration manageable (one entry per face × visual = 24 entries).
 */
public class FluidPipeBlock extends Block implements EntityBlock {

    public static final EnumProperty<FluidPipeFaceVisual> NORTH = EnumProperty.create("north", FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> EAST  = EnumProperty.create("east",  FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> SOUTH = EnumProperty.create("south", FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> WEST  = EnumProperty.create("west",  FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> UP    = EnumProperty.create("up",    FluidPipeFaceVisual.class);
    public static final EnumProperty<FluidPipeFaceVisual> DOWN  = EnumProperty.create("down",  FluidPipeFaceVisual.class);

    /** Centre cube + small inset so the player's crosshair-trace picks the right face. */
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
        // Hit box is the centre cube only — arms extend visually but the cube is enough
        // for the player to target any face. Hitting an arm still resolves the face via
        // the trace's hit direction.
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != SmitheryBlockEntities.FLUID_PIPE.get()) return null;
        return (lvl, pos, st, be) -> ((FluidPipeBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    // ---- Lifecycle hooks ----

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshAllFaces(level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        refreshAllFaces(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;
        refreshAllFaces(level, pos);
    }

    private void refreshAllFaces(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.refreshAllVisuals();
        }
    }

    // ---- Interaction: cycle face mode with a stick ----

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.STICK)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)) return InteractionResult.PASS;

        // hit.getDirection() returns the face of THIS block that was clicked
        // (i.e. the face whose visual the player can see). That's the face we cycle.
        Direction face = hit.getDirection();
        FluidPipeFaceMode newMode = pipe.cycleFaceMode(face);
        player.sendSystemMessage(Component.literal("Pipe face " + face.getName().toUpperCase()
                        + ": " + newMode.displayName()).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)) return InteractionResult.PASS;
        // Bare-hand peek: report the face's current mode + the pipe's stored fluid.
        Direction face = hit.getDirection();
        FluidPipeFaceMode mode = pipe.faceMode(face);
        String fluidLine = pipe.storedFluidLabel();
        player.sendSystemMessage(Component.literal("Pipe face " + face.getName().toUpperCase()
                        + ": " + mode.displayName() + "  |  " + fluidLine)
                .withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    /**
     * Helper for the BE to find its block. Avoids hand-rolled instanceof checks
     * in code that already has a Level + BlockPos.
     */
    public static boolean isPipe(BlockState state) {
        return state.is(SmitheryBlocks.FLUID_PIPE.get());
    }
}
