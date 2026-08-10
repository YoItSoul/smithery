package com.soul.smithery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soul.smithery.block.FluidPipeBlock;
import com.soul.smithery.block.FluidPipeFaceVisual;
import com.soul.smithery.block.entity.FluidPipeBlockEntity;
import com.soul.smithery.registry.SmitheryFluids;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Block entity renderer for fluid pipes.
 *
 * <p>Draws a tinted molten cube inside the pipe's central hollow whenever the pipe has
 * recently transported fluid. The cube tints the shared molten block-atlas sprite by the
 * material's molten colour, fading via the pipe's intensity ticks; arm cuboids extend
 * into each connected face so the flow reads as continuous along the network.
 */
public class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeBlockEntity> {

    /**
     * Constructs the renderer with the provider context.
     *
     * @param context renderer provider context (unused)
     */
    public FluidPipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FluidPipeBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ResourceLocation fluidId = be.transientFluidId();
        if (fluidId == null) return;

        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) return;
        SmitheryFluids.Entry entry = SmitheryFluids.forFluid(fluid);
        if (entry == null) return;

        int rgb = entry.material.stats().moltenColor() & 0xFFFFFF;
        float fade = (float) be.intensityTicks() / (float) FluidPipeBlockEntity.FLOW_PERSIST_TICKS;
        int alpha = Math.max(0, Math.min(255, (int) (fade * 255f)));
        if (alpha <= 0) return;
        int color = (alpha << 24) | rgb;

        boolean[] armConnected = new boolean[6];
        BlockState blockState = be.getBlockState();
        if (blockState.getBlock() instanceof FluidPipeBlock) {
            for (Direction dir : Direction.values()) {
                FluidPipeFaceVisual v = blockState.getValue(FluidPipeBlock.propertyFor(dir));
                armConnected[dir.get3DDataValue()] = v != FluidPipeFaceVisual.NONE;
            }
        }

        final float lo = MoltenColumn.BORE_MIN;
        final float hi = MoltenColumn.BORE_MAX;
        final float armNear = 0f;
        final float armFar  = MoltenColumn.BORE_MIN;
        final float armFar2 = MoltenColumn.BORE_MAX;
        final float armEnd  = 16f / 16f;

        TextureAtlasSprite sprite = MoltenColumn.sprite();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(sprite.atlasLocation()));
        PoseStack.Pose pose = poseStack.last();

        MoltenColumn.drawCuboid(pose, buffer, sprite, lo, lo, lo,  hi, hi, hi, color);

        if (armConnected[Direction.NORTH.get3DDataValue()])
            MoltenColumn.drawCuboid(pose, buffer, sprite, lo, lo, armNear,  hi, hi, armFar, color);
        if (armConnected[Direction.SOUTH.get3DDataValue()])
            MoltenColumn.drawCuboid(pose, buffer, sprite, lo, lo, armFar2,  hi, hi, armEnd, color);
        if (armConnected[Direction.WEST.get3DDataValue()])
            MoltenColumn.drawCuboid(pose, buffer, sprite, armNear, lo, lo,  armFar, hi, hi, color);
        if (armConnected[Direction.EAST.get3DDataValue()])
            MoltenColumn.drawCuboid(pose, buffer, sprite, armFar2, lo, lo,  armEnd, hi, hi, color);
        if (armConnected[Direction.DOWN.get3DDataValue()])
            MoltenColumn.drawCuboid(pose, buffer, sprite, lo, armNear, lo,  hi, armFar, hi, color);
        if (armConnected[Direction.UP.get3DDataValue()])
            MoltenColumn.drawCuboid(pose, buffer, sprite, lo, armFar2, lo,  hi, armEnd, hi, color);
    }

}
