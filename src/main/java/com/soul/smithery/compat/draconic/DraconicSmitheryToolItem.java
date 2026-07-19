package com.soul.smithery.compat.draconic;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.modules.lib.ModularOPStorage;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.init.ModuleCfg;
import com.brandon3055.draconicevolution.items.equipment.IModularItem;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * DE-aware Smithery tool. Registered in place of {@link SmitheryToolItem} whenever Draconic
 * Evolution is installed; behaves identically until the tool is composed from a draconic-tier
 * material (see {@link DraconicCompat#registerMaterialTier}), at which point it exposes DE's
 * {@code ModuleHost} capability: DE's module GUI opens on it via DE's own keybind, modules
 * install into a grid sized from DE's config, and energy modules give it an FE buffer that
 * absorbs durability damage.
 *
 * <p>Implements {@link IModularItem} so DE-internal paths (module tick contexts, fusion data
 * transfer, GUI sync share-tags) treat it as a first-class modular item.
 */
public class DraconicSmitheryToolItem extends SmitheryToolItem implements IModularItem {

    private static final UUID MODULE_DAMAGE_UUID = UUID.fromString("c1c86644-6f1c-4534-9160-3e1a86d1ab6b");

    /** Re-entrancy guard for AOE harvesting — breaking neighbors re-enters mineBlock. */
    private static final ThreadLocal<Boolean> AOE_BREAKING = ThreadLocal.withInitial(() -> false);

    public DraconicSmitheryToolItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties, toolTypeId);
    }

    // ------------------------------------------------------------------ IModularItem contract

    @Override
    public TechLevel getTechLevel() {
        // Stack-independent fallback only (rarity/name tinting in DE code paths); the real
        // per-stack tier lives on the host built from the tool's composition.
        return TechLevel.DRACONIC;
    }

    @Override
    public ModuleHostImpl createHost(ItemStack stack) {
        TechLevel tier = ModularSupport.tierOf(stack);
        if (tier == null) tier = TechLevel.WYVERN;
        TechLevel dims = tier == TechLevel.DRACONIUM ? TechLevel.WYVERN : tier;
        return new ModuleHostImpl(tier, ModuleCfg.toolWidth(dims), ModuleCfg.toolHeight(dims),
                toolTypeId().getPath(), ModuleCfg.removeInvalidModules);
    }

    @Override
    public ModularOPStorage createOPStorage(ItemStack stack, ModuleHostImpl host) {
        TechLevel tier = host.getHostTechLevel();
        TechLevel dims = tier == TechLevel.DRACONIUM ? TechLevel.WYVERN : tier;
        return new ModularOPStorage(host, EquipCfg.getBaseToolEnergy(dims), EquipCfg.getBaseToolTransfer(dims));
    }

    @Override
    public com.brandon3055.brandonscore.capability.MultiCapabilityProvider initCapabilities(
            ItemStack stack, @Nullable CompoundTag nbt) {
        return new ModularSupport.Provider(stack, toolTypeId().getPath(), false);
    }

    // ------------------------------------------------------------------ module effect bridges

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) return base;
        double moduleDamage = ModularSupport.moduleDamage(stack);
        if (moduleDamage <= 0) return base;
        Multimap<Attribute, AttributeModifier> merged = HashMultimap.create(base);
        merged.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(MODULE_DAMAGE_UUID,
                "smithery_de_module_damage", moduleDamage, AttributeModifier.Operation.ADDITION));
        return merged;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float speed = super.getDestroySpeed(stack, state);
        if (speed > 1.0f) {
            double bonus = ModularSupport.moduleSpeed(stack);
            if (bonus > 0) speed *= (float) (1.0 + bonus);
        }
        return speed;
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        int remaining = ModularSupport.absorbDamageWithEnergy(stack, amount);
        return super.damageItem(stack, remaining, entity, onBroken);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        boolean result = super.mineBlock(stack, level, state, pos, owner);
        if (level.isClientSide() || AOE_BREAKING.get() || !(owner instanceof ServerPlayer player)
                || player.isShiftKeyDown()) {
            return result;
        }
        int radius = ModularSupport.moduleAoe(stack);
        if (radius <= 0 || !isCorrectToolForDrops(stack, state)) return result;

        AOE_BREAKING.set(true);
        try {
            for (BlockPos target : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius),
                                                          pos.offset(radius, radius, radius))) {
                if (target.equals(pos)) continue;
                BlockState targetState = level.getBlockState(target);
                if (targetState.isAir()) continue;
                if (targetState.getDestroySpeed(level, target) < 0) continue; // unbreakable
                if (!isCorrectToolForDrops(stack, targetState)) continue;
                if (stack.getDamageValue() >= stack.getMaxDamage() - 1) break; // don't shatter
                player.gameMode.destroyBlock(target.immutable());
            }
        } finally {
            AOE_BREAKING.set(false);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Guarded override: DE's equipment handlers call this on every {@code IModularItem}
     * they see and the interface default {@code orElseThrow}s on the module-host capability —
     * which is legitimately absent on non-draconic-tier compositions. No host, no tick.
     */
    @Override
    public void handleTick(ItemStack stack, LivingEntity entity, @Nullable EquipmentSlot slot, boolean equipped) {
        if (!stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent()) return;
        IModularItem.super.handleTick(stack, entity, slot, equipped);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) return;
        if (!stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent()) return;
        boolean equipped = living.getMainHandItem() == stack || living.getOffhandItem() == stack;
        handleTick(stack, living, equipped ? EquipmentSlot.MAINHAND : null, equipped);
    }
}
