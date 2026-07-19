package com.soul.smithery.compat.draconic;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.modules.lib.ModularOPStorage;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.init.ModuleCfg;
import com.brandon3055.draconicevolution.items.equipment.IModularArmor;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * DE-aware Smithery armor. Same integration contract as {@link DraconicSmitheryToolItem}:
 * draconic-tier compositions expose DE's {@code ModuleHost} (chest pieces use DE's chestpiece
 * grid dimensions, other slots the tool dimensions), tick their modules while worn, gain a
 * movement-speed bridge for SPEED modules, and use module energy to absorb durability damage
 * before Smithery's own never-shatter clamp applies.
 */
public class DraconicSmitheryArmorItem extends SmitheryArmorItem implements IModularArmor {

    /** Distinct per-slot UUIDs so speed bonuses from multiple worn pieces stack. */
    private static final UUID[] MODULE_SPEED_UUIDS = {
            UUID.fromString("b1d3f7c2-8a4e-4f6b-9c2d-0e5a7b391418"), // FEET
            UUID.fromString("c2e4a8d3-9b5f-4a7c-8d3e-1f6b8c4a2529"), // LEGS
            UUID.fromString("d3f5b9e4-ac6a-4b8d-9e4f-2a7c9d5b363a"), // CHEST
            UUID.fromString("e4a6caf5-bd7b-4c9e-8f5a-3b8dae6c474b"), // HEAD
    };

    public DraconicSmitheryArmorItem(Type type, Properties properties, ResourceLocation toolTypeId) {
        super(type, properties, toolTypeId);
    }

    // ------------------------------------------------------------------ IModularItem contract

    @Override
    public TechLevel getTechLevel() {
        return TechLevel.DRACONIC;
    }

    private boolean chestGrid() {
        return getType() == Type.CHESTPLATE;
    }

    /**
     * Host categories for the chestplate. DE 1.20 collapsed all armor moduling into its single
     * Modular Chestpiece and balanced every armor module (shield, jump, flight, undying, …)
     * around ONE hosting piece — so Smithery mirrors that exactly: only chestplates host
     * modules ({@link #initCapabilities} returns no provider for other slots), and they
     * declare the same CHESTPIECE category DE's armor modules require (ARMOR/ARMOR_CHEST ride
     * along for addon modules).
     */
    private com.brandon3055.draconicevolution.api.modules.ModuleCategory[] hostCategories() {
        return new com.brandon3055.draconicevolution.api.modules.ModuleCategory[] {
                com.brandon3055.draconicevolution.api.modules.ModuleCategory.CHESTPIECE,
                com.brandon3055.draconicevolution.api.modules.ModuleCategory.ARMOR,
                com.brandon3055.draconicevolution.api.modules.ModuleCategory.ARMOR_CHEST,
        };
    }

    @Override
    public ModuleHostImpl createHost(ItemStack stack) {
        TechLevel tier = ModularSupport.tierOf(stack);
        if (tier == null) tier = TechLevel.WYVERN;
        TechLevel dims = tier == TechLevel.DRACONIUM ? TechLevel.WYVERN : tier;
        int w = chestGrid() ? ModuleCfg.chestpieceWidth(dims) : ModuleCfg.toolWidth(dims);
        int h = chestGrid() ? ModuleCfg.chestpieceHeight(dims) : ModuleCfg.toolHeight(dims);
        return new ModuleHostImpl(tier, w, h, toolTypeId().getPath(), ModuleCfg.removeInvalidModules,
                hostCategories());
    }

    @Override
    public ModularOPStorage createOPStorage(ItemStack stack, ModuleHostImpl host) {
        TechLevel tier = host.getHostTechLevel();
        TechLevel dims = tier == TechLevel.DRACONIUM ? TechLevel.WYVERN : tier;
        return new ModularOPStorage(host,
                EquipCfg.getBaseChestpieceEnergy(dims), EquipCfg.getBaseChestpieceTransfer(dims));
    }

    @Override
    public @Nullable com.brandon3055.brandonscore.capability.MultiCapabilityProvider initCapabilities(
            ItemStack stack, @Nullable CompoundTag nbt) {
        // DE balance: only the chestplate hosts modules (DE's single armor piece is its
        // chestpiece). Other slots expose no module capability at all.
        if (!chestGrid()) return null;
        return new ModularSupport.Provider(stack, toolTypeId().getPath(), true, hostCategories());
    }

    // ------------------------------------------------------------------ module effect bridges

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != getType().getSlot()) return base;
        double speed = ModularSupport.moduleSpeed(stack);
        if (speed <= 0) return base;
        Multimap<Attribute, AttributeModifier> merged = HashMultimap.create(base);
        merged.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                MODULE_SPEED_UUIDS[getType().getSlot().getIndex()],
                "smithery_de_module_speed", speed, AttributeModifier.Operation.MULTIPLY_TOTAL));
        return merged;
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        int remaining = ModularSupport.absorbDamageWithEnergy(stack, amount);
        return super.damageItem(stack, remaining, entity, onBroken);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Guarded override: DE's {@code ModularArmorEventHandler} ticks every equipped
     * {@code IModularItem} and the interface default {@code orElseThrow}s on the module-host
     * capability — absent on non-draconic-tier compositions (crashed on draconium boots).
     */
    @Override
    public void handleTick(ItemStack stack, LivingEntity entity, @Nullable EquipmentSlot slot, boolean equipped) {
        if (!stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent()) return;
        IModularArmor.super.handleTick(stack, entity, slot, equipped);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) return;
        if (!stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent()) return;
        EquipmentSlot armorSlot = getType().getSlot();
        boolean worn = living.getItemBySlot(armorSlot) == stack;
        handleTick(stack, living, worn ? armorSlot : null, worn);
    }
}
