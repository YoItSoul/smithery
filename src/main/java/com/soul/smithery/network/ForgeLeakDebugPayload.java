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
 * Server-to-client payload requesting that the client draw debug wireframe boxes at the given
 * positions for a fixed duration.
 *
 * <p>Used to visualise the wall holes (leak points) found during forge multiblock validation.
 *
 * @param positions     block positions to outline on the client
 * @param durationTicks how long the boxes remain visible, in ticks
 */
public record ForgeLeakDebugPayload(List<BlockPos> positions, int durationTicks) implements CustomPacketPayload {

    /** Payload type identifier under {@code smithery:forge_leak_debug}. */
    public static final CustomPacketPayload.Type<ForgeLeakDebugPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Smithery.MODID, "forge_leak_debug"));

    /** Stream codec for serialising payload instances over the network. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeLeakDebugPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ForgeLeakDebugPayload::positions,
                    ByteBufCodecs.VAR_INT, ForgeLeakDebugPayload::durationTicks,
                    ForgeLeakDebugPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
