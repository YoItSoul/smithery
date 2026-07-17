package com.soul.smithery.client;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Swaps in full-bright models for emissive materials at model-bake time.
 *
 * <p>Two strategies, matching how emissiveness can be decided:
 * <ul>
 *   <li><b>Part items</b> — each (material × part type) pair is its own item, so emissiveness
 *       is known statically: the part's inventory model is replaced outright with a
 *       {@link FullbrightBakedModel}. Zero per-frame cost.</li>
 *   <li><b>Composed gear</b> — one item (and one model) is shared by every material combo, so
 *       the decision is per-stack: the model is wrapped so its {@link ItemOverrides} resolve
 *       to the full-bright variant when the stack's composition contains an emissive material.
 *       The NBT check per resolve mirrors what the tint handlers already do per frame.</li>
 * </ul>
 *
 * <p>Materials are registered during mod construction (part items depend on them), so they are
 * always available by the time the first model bake fires.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EmissiveModels {

    private EmissiveModels() {}

    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        FullbrightBakedModel.clearCache();
        Map<ResourceLocation, BakedModel> models = event.getModels();

        int swapped = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof PartItem part)) continue;
            Material material = part.material();
            if (material == null || !material.stats().emissive()) continue;
            if (replaceInventoryModel(models, item, FullbrightBakedModel::of)) swapped++;
        }

        int wrapped = 0;
        for (Item item : SmitheryItemColors.composedGear()) {
            if (replaceInventoryModel(models, item, ComposedEmissiveModel::new)) wrapped++;
        }

        Smithery.LOGGER.debug("Emissive models: {} part models swapped, {} composed models wrapped.",
                swapped, wrapped);
    }

    /** Replaces the item's {@code #inventory} model via {@code wrapper}; true if it existed. */
    private static boolean replaceInventoryModel(Map<ResourceLocation, BakedModel> models, Item item,
                                                 java.util.function.Function<BakedModel, BakedModel> wrapper) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) return false;
        ModelResourceLocation key = new ModelResourceLocation(itemId, "inventory");
        BakedModel existing = models.get(key);
        if (existing == null) return false;
        models.put(key, wrapper.apply(existing));
        return true;
    }

    /**
     * True when any slot material of {@code stack}'s composition is emissive. Same NBT-parse
     * cost class as the per-frame tint handlers; cheap null-tag short-circuit first.
     */
    public static boolean hasEmissiveMaterial(ItemStack stack) {
        if (stack.getTag() == null) return false;
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null) return false;
        for (ResourceLocation matId : comp.slotMaterials()) {
            Material m = matId != null ? SmitheryAPI.MATERIALS.get(matId) : null;
            if (m != null && m.stats().emissive()) return true;
        }
        return false;
    }

    /**
     * Wrapper for composed-gear models: defers to the original model's overrides (bow pull,
     * etc.), then upgrades the resolved model to full-bright when the stack contains an
     * emissive material.
     */
    private static final class ComposedEmissiveModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        ComposedEmissiveModel(BakedModel base) {
            super(base);
            this.overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack,
                                          @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    BakedModel resolved = base.getOverrides().resolve(base, stack, level, entity, seed);
                    if (resolved == null) resolved = base;
                    return hasEmissiveMaterial(stack) ? FullbrightBakedModel.of(resolved) : resolved;
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
