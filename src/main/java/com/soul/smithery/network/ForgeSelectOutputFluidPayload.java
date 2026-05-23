package com.soul.smithery.network;

import com.soul.smithery.Smithery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server payload. Player clicked a fluid layer in the Forge Controller GUI; tell
 * the server which fluid id should be the active output for that controller. An empty
 * {@code fluidId} ({@link Identifier} parsing to namespace="" / path="") clears the selection.
 */
public record ForgeSelectOutputFluidPayload(BlockPos controllerPos, Identifier fluidId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ForgeSelectOutputFluidPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Smithery.MODID, "forge_select_output_fluid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeSelectOutputFluidPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ForgeSelectOutputFluidPayload::controllerPos,
                    Identifier.STREAM_CODEC, ForgeSelectOutputFluidPayload::fluidId,
                    ForgeSelectOutputFluidPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
