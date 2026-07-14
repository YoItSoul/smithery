package com.soul.smithery.client;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.registry.SmitheryFluids;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Item color handlers that apply per-material color to Smithery's grayscale item textures —
 * the 1.20.1 equivalent of per-layer tint sources.
 *
 * <p>Three families:
 * <ul>
 *   <li>parts — layer 0 tinted with the part's material color;</li>
 *   <li>composed gear (tools, armor, ranged) — the tint index doubles as the composition slot
 *       index, so each layered-model layer takes its own slot's material color;</li>
 *   <li>molten buckets — layer 1 (the fluid window) tinted with the molten color.</li>
 * </ul>
 *
 * <p>Every handler falls through to opaque white (-1) when the composition or material can't
 * be resolved, so the underlying grayscale layer renders untinted rather than vanishing.
 */
public final class SmitheryItemColors {

    private SmitheryItemColors() {}

    /**
     * Registers all Smithery item color handlers.
     *
     * @param event Forge's item color-handler registration event
     */
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        List<Item> partItems = new ArrayList<>();
        SmitheryItems.builtInParts().values().forEach(part -> partItems.add(part.get()));
        if (!partItems.isEmpty()) {
            event.register(SmitheryItemColors::partColor, partItems.toArray(new Item[0]));
        }

        List<Item> composedItems = new ArrayList<>(List.of(
                SmitheryItems.SWORD.get(), SmitheryItems.PICKAXE.get(), SmitheryItems.AXE.get(),
                SmitheryItems.SHOVEL.get(), SmitheryItems.HOE.get(), SmitheryItems.SPEAR.get(),
                SmitheryItems.BROADSWORD.get(), SmitheryItems.RAPIER.get(), SmitheryItems.PAXEL.get(),
                SmitheryItems.MINING_HAMMER.get(), SmitheryItems.KAMA.get(), SmitheryItems.CLEAVER.get(),
                SmitheryItems.LUMBERAXE.get(), SmitheryItems.EXCAVATOR.get(), SmitheryItems.BATTLESIGN.get(),
                SmitheryItems.BOW.get(), SmitheryItems.CROSSBOW.get(), SmitheryItems.TRIDENT.get(),
                SmitheryItems.ARROW.get(), SmitheryItems.SHURIKEN.get(),
                SmitheryItems.HELMET.get(), SmitheryItems.CHESTPLATE.get(),
                SmitheryItems.LEGGINGS.get(), SmitheryItems.BOOTS.get()));
        event.register(SmitheryItemColors::slotColor, composedItems.toArray(new Item[0]));

        List<Item> buckets = new ArrayList<>();
        SmitheryFluids.entries().values().forEach(entry -> buckets.add(entry.bucket.get()));
        if (!buckets.isEmpty()) {
            event.register(SmitheryItemColors::bucketColor, buckets.toArray(new Item[0]));
        }
    }

    /** Part items: layer 0 takes the part's material color. */
    private static int partColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        if (!(stack.getItem() instanceof PartItem part)) return -1;
        return part.tintColor() | 0xFF000000;
    }

    /** Composed gear: the tint index is the composition slot whose material colors the layer. */
    private static int slotColor(ItemStack stack, int tintIndex) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null) return -1;
        if (tintIndex < 0 || tintIndex >= comp.slotMaterials().size()) return -1;
        ResourceLocation matId = comp.slotMaterials().get(tintIndex);
        if (matId == null) return -1;
        Material mat = SmitheryAPI.MATERIALS.get(matId);
        if (mat == null) return -1;
        return mat.stats().partColor() | 0xFF000000;
    }

    /** Molten buckets: layer 1 (the fluid window) takes the molten color. */
    private static int bucketColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        SmitheryFluids.Entry entry = SmitheryFluids.forBucketItemId(itemId);
        if (entry == null) return -1;
        return entry.material.stats().moltenColor() | 0xFF000000;
    }
}
