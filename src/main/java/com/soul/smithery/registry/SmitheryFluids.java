package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.item.PartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
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

    /**
     * Composed display name for a material's fluid and its in-world block. Molten-base materials
     * render as {@code "Molten <material>"}; water-base ones (e.g. blood) render as the bare
     * material name, since "Molten Blood" reads wrong. The material name comes from the material
     * atom, so any material — including ones contributed at runtime by other mods — renders
     * correctly with no per-fluid lang entry.
     *
     * @param materialId id of the source material
     * @return the composed fluid/block display name
     */
    public static MutableComponent moltenName(ResourceLocation materialId) {
        MutableComponent matName = Component.translatable(PartItem.materialTranslationKey(materialId));
        return isWaterBase(materialId) ? matName : Component.translatable("smithery.molten.name", matName);
    }

    /**
     * Composed display name for a material's bucket: {@code "Molten <material> Bucket"} for
     * molten-base materials, {@code "<material> Bucket"} for water-base ones. See {@link #moltenName}.
     *
     * @param materialId id of the source material
     * @return the composed bucket display name
     */
    public static MutableComponent moltenBucketName(ResourceLocation materialId) {
        MutableComponent matName = Component.translatable(PartItem.materialTranslationKey(materialId));
        return Component.translatable(
                isWaterBase(materialId) ? "smithery.molten.bucket_water" : "smithery.molten.bucket", matName);
    }

    private static boolean isWaterBase(ResourceLocation materialId) {
        Material mat = SmitheryAPI.MATERIALS.get(materialId);
        return mat != null && mat.stats().fluidBase() == MaterialStats.FluidBase.WATER;
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
        private final com.soul.smithery.api.material.MaterialStats stats;
        private final ResourceLocation materialId;

        MoltenFluidType(Properties properties, boolean waterBase,
                        com.soul.smithery.api.material.MaterialStats stats, ResourceLocation materialId) {
            super(properties);
            this.waterBase = waterBase;
            this.stats = stats;
            this.materialId = materialId;
        }

        // Fluid display name (JEI ingredients, tanks, Jade) composes from the material's own name
        // rather than a per-fluid lang key — see SmitheryFluids#moltenName.
        @Override
        public Component getDescription() {
            return moltenName(materialId);
        }

        // Both description overloads must be overridden: FluidType#getDescription(FluidStack)
        // builds its own Component from getDescriptionId(stack) instead of delegating to the
        // no-arg form, and FluidStack#getDisplayName routes through this overload. Leaving it
        // to super is what surfaces the raw "fluid_type.smithery.molten_<material>" key in
        // every stack-based consumer.
        @Override
        public Component getDescription(FluidStack stack) {
            return moltenName(materialId);
        }

        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
            // Color-cycling materials get PRE-TINTED animated sprites baked by
            // SmitheryGeneratedPack — the base flow animation's frames are multiplied through
            // the material's palette so the world render animates without chunk rebuilds.
            // Those sprites render with a white tint; static materials keep the shared
            // grayscale sprites tinted by moltenColor (now derived from partColor by default).
            //
            // CRITICAL: FluidType's constructor calls this method BEFORE subclass fields are
            // assigned — every field read must stay lazy, inside the extension's methods.
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                private boolean cycle() { return !waterBase && stats.hasColorCycle(); }

                @Override public ResourceLocation getStillTexture() {
                    if (cycle()) return ResourceLocation.fromNamespaceAndPath(Smithery.MODID,
                            "block/molten_" + materialId.getPath() + "_cycle_still");
                    return waterBase ? WATER_STILL : MOLTEN_STILL;
                }
                @Override public ResourceLocation getFlowingTexture() {
                    if (cycle()) return ResourceLocation.fromNamespaceAndPath(Smithery.MODID,
                            "block/molten_" + materialId.getPath() + "_cycle_flow");
                    return waterBase ? WATER_FLOW : MOLTEN_FLOW;
                }
                @Override public int getTintColor() {
                    if (cycle()) return 0xFFFFFFFF;
                    return stats.moltenColor() | 0xFF000000;
                }
            });
        }
    }

    /**
     * Vanilla-lava fire spread, shared by the molten source and flowing fluids: each random
     * tick either climbs upward looking for air pockets beside flammables to ignite, or
     * torches flammable blocks at its own level — the same two-branch behavior as
     * {@code LavaFluid#randomTick}. Combined with the generated {@code minecraft:lava} fluid
     * tag (entity burn/damage, item destruction), molten metal behaves like lava end to end.
     */
    private static void moltenFireSpreadTick(net.minecraft.world.level.Level level,
                                             net.minecraft.core.BlockPos pos,
                                             net.minecraft.util.RandomSource random) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)) return;
        int i = random.nextInt(3);
        if (i > 0) {
            net.minecraft.core.BlockPos target = pos;
            for (int j = 0; j < i; ++j) {
                target = target.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (!level.isLoaded(target)) return;
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(target);
                if (state.isAir()) {
                    if (hasFlammableNeighbours(level, target)) {
                        level.setBlockAndUpdate(target,
                                net.minecraft.world.level.block.BaseFireBlock.getState(level, target));
                        return;
                    }
                } else if (state.blocksMotion()) {
                    return;
                }
            }
        } else {
            for (int k = 0; k < 3; ++k) {
                net.minecraft.core.BlockPos side = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!level.isLoaded(side)) return;
                if (level.isEmptyBlock(side.above()) && level.getBlockState(side).ignitedByLava()) {
                    level.setBlockAndUpdate(side.above(),
                            net.minecraft.world.level.block.BaseFireBlock.getState(level, side.above()));
                }
            }
        }
    }

    private static boolean hasFlammableNeighbours(net.minecraft.world.level.Level level,
                                                  net.minecraft.core.BlockPos pos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            net.minecraft.core.BlockPos side = pos.relative(dir);
            if (level.isLoaded(side) && level.getBlockState(side).ignitedByLava()) return true;
        }
        return false;
    }

    /** Molten source fluid: lava-style fire spread on random tick. */
    private static final class MoltenSource extends ForgeFlowingFluid.Source {
        MoltenSource(ForgeFlowingFluid.Properties props) { super(props); }
        @Override protected boolean isRandomlyTicking() { return true; }
        @Override protected void randomTick(net.minecraft.world.level.Level level,
                                            net.minecraft.core.BlockPos pos,
                                            net.minecraft.world.level.material.FluidState state,
                                            net.minecraft.util.RandomSource random) {
            moltenFireSpreadTick(level, pos, random);
        }
    }

    /** Molten flowing fluid: lava-style fire spread on random tick. */
    private static final class MoltenFlowing extends ForgeFlowingFluid.Flowing {
        MoltenFlowing(ForgeFlowingFluid.Properties props) { super(props); }
        @Override protected boolean isRandomlyTicking() { return true; }
        @Override protected void randomTick(net.minecraft.world.level.Level level,
                                            net.minecraft.core.BlockPos pos,
                                            net.minecraft.world.level.material.FluidState state,
                                            net.minecraft.util.RandomSource random) {
            moltenFireSpreadTick(level, pos, random);
        }
    }

    public static void registerOne(Material material) {
        ResourceLocation matId = material.id();
        String name = "molten_" + matId.getPath();
        MaterialStats stats = material.stats();
        int tempCelsius = (int) stats.meltingTemp();
        boolean waterBase = stats.fluidBase() == MaterialStats.FluidBase.WATER;

        RegistryObject<FluidType> type = FLUID_TYPES.register(name,
                () -> new MoltenFluidType(FluidType.Properties.create()
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
                        waterBase, stats, matId));

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
                () -> waterBase ? new ForgeFlowingFluid.Source(props) : new MoltenSource(props));
        flowingRef[0] = FLUIDS.register(name + "_flowing",
                () -> waterBase ? new ForgeFlowingFluid.Flowing(props) : new MoltenFlowing(props));

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
                        .sound(SoundType.EMPTY)) {
                    // In-world block name (Jade/TOP) composes from the material name.
                    @Override
                    public MutableComponent getName() {
                        return moltenName(matId);
                    }
                });

        // Molten buckets double as furnace fuel, scaled by how hot the metal is: a lava
        // bucket's 20,000 ticks at a 1000°C reference, so molten copper roughly matches lava
        // while draconic-tier metals are dense late-game fuel. Clamped to [2,000 .. 160,000]
        // (a tenth of, up to eight times, a lava bucket); the empty bucket stays behind via
        // the craft remainder, same as vanilla lava. Water-base fluids don't burn.
        final int burnTime = waterBase ? 0
                : Math.max(2000, Math.min(160_000, (int) (20_000L * Math.max(1, tempCelsius) / 1000L)));
        bucketRef[0] = SmitheryItems.ITEMS.register(name + "_bucket",
                () -> new BucketItem(sourceRef[0], new Item.Properties()
                        .craftRemainder(Items.BUCKET)
                        .stacksTo(1)) {
                    @Override
                    public Component getName(ItemStack stack) {
                        return moltenBucketName(matId);
                    }

                    @Override
                    public int getBurnTime(ItemStack stack,
                                           @org.jetbrains.annotations.Nullable net.minecraft.world.item.crafting.RecipeType<?> recipeType) {
                        return burnTime;
                    }
                });

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
