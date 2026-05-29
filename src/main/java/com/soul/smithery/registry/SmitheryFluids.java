package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-{@link Material} molten fluid registry.
 *
 * <p>For every registered Material with a non-zero {@link MaterialStats#meltingTemp()} this
 * class registers a {@link FluidType}, a source + flowing {@link FlowingFluid}, a
 * {@link LiquidBlock} and a bucket {@code Item}. All molten fluids share one pair of
 * greyscale textures; per-fluid color comes from {@link MaterialStats#moltenColor()}
 * applied via a {@code BlockTintSource} on the client.
 */
public final class SmitheryFluids {

    /** Deferred register for Smithery-namespaced fluid types. */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Smithery.MODID);
    /** Deferred register for Smithery-namespaced fluids (source + flowing). */
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Smithery.MODID);
    /** Deferred register for Smithery-namespaced {@link LiquidBlock} instances. */
    public static final DeferredRegister.Blocks FLUID_BLOCKS =
            DeferredRegister.createBlocks(Smithery.MODID);

    /**
     * Holder bundle for one material's molten-fluid set (type, source, flowing, block, bucket).
     */
    public static final class Entry {
        /** Identifier of the {@link Material} this fluid set was derived from. */
        public final Identifier materialId;
        /** Deferred holder for this material's {@link FluidType}. */
        public final DeferredHolder<FluidType, FluidType> type;
        /** Deferred holder for the source (still) {@link FlowingFluid} variant. */
        public final DeferredHolder<Fluid, FlowingFluid> source;
        /** Deferred holder for the flowing {@link FlowingFluid} variant. */
        public final DeferredHolder<Fluid, FlowingFluid> flowing;
        /** The {@link Material} this fluid set was derived from. */
        public final com.soul.smithery.api.material.Material material;
        /** Deferred holder for the in-world {@link LiquidBlock} backing this fluid. */
        public final net.neoforged.neoforge.registries.DeferredBlock<LiquidBlock> block;
        /** Deferred holder for the bucket item that picks up this fluid. */
        public final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BucketItem> bucket;

        Entry(Identifier materialId,
              com.soul.smithery.api.material.Material material,
              DeferredHolder<FluidType, FluidType> type,
              DeferredHolder<Fluid, FlowingFluid> source,
              DeferredHolder<Fluid, FlowingFluid> flowing,
              net.neoforged.neoforge.registries.DeferredBlock<LiquidBlock> block,
              net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BucketItem> bucket) {
            this.materialId = materialId;
            this.material   = material;
            this.type       = type;
            this.source     = source;
            this.flowing    = flowing;
            this.block      = block;
            this.bucket     = bucket;
        }
    }

    private static final Map<Identifier, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<Identifier, Entry> ENTRIES_BY_BUCKET_ID = new java.util.HashMap<>();

    /**
     * All registered molten-fluid entries in registration order.
     *
     * @return unmodifiable view of the material-id → {@link Entry} map
     */
    public static Map<Identifier, Entry> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    /**
     * Reverse lookup by bucket item id.
     *
     * @param bucketItemId registry id of a bucket item
     * @return the matching {@link Entry}, or null if the id is not a Smithery molten bucket
     */
    public static Entry forBucketItemId(Identifier bucketItemId) {
        return ENTRIES_BY_BUCKET_ID.get(bucketItemId);
    }

    /**
     * Looks up the molten fluid entry for a given {@link Material} id.
     *
     * @param materialId id of a registered Material
     * @return the matching {@link Entry}, or null if the material has no molten fluid (e.g. wood)
     */
    public static Entry forMaterial(Identifier materialId) {
        return ENTRIES.get(materialId);
    }

    /**
     * Reverse lookup from a vanilla {@link Fluid} back to its Smithery entry.
     *
     * @param fluid any fluid; may be null
     * @return the matching {@link Entry} whose source equals {@code fluid}, or null if none
     */
    public static Entry forFluid(net.minecraft.world.level.material.Fluid fluid) {
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

    private static void registerOne(Material material) {
        Identifier matId = material.id();
        String name = "molten_" + matId.getPath();
        MaterialStats stats = material.stats();
        int tempCelsius = (int) stats.meltingTemp();

        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name,
                () -> new FluidType(FluidType.Properties.create()
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
                        .supportsBoating(false)));

        DeferredHolder<Fluid, FlowingFluid>[] sourceRef = new DeferredHolder[1];
        DeferredHolder<Fluid, FlowingFluid>[] flowingRef = new DeferredHolder[1];
        net.neoforged.neoforge.registries.DeferredBlock<LiquidBlock>[] blockRef =
                new net.neoforged.neoforge.registries.DeferredBlock[1];
        net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BucketItem>[] bucketRef =
                new net.neoforged.neoforge.registries.DeferredItem[1];

        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                type,
                () -> sourceRef[0].get(),
                () -> flowingRef[0].get())
                .block(() -> blockRef[0].get())
                .bucket(() -> bucketRef[0].get())
                .explosionResistance(100f)
                .tickRate(30);

        sourceRef[0] = FLUIDS.register(name,
                () -> new BaseFlowingFluid.Source(props));
        flowingRef[0] = FLUIDS.register(name + "_flowing",
                () -> new BaseFlowingFluid.Flowing(props));

        blockRef[0] = FLUID_BLOCKS.registerBlock(name,
                blockProps -> new LiquidBlock(sourceRef[0].get(), blockProps),
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_ORANGE)
                        .replaceable()
                        .noCollision()
                        .randomTicks()
                        .strength(100f)
                        .noLootTable()
                        .liquid()
                        .pushReaction(PushReaction.DESTROY)
                        .sound(SoundType.EMPTY));

        bucketRef[0] = SmitheryItems.ITEMS.registerItem(name + "_bucket",
                itemProps -> new net.minecraft.world.item.BucketItem(sourceRef[0].get(), itemProps),
                p -> p.craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));

        Entry entry = new Entry(matId, material, type, sourceRef[0], flowingRef[0], blockRef[0], bucketRef[0]);
        ENTRIES.put(matId, entry);
        ENTRIES_BY_BUCKET_ID.put(Identifier.fromNamespaceAndPath(Smithery.MODID, name + "_bucket"), entry);
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
