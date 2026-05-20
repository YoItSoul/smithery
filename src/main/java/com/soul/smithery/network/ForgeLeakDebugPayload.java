package com.soul.smithery.network;

import com.soul.smithery.Smithery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Server → client payload. Tells the client to draw red wireframe boxes around the given
 * positions for a fixed duration. Used as the debug visualization for forge wall holes
 * (leak points) detected during validation.
 */
public record ForgeLeakDebugPayload(List<BlockPos> positions, int durationTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ForgeLeakDebugPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Smithery.MODID, "forge_leak_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeLeakDebugPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ForgeLeakDebugPayload::positions,
                    ByteBufCodecs.VAR_INT, ForgeLeakDebugPayload::durationTicks,
                    ForgeLeakDebugPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
