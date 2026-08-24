package com.soul.smithery.compat.draconic;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.modules.lib.ModularOPStorage;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.init.ModuleCfg;
import com.brandon3055.draconicevolution.items.equipment.IModularArmor;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * DE-aware Smithery armor. Same integration contract as {@link DraconicSmitheryToolItem}:
 * draconic-tier compositions expose DE's {@code ModuleHost} (chest pieces use DE's chestpiece
 * grid dimensions, other slots the tool dimensions) and use module energy to absorb durability
 * damage before Smithery's own never-shatter clamp applies.
 *
 * <p>Deliberately thin: DE's own handlers already drive module ticking, jump boost and
 * movement speed through any item implementing its interfaces, so bridging those here
 * applied every effect twice.
 *
 * <p><b>Known trade-off, chest slot.</b> DE's {@code IModularArmor.getArmor} short-circuits on
 * {@code getItemBySlot(CHEST).getItem() instanceof IModularArmor} <em>without</em> testing for the
 * module-host capability, and returns immediately. So while any Smithery chestplate is worn — even
 * a plain iron one with no host — DE's equipment-manager fallback is never reached, and a DE
 * Modular Chestpiece sitting in a Curios slot silently loses shield, undying, flight and hill-step.
 * There is no fix on this side: that same {@code instanceof} short-circuit is exactly what lets DE
 * find a draconic-tier Smithery chestplate, so narrowing it would break the integration it enables.
 * Resolving it properly needs a mixin on {@code getArmor} to fall through when the chest stack has
 * no {@code MODULE_HOST}.
 */
public class DraconicSmitheryArmorItem extends SmitheryArmorItem implements IModularArmor {

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
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        int remaining = ModularSupport.absorbDamageWithEnergy(stack, amount);
        return super.damageItem(stack, remaining, entity, onBroken);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Guarded override: BrandonsCore queries every equipped {@code ElytraEnabledItem} — from
     * {@code LivingEntity#updateFallFlying}, {@code Player#tryToStartFallFlying} and DE's elytra
     * render layer, so once per frame while worn — and the interface default {@code orElseThrow}s
     * on the module-host capability, which is legitimately absent on non-draconic-tier
     * compositions. No host means no FLIGHT module, so no elytra.
     */
    @Override
    public boolean canElytraFlyBC(ItemStack stack, LivingEntity entity) {
        if (!stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent()) return false;
        return IModularArmor.super.canElytraFlyBC(stack, entity);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Guarded for the same reason as {@link #canElytraFlyBC}: BrandonsCore's fall-flying hook
     * reaches this for equipment-slot items without re-checking. False leaves the tick to vanilla.
     */
    @Override
    public boolean elytraFlightTickBC(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!stack.getCapability(DECapabilities.MODULE_HOST_CAPABILITY).isPresent()) return false;
        return IModularArmor.super.elytraFlightTickBC(stack, entity, flightTicks);
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

}
