package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-{@link Material} molten fluid registry.
 *
 * <p>For every registered Material with a non-zero {@link MaterialStats#meltingTemp()} this
 * class registers a {@link FluidType}, a source + flowing {@link FlowingFluid}, a
 * {@link LiquidBlock} and a bucket {@code Item}. All molten fluids share one pair of
 * greyscale textures; per-fluid color comes from {@link MaterialStats#moltenColor()}
 * applied via color handlers on the client.
 */
public final class SmitheryFluids {

    /** Deferred register for Smithery-namespaced fluid types. */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Smithery.MODID);
    /** Deferred register for Smithery-namespaced fluids (source + flowing). */
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, Smithery.MODID);
    /** Deferred register for Smithery-namespaced {@link LiquidBlock} instances. */
    public static final DeferredRegister<Block> FLUID_BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Smithery.MODID);

    /**
     * Holder bundle for one material's molten-fluid set (type, source, flowing, block, bucket).
     */
    public static final class Entry {
        /** ResourceLocation of the {@link Material} this fluid set was derived from. */
        public final ResourceLocation materialId;
        /** The {@link Material} this fluid set was derived from. */
        public final Material material;
        /** Registry object for this material's {@link FluidType}. */
        public final RegistryObject<FluidType> type;
        /** Registry object for the source (still) {@link FlowingFluid} variant. */
        public final RegistryObject<FlowingFluid> source;
        /** Registry object for the flowing {@link FlowingFluid} variant. */
        public final RegistryObject<FlowingFluid> flowing;
        /** Registry object for the in-world {@link LiquidBlock} backing this fluid. */
        public final RegistryObject<LiquidBlock> block;
        /** Registry object for the bucket item that picks up this fluid. */
        public final RegistryObject<BucketItem> bucket;

        Entry(ResourceLocation materialId,
              Material material,
              RegistryObject<FluidType> type,
              RegistryObject<FlowingFluid> source,
              RegistryObject<FlowingFluid> flowing,
              RegistryObject<LiquidBlock> block,
              RegistryObject<BucketItem> bucket) {
            this.materialId = materialId;
            this.material   = material;
            this.type       = type;
            this.source     = source;
            this.flowing    = flowing;
            this.block      = block;
            this.bucket     = bucket;
        }
    }

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Entry> ENTRIES_BY_BUCKET_ID = new HashMap<>();

    /**
     * All registered molten-fluid entries in registration order.
     *
     * @return unmodifiable view of the material-id → {@link Entry} map
     */
    public static Map<ResourceLocation, Entry> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    /**
     * Reverse lookup by bucket item id.
     *
     * @param bucketItemId registry id of a bucket item
     * @return the matching {@link Entry}, or null if the id is not a Smithery molten bucket
     */
    public static Entry forBucketItemId(ResourceLocation bucketItemId) {
        return ENTRIES_BY_BUCKET_ID.get(bucketItemId);
    }

    /**
     * Looks up the molten fluid entry for a given {@link Material} id.
     *
     * @param materialId id of a registered Material
     * @return the matching {@link Entry}, or null if the material has no molten fluid (e.g. wood)
     */
    public static Entry forMaterial(ResourceLocation materialId) {
        return ENTRIES.get(materialId);
    }

    /**
     * Reverse lookup from a vanilla {@link Fluid} back to its Smithery entry.
     *
     * @param fluid any fluid; may be null
     * @return the matching {@link Entry} whose source equals {@code fluid}, or null if none
     */
    public static Entry forFluid(Fluid fluid) {
        if (fluid == null) return null;
        for (Entry e : ENTRIES.values()) {
            if (e.source.get() == fluid) return e;
        }
        return null;
    }

    /**
     * Bootstraps molten-fluid entries from the populated {@link SmitheryAPI#MATERIALS}
     * registry.
     *
     * <p>Must be called AFTER {@code SmitheryMaterials.register()} and BEFORE the deferred
     * registers fire (i.e. before {@code bus.register} is invoked).
     */
    public static void bootstrap() {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            MaterialStats stats = mat.stats();
            if (stats.meltingTemp() <= 0f) continue;
            registerOne(mat);
        }
    }

    private static final ResourceLocation MOLTEN_STILL =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "block/molten_still");
    private static final ResourceLocation MOLTEN_FLOW =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "block/molten_flow");
    private static final ResourceLocation WATER_STILL =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
    private static final ResourceLocation WATER_FLOW =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");

    /**
     * FluidType whose client extensions pick one of two animated base sprites — the
     * lava-style smithery molten pair (default) or vanilla water's rippling stills (for
     * water-base materials such as blood) — tinted per-material from
     * {@link MaterialStats#moltenColor()}. The {@code initializeClient} consumer pattern
     * keeps this class safe to construct on the dedicated server.
     */
    private static final class MoltenFluidType extends FluidType {
        private final boolean waterBase;
        private final int tintColor;

        MoltenFluidType(Properties properties, boolean waterBase, int tintColor) {
            super(properties);
            this.waterBase = waterBase;
            this.tintColor = tintColor;
        }

        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() {
                    return waterBase ? WATER_STILL : MOLTEN_STILL;
                }
                @Override public ResourceLocation getFlowingTexture() {
                    return waterBase ? WATER_FLOW : MOLTEN_FLOW;
                }
                @Override public int getTintColor() {
                    return tintColor;
                }
            });
        }
    }

    public static void registerOne(Material material) {
        ResourceLocation matId = material.id();
        String name = "molten_" + matId.getPath();
        MaterialStats stats = material.stats();
        int tempCelsius = (int) stats.meltingTemp();
        boolean waterBase = stats.fluidBase() == MaterialStats.FluidBase.WATER;
        int tintColor = stats.moltenColor() | 0xFF000000;

        RegistryObject<FluidType> type = FLUID_TYPES.register(name,
                () -> new MoltenFluidType(FluidType.Properties.create()
                        .descriptionId("fluid." + Smithery.MODID + "." + name)
                        .lightLevel(15)
                        .density(7000)
                        .viscosity(6000)
                        .temperature(Math.max(300, tempCelsius))
                        .canSwim(false)
                        .canDrown(false)
                        .canPushEntity(false)
                        .canHydrate(false)
                        .canExtinguish(false)
                        .canConvertToSource(false)
                        .supportsBoating(false),
                        waterBase, tintColor));

        // The fluid properties reference source/flowing/block/bucket before those registry
        // objects exist, so single-element arrays break the circular dependency lazily.
        @SuppressWarnings("unchecked")
        RegistryObject<FlowingFluid>[] sourceRef = new RegistryObject[1];
        @SuppressWarnings("unchecked")
        RegistryObject<FlowingFluid>[] flowingRef = new RegistryObject[1];
        @SuppressWarnings("unchecked")
        RegistryObject<LiquidBlock>[] blockRef = new RegistryObject[1];
        @SuppressWarnings("unchecked")
        RegistryObject<BucketItem>[] bucketRef = new RegistryObject[1];

        ForgeFlowingFluid.Properties props = new ForgeFlowingFluid.Properties(
                type,
                () -> sourceRef[0].get(),
                () -> flowingRef[0].get())
                .block(() -> blockRef[0].get())
                .bucket(() -> bucketRef[0].get())
                .explosionResistance(100f)
                .tickRate(30);

        sourceRef[0] = FLUIDS.register(name,
                () -> new ForgeFlowingFluid.Source(props));
        flowingRef[0] = FLUIDS.register(name + "_flowing",
                () -> new ForgeFlowingFluid.Flowing(props));

        blockRef[0] = FLUID_BLOCKS.register(name,
                () -> new LiquidBlock(sourceRef[0], BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_ORANGE)
                        .replaceable()
                        .noCollission()
                        .randomTicks()
                        .strength(100f)
                        .noLootTable()
                        .liquid()
                        .pushReaction(PushReaction.DESTROY)
                        .sound(SoundType.EMPTY)));

        bucketRef[0] = SmitheryItems.ITEMS.register(name + "_bucket",
                () -> new BucketItem(sourceRef[0], new Item.Properties()
                        .craftRemainder(Items.BUCKET)
                        .stacksTo(1)));

        Entry entry = new Entry(matId, material, type, sourceRef[0], flowingRef[0], blockRef[0], bucketRef[0]);
        ENTRIES.put(matId, entry);
        ENTRIES_BY_BUCKET_ID.put(ResourceLocation.fromNamespaceAndPath(Smithery.MODID, name + "_bucket"), entry);
    }

    /**
     * Binds all three deferred registers (types, fluids, fluid-blocks) to the mod event bus.
     *
     * @param bus the mod-bus the deferred registers attach to
     */
    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        FLUID_BLOCKS.register(bus);
    }

    private SmitheryFluids() {}
}
