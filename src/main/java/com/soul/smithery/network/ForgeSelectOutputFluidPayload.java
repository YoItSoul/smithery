package com.soul.smithery.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server message announcing which stored fluid the player wants the named forge
 * controller to drain as its active output.
 *
 * <p>The server-side handler additionally validates proximity to the controller.
 *
 * @param controllerPos block position of the forge controller being configured
 * @param fluidId       identifier of the fluid to select (selecting the current fluid clears it)
 */
public record ForgeSelectOutputFluidPayload(BlockPos controllerPos, ResourceLocation fluidId) {

    /** Writes this message to the network buffer. */
    public static void encode(ForgeSelectOutputFluidPayload msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.controllerPos);
        buf.writeResourceLocation(msg.fluidId);
    }

    /** Reads a message from the network buffer. */
    public static ForgeSelectOutputFluidPayload decode(FriendlyByteBuf buf) {
        return new ForgeSelectOutputFluidPayload(buf.readBlockPos(), buf.readResourceLocation());
    }
}
