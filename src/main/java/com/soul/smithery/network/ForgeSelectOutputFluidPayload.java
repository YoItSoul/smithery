package com.soul.smithery.network;

import com.soul.smithery.Smithery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload announcing which stored fluid the player wants the named forge
 * controller to drain as its active output.
 *
 * <p>An empty {@code fluidId} (one whose namespace and path are both empty) clears the
 * selection. The server-side handler additionally validates proximity to the controller.
 *
 * @param controllerPos block position of the forge controller being configured
 * @param fluidId       identifier of the fluid to select, or an empty identifier to clear
 */
public record ForgeSelectOutputFluidPayload(BlockPos controllerPos, ResourceLocation fluidId)
        implements CustomPacketPayload {

    /** Payload type identifier under {@code smithery:forge_select_output_fluid}. */
    public static final CustomPacketPayload.Type<ForgeSelectOutputFluidPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    new ResourceLocation(Smithery.MODID, "forge_select_output_fluid"));

    /** Stream codec for serialising payload instances over the network. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeSelectOutputFluidPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ForgeSelectOutputFluidPayload::controllerPos,
                    ResourceLocation.STREAM_CODEC, ForgeSelectOutputFluidPayload::fluidId,
                    ForgeSelectOutputFluidPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
