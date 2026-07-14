package com.soul.smithery.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client message requesting that the client draw debug wireframe boxes at the given
 * positions for a fixed duration.
 *
 * <p>Used to visualise the wall holes (leak points) found during forge multiblock validation.
 *
 * @param positions     block positions to outline on the client
 * @param durationTicks how long the boxes remain visible, in ticks
 */
public record ForgeLeakDebugPayload(List<BlockPos> positions, int durationTicks) {

    /** Writes this message to the network buffer. */
    public static void encode(ForgeLeakDebugPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.positions.size());
        for (BlockPos pos : msg.positions) {
            buf.writeBlockPos(pos);
        }
        buf.writeVarInt(msg.durationTicks);
    }

    /** Reads a message from the network buffer. */
    public static ForgeLeakDebugPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        return new ForgeLeakDebugPayload(positions, buf.readVarInt());
    }
}
