package com.soul.smithery.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server message setting which shape a Part Press is configured to cut.
 *
 * <p>Sent when the player picks one in the press's selection screen. The screen is client-only —
 * there is no menu behind it — so this is the only thing that crosses back, and the server
 * re-validates both the reach and the part type rather than trusting either.
 *
 * @param pressPos   position of the press being configured
 * @param partTypeId id of the chosen part type
 */
public record PartPressSelectPartPayload(BlockPos pressPos, ResourceLocation partTypeId) {

    /** Writes this message to the network buffer. */
    public static void encode(PartPressSelectPartPayload msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pressPos);
        buf.writeResourceLocation(msg.partTypeId);
    }

    /** Reads a message from the network buffer. */
    public static PartPressSelectPartPayload decode(FriendlyByteBuf buf) {
        return new PartPressSelectPartPayload(buf.readBlockPos(), buf.readResourceLocation());
    }
}
