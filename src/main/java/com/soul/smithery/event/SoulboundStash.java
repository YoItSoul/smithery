package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-player stash of items rescued from death drops by the soulbound modifier, held
 * across the death copy and paid back out by {@link SoulboundHandler} when the respawned
 * player joins the level.
 *
 * <p>This is the 1.20.1 capability equivalent of a {@code copyOnDeath} data attachment:
 * the capability serializes with the player, and {@link SoulboundHandler} copies it across
 * the death clone explicitly.
 */
public final class SoulboundStash {

    /** Capability handle; injected by Forge's capability manager. */
    public static final Capability<SoulboundStash> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    /** Attachment id for the capability provider. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "soulbound_stash");

    private List<ItemStack> items = List.of();

    /** Items kept through death, pending return on respawn; never null. */
    public List<ItemStack> items() {
        return Collections.unmodifiableList(items);
    }

    /** Replaces the stash contents. */
    public void setItems(List<ItemStack> items) {
        this.items = List.copyOf(items);
    }

    /** Copies another stash's contents into this one (used across the death clone). */
    public void copyFrom(SoulboundStash other) {
        this.items = other.items;
    }

    ListTag save() {
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    void load(ListTag list) {
        List<ItemStack> loaded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) loaded.add(stack);
        }
        this.items = List.copyOf(loaded);
    }

    /** Registers the capability class with Forge. */
    @Mod.EventBusSubscriber(modid = Smithery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    static final class Registrar {
        private Registrar() {}

        @SubscribeEvent
        static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(SoulboundStash.class);
        }
    }

    /** Attaches a serializable stash to every player. */
    @Mod.EventBusSubscriber(modid = Smithery.MODID)
    static final class Attacher {
        private Attacher() {}

        @SubscribeEvent
        static void onAttachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(ID, new Provider());
            }
        }
    }

    /** Serializable capability provider carrying one stash instance. */
    static final class Provider implements ICapabilitySerializable<ListTag> {
        private final SoulboundStash stash = new SoulboundStash();
        private final LazyOptional<SoulboundStash> optional = LazyOptional.of(() -> stash);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public ListTag serializeNBT() {
            return stash.save();
        }

        @Override
        public void deserializeNBT(ListTag tag) {
            stash.load(tag);
        }
    }

    /** Convenience lookup; empty when the entity carries no stash (non-players). */
    public static LazyOptional<SoulboundStash> get(Player player) {
        return player.getCapability(CAPABILITY);
    }
}
