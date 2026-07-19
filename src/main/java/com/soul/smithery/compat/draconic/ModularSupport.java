package com.soul.smithery.compat.draconic;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.AOEData;
import com.brandon3055.draconicevolution.api.modules.data.DamageData;
import com.brandon3055.draconicevolution.api.modules.data.SpeedData;
import com.brandon3055.draconicevolution.api.modules.lib.ModularOPStorage;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.init.ModuleCfg;
import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared plumbing for DE module hosting on Smithery gear.
 *
 * <p>Smithery items are composition-dynamic — one item per tool type, materials in NBT — so
 * unlike DE's own gear the module grid cannot be fixed at item construction. The
 * {@link Provider} attached from {@code initCapabilities} therefore resolves lazily: on each
 * capability query it re-reads the stack's composition until a draconic-tier material appears,
 * then builds the {@link ModuleHostImpl} (grid sized from DE's own {@code ModuleCfg}, so DE
 * config tweaks apply to Smithery gear too) and a {@link ModularOPStorage}. Stacks with no
 * draconic material never expose the capability, so DE's GUI refuses them exactly like any
 * non-modular item.
 */
final class ModularSupport {

    private ModularSupport() {}

    /** Highest registered tech tier among the stack's composition materials, or null. */
    @Nullable
    static TechLevel tierOf(ItemStack stack) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null) return null;
        int best = -1;
        for (ResourceLocation mat : comp.slotMaterials()) {
            Integer tier = DraconicCompat.materialTiers().get(mat);
            if (tier != null && tier > best) best = tier;
        }
        return best < 0 ? null : TechLevel.byIndex(best);
    }

    /** Reads combined module data of the given type from the stack's host, or null. */
    @Nullable
    static <T extends com.brandon3055.draconicevolution.api.modules.data.ModuleData<T>>
    T moduleData(ItemStack stack, com.brandon3055.draconicevolution.api.modules.ModuleType<T> type) {
        ModuleHost host = stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).orElse(null);
        return host == null ? null : host.getModuleData(type);
    }

    /** Extra attack damage from installed DAMAGE modules. */
    static double moduleDamage(ItemStack stack) {
        DamageData data = moduleData(stack, ModuleTypes.DAMAGE);
        return data == null ? 0 : data.damagePoints();
    }

    /** Dig/attack speed multiplier bonus from installed SPEED modules (0 = none). */
    static double moduleSpeed(ItemStack stack) {
        SpeedData data = moduleData(stack, ModuleTypes.SPEED);
        return data == null ? 0 : data.speedMultiplier();
    }

    /** AOE radius from installed AOE modules (0 = none). */
    static int moduleAoe(ItemStack stack) {
        AOEData data = moduleData(stack, ModuleTypes.AOE);
        return data == null ? 0 : data.aoe();
    }

    /**
     * Drains module energy to absorb durability damage: each point of prevented damage costs
     * {@link #ENERGY_PER_DURABILITY}. Returns the damage that could NOT be absorbed.
     */
    static int absorbDamageWithEnergy(ItemStack stack, int amount) {
        if (amount <= 0) return 0;
        IEnergyStorage energy = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (energy == null) return amount;
        int absorbable = (int) Math.min(amount, energy.getEnergyStored() / (long) ENERGY_PER_DURABILITY);
        if (absorbable > 0) {
            energy.extractEnergy(absorbable * ENERGY_PER_DURABILITY, false);
        }
        return amount - absorbable;
    }

    /** FE drained per point of durability damage absorbed by energy modules. */
    static final int ENERGY_PER_DURABILITY = 500;

    /**
     * Lazy stack capability provider exposing DE's module host, property provider, and energy
     * storage once the stack's composition includes a draconic-tier material.
     *
     * <p>Extends BrandonsCore's {@code MultiCapabilityProvider} purely to satisfy
     * {@code IModularItem.initCapabilities}'s covariant return type — every inherited behavior
     * is overridden; the superclass holds no state for this provider.
     */
    static final class Provider extends com.brandon3055.brandonscore.capability.MultiCapabilityProvider
            implements ICapabilitySerializable<CompoundTag> {
        private final ItemStack stack;
        private final String providerName;
        private final boolean chestpieceGrid;
        private final com.brandon3055.draconicevolution.api.modules.ModuleCategory[] categories;

        private ModuleHostImpl host;
        private ModularOPStorage opStorage;
        private CompoundTag pendingNbt;

        /**
         * @param stack the stack this provider is bound to
         * @param providerName stable name for the host (tool-type path; DE uses e.g. "sword")
         * @param chestpieceGrid true to size the grid from DE's chestpiece config instead of tools
         * @param categories host module categories — modules whose categories don't intersect
         *        these are refused by the grid (DE armor modules carry CHESTPIECE)
         */
        Provider(ItemStack stack, String providerName, boolean chestpieceGrid,
                 com.brandon3055.draconicevolution.api.modules.ModuleCategory... categories) {
            this.stack = stack;
            this.providerName = providerName;
            this.chestpieceGrid = chestpieceGrid;
            this.categories = categories;
        }

        private void ensureResolved() {
            if (host != null) return;
            TechLevel tier = tierOf(stack);
            if (tier == null) return;
            // DE's grid/energy config has no draconium row (DE ships no draconium gear) —
            // draconium hosts borrow the wyvern dimensions but keep their true tech level.
            TechLevel dims = tier == TechLevel.DRACONIUM ? TechLevel.WYVERN : tier;
            int w = chestpieceGrid ? ModuleCfg.chestpieceWidth(dims) : ModuleCfg.toolWidth(dims);
            int h = chestpieceGrid ? ModuleCfg.chestpieceHeight(dims) : ModuleCfg.toolHeight(dims);
            host = new ModuleHostImpl(tier, w, h, providerName, ModuleCfg.removeInvalidModules, categories);
            // Chestpieces use DE's chestpiece energy pool (larger, shield-oriented), tools the
            // tool pool — mirroring ModularChestpiece/ModularSword exactly, config mults included.
            long baseEnergy = chestpieceGrid
                    ? EquipCfg.getBaseChestpieceEnergy(dims) : EquipCfg.getBaseToolEnergy(dims);
            long baseTransfer = chestpieceGrid
                    ? EquipCfg.getBaseChestpieceTransfer(dims) : EquipCfg.getBaseToolTransfer(dims);
            opStorage = new ModularOPStorage(host, baseEnergy, baseTransfer);
            if (pendingNbt != null) {
                readInto(pendingNbt);
                pendingNbt = null;
            }
        }

        private void readInto(CompoundTag tag) {
            if (tag.contains("host")) host.deserializeNBT(tag.getCompound("host"));
            if (tag.contains("energy")) opStorage.deserializeNBT(tag.getCompound("energy"));
        }

        @Override
        @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            boolean wantsHost = cap == DECapabilities.MODULE_HOST_CAPABILITY
                    || cap == DECapabilities.PROPERTY_PROVIDER_CAPABILITY;
            boolean wantsEnergy = cap == DECapabilities.OP_STORAGE || cap == ForgeCapabilities.ENERGY;
            if (!wantsHost && !wantsEnergy) return LazyOptional.empty();
            ensureResolved();
            if (host == null) return LazyOptional.empty();
            if (wantsHost) return LazyOptional.of(() -> host).cast();
            if (cap == ForgeCapabilities.ENERGY && !(opStorage instanceof IEnergyStorage)) {
                return LazyOptional.empty();
            }
            return LazyOptional.of(() -> opStorage).cast();
        }

        @Override
        public CompoundTag serializeNBT() {
            if (host == null) {
                return pendingNbt != null ? pendingNbt.copy() : new CompoundTag();
            }
            CompoundTag tag = new CompoundTag();
            tag.put("host", host.serializeNBT());
            tag.put("energy", opStorage.serializeNBT());
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            if (host != null) {
                readInto(tag);
            } else {
                pendingNbt = tag.copy();
            }
        }
    }
}
