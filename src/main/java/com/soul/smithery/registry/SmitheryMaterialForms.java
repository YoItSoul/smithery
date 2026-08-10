package com.soul.smithery.registry;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastBlocks;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.content.SmitheryPartTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Storage forms — an ingot and a 9× storage block — for materials that declare
 * {@code MaterialStats.Builder#storageForms()}.
 *
 * <p>A material with no item anywhere in the pack has nowhere to live: an alloy that only ever
 * exists as a molten fluid cannot be put in a chest, and with no item there is nothing to melt back
 * into it. Declaring {@code storageForms()} asks Smithery to mint both, so any alloy a packmaker
 * invents is storable and reversible without them having to author items, models or textures.</p>
 *
 * <p>Forms land in the namespace of whichever {@link DeferredRegister} is passed in, matching how
 * {@link SmitheryItems#registerPartsFor} already places part items with their owning mod. Registry
 * names replace dots with underscores, since material ids may contain them
 * ({@code ma.base_essence} → {@code ma_base_essence_ingot}) and registry names may not.</p>
 *
 * <p>Both forms render from one greyscale template tinted with the material's
 * {@code partColor}, so a hundred materials cost two textures.</p>
 */
public final class SmitheryMaterialForms {

    /** Ingot and storage block minted for one material. */
    public record Forms(ResourceLocation materialId,
                        RegistryObject<Item> ingot,
                        RegistryObject<Block> block,
                        RegistryObject<Item> blockItem) {

        /** Registry path of the ingot, e.g. {@code manyullyn_ingot}. */
        public String ingotPath() { return registryBase(materialId) + "_ingot"; }

        /** Registry path of the storage block, e.g. {@code manyullyn_block}. */
        public String blockPath() { return registryBase(materialId) + "_block"; }
    }

    private static final Map<ResourceLocation, Forms> FORMS = new LinkedHashMap<>();

    /** Matches SmitheryMeltingRecipes: ingot = 144 mB, block = 1296 mB. */
    private static final int INGOT_MB = 144;
    private static final int BLOCK_MB = 1296;

    private static final BlockBehaviour.Properties BLOCK_PROPS = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 6.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL);

    /** Material ids may contain dots; registry names may not. */
    public static String registryBase(ResourceLocation materialId) {
        return materialId.getPath().replace('.', '_');
    }

    /**
     * Registers storage forms for every material in {@code namespace} that asked for them.
     *
     * <p>Call from the owning mod's constructor, after its materials are in {@link SmitheryAPI}
     * and before its registers are attached to the mod event bus — the same ordering
     * {@link SmitheryItems#registerBuiltInParts()} requires.</p>
     *
     * @param namespace    only materials whose id sits in this namespace are considered
     * @param targetItems  the item register that should own the ingots and block items
     * @param targetBlocks the block register that should own the storage blocks
     */
    public static void registerDeclaredForms(String namespace,
                                             DeferredRegister<Item> targetItems,
                                             DeferredRegister<Block> targetBlocks) {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            if (!namespace.equals(mat.id().getNamespace())) continue;
            if (!mat.stats().storageForms()) continue;
            registerFormsFor(mat.id(), targetItems, targetBlocks);
        }
    }

    /**
     * Registers an ingot and storage block for one material, whether or not it declared
     * {@code storageForms()}. Use {@link #registerDeclaredForms} unless you are minting forms for a
     * material you do not own.
     *
     * @return the newly registered pair, or the existing pair if this material already has one
     */
    public static Forms registerFormsFor(ResourceLocation materialId,
                                         DeferredRegister<Item> targetItems,
                                         DeferredRegister<Block> targetBlocks) {
        Forms existing = FORMS.get(materialId);
        if (existing != null) return existing;

        String base = registryBase(materialId);
        RegistryObject<Item> ingot =
                targetItems.register(base + "_ingot", () -> new Item(new Item.Properties()));
        RegistryObject<Block> block =
                targetBlocks.register(base + "_block", () -> new Block(BLOCK_PROPS));
        RegistryObject<Item> blockItem =
                targetItems.register(base + "_block", () -> new BlockItem(block.get(), new Item.Properties()));

        Forms forms = new Forms(materialId, ingot, block, blockItem);
        FORMS.put(materialId, forms);
        BY_REGISTRY_NAME.put(ingot.getId().toString(), Kind.INGOT);
        BY_REGISTRY_NAME.put(blockItem.getId().toString(), Kind.BLOCK);

        // The point of a storage form is that it round-trips: what the forge poured out can be
        // poured back in. Registered here rather than in a datapack so it cannot drift from the
        // items that were actually minted.
        // A material with no melting temperature never gets a fluid, so melting its ingot would
        // lead nowhere. Those are the part-builder materials of the Tinkers world: route their
        // ingot through the press instead, which is the craftable rather than castable path.
        Material mat = SmitheryAPI.MATERIALS.get(materialId);
        boolean meltable = mat != null && mat.stats().meltingTemp() > 0.0F;
        if (meltable) {
            SmitheryAPI.registerMeltingRecipe(ingot.getId().toString(), materialId.toString(), INGOT_MB);
            SmitheryAPI.registerMeltingRecipe(blockItem.getId().toString(), materialId.toString(), BLOCK_MB);
            // The other half of the round trip: a Casting Basin pours the fluid back into the block
            // at the same portion the forge melts it for. A generated form is the fallback for a
            // material with nowhere else to live, so it never displaces a hand-registered cast.
            if (CastBlocks.resolve(materialId) == null) {
                CastBlocks.register(materialId, BLOCK_MB, blockItem::get);
            }
            // Same for the ingot on a Casting Table. The ingot shape is a synthetic cast, so it
            // resolves only through CastResults — without this the minted ingot would melt down but
            // could never be cast back, leaving the storable form a one-way trip.
            if (!CastResults.hasResult(materialId, SmitheryPartTypes.INGOT.id())) {
                CastResults.register(materialId, SmitheryPartTypes.INGOT.id(), ingot::get);
            }
        } else {
            SmitheryAPI.registerPressInput(ingot.getId(), materialId);
        }
        return forms;
    }

    /** Every material that has storage forms, in registration order. */
    public static Map<ResourceLocation, Forms> all() {
        return Collections.unmodifiableMap(FORMS);
    }

    /** The forms for one material, or null when it has none. */
    public static Forms get(ResourceLocation materialId) {
        return FORMS.get(materialId);
    }

    /** Which half of a pair a registry entry is. */
    public enum Kind { INGOT, BLOCK }

    /** "namespace:path" of every registered form, so the resource pack can answer for any namespace. */
    private static final Map<String, Kind> BY_REGISTRY_NAME = new LinkedHashMap<>();

    /**
     * Identifies a generated form by the namespace and path the game is asking about.
     *
     * <p>Forms are minted into the owning mod's namespace, so the generated resource pack cannot
     * assume {@code smithery:} the way it can for parts and fluids.</p>
     *
     * @return which half of the pair this is, or null when the name is not a generated form
     */
    public static Kind kindOf(String namespace, String path) {
        return BY_REGISTRY_NAME.get(namespace + ":" + path);
    }

    /** The material a generated form belongs to, or null when the name is not a form. */
    public static ResourceLocation materialFor(String namespace, String path) {
        String key = namespace + ":" + path;
        for (Forms f : FORMS.values()) {
            if (key.equals(f.ingot().getId().toString()) || key.equals(f.blockItem().getId().toString())) {
                return f.materialId();
            }
        }
        return null;
    }

    private SmitheryMaterialForms() {}
}
