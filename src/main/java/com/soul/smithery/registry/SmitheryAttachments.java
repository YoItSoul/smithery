package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Entity data attachments. Currently just the soulbound stash: items rescued from a player's
 * death drops, held across the death copy ({@code copyOnDeath}) and paid back out by
 * {@code SoulboundHandler} when the respawned player joins the level.
 */
public final class SmitheryAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Smithery.MODID);

    /** Items kept through death by the soulbound modifier, pending return on respawn. */
    public static final Supplier<AttachmentType<List<ItemStack>>> SOULBOUND_STASH =
            ATTACHMENT_TYPES.register("soulbound_stash", () -> AttachmentType
                    .<List<ItemStack>>builder(() -> List.of())
                    .serialize(ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items"))
                    .copyOnDeath()
                    .build());

    /** Attaches the deferred register to the mod event bus. */
    public static void register(IEventBus bus) {
        ATTACHMENT_TYPES.register(bus);
    }

    private SmitheryAttachments() {}
}
