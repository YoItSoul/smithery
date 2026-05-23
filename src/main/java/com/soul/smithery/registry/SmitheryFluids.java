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
 * Per-material molten fluids.
 *
 * For every registered Material with a non-zero meltingTemp we create:
 *   - a {@link FluidType} (NeoForge concept) carrying physics props
 *   - a source {@link FlowingFluid} (BaseFlowingFluid.Source)
 *   - a flowing {@link FlowingFluid} (BaseFlowingFluid.Flowing)
 *   - a {@link LiquidBlock} so the fluid can exist in the world
 *
 * Bucket items are intentionally NOT registered yet — buckets / drain output
 * comes later in the design. The fluids exist now so the casting basin and
 * the drain can wire to real fluid identities instead of opaque material IDs.
 *
 * All molten fluids share one pair of greyscale textures (smithery:block/molten_still
 * and smithery:block/molten_flow, both copied + greyscaled from vanilla lava).
 * Per-fluid color comes from the Material's {@link MaterialStats#moltenColor()},
 * applied via a {@code BlockTintSource} registered with the fluid model in
 * {@code SmitheryFluidsClient}.
 */
public final class SmitheryFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Smithery.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Smithery.MODID);
    public static final DeferredRegister.Blocks FLUID_BLOCKS =
            DeferredRegister.createBlocks(Smithery.MODID);

    /** Holders for one material's molten fluid set. */
    public static final class Entry {
        public final Identifier materialId;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, FlowingFluid> source;
        public final DeferredHolder<Fluid, FlowingFluid> flowing;
        public final com.soul.smithery.api.material.Material material;
        // LiquidBlock is registered as a Block; field declared with the generic type
        // to keep callers honest about what they get back.
        public final net.neoforged.neoforge.registries.DeferredBlock<LiquidBlock> block;
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
    /** Reverse index: bucket item id → entry, used by MoltenBucketTintSource. */
    private static final Map<Identifier, Entry> ENTRIES_BY_BUCKET_ID = new java.util.HashMap<>();

    /** All registered molten-fluid entries, in registration order. */
    public static Map<Identifier, Entry> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    /** Lookup an entry by the registry id of its bucket item; null if not a smithery molten bucket. */
    public static Entry forBucketItemId(Identifier bucketItemId) {
        return ENTRIES_BY_BUCKET_ID.get(bucketItemId);
    }

    /** Lookup the molten fluid for a given Material id, or null if it has none (e.g. wood). */
    public static Entry forMaterial(Identifier materialId) {
        return ENTRIES.get(materialId);
    }

    /** Reverse lookup: find the entry whose source fluid matches the given Fluid; null if none. */
    public static Entry forFluid(net.minecraft.world.level.material.Fluid fluid) {
        if (fluid == null) return null;
        for (Entry e : ENTRIES.values()) {
            if (e.source.get() == fluid) return e;
        }
        return null;
    }

    /**
     * Bootstrap entries from the populated {@link SmitheryAPI#MATERIALS} registry.
     * Call AFTER SmitheryMaterials.register() and BEFORE the deferred registers fire
     * (i.e. before bus.register is invoked).
     */
    public static void bootstrap() {
        for (Material mat : SmitheryAPI.MATERIALS.all()) {
            MaterialStats stats = mat.stats();
            if (stats.meltingTemp() <= 0f) continue; // skip non-meltable (e.g. wood)
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
                        .density(7000)            // heavier than water; we don't have per-material density yet
                        .viscosity(6000)          // sluggish like lava
                        .temperature(Math.max(300, tempCelsius))
                        .canSwim(false)
                        .canDrown(false)
                        .canPushEntity(false)
                        .canHydrate(false)
                        .canExtinguish(false)
                        .canConvertToSource(false)
                        .supportsBoating(false)));

        // Forward references resolved via Lazy-style anon holders below — Source/Flowing/Block/Bucket
        // each reference each other through Properties; the holders are wired before they're
        // dereferenced (registry suppliers fire later, by which time .get() resolves cleanly).
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

        // Use registerBlock(name, func, propsSupplier) so the block id (ResourceKey) is
        // baked into BlockBehaviour.Properties — required in MC 26.1.x; Properties.of()
        // alone fails at construct time with "Block id not set".
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

        // Standard 1-stack bucket that returns an empty bucket on use. The visual is
        // a shared grayscale sprite (smithery:item/molten_bucket) recolored per-fluid
        // by MoltenBucketTintSource on the client.
        bucketRef[0] = SmitheryItems.ITEMS.registerItem(name + "_bucket",
                itemProps -> new net.minecraft.world.item.BucketItem(sourceRef[0].get(), itemProps),
                p -> p.craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));

        Entry entry = new Entry(matId, material, type, sourceRef[0], flowingRef[0], blockRef[0], bucketRef[0]);
        ENTRIES.put(matId, entry);
        ENTRIES_BY_BUCKET_ID.put(Identifier.fromNamespaceAndPath(Smithery.MODID, name + "_bucket"), entry);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        FLUID_BLOCKS.register(bus);
    }

    private SmitheryFluids() {}
}
