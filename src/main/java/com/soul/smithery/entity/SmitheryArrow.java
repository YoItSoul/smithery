package com.soul.smithery.entity;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolStats;
import com.soul.smithery.registry.SmitheryDataComponents;
import com.soul.smithery.registry.SmitheryEntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Smithery arrow projectile. Extends vanilla {@link Arrow} so all stock behaviour
 * (flight physics, pickup, block sticking, tipped-arrow effects) still works; the only
 * additional behaviour is on-hit modifier dispatch. Composition lives on the arrow's
 * pickup ItemStack and is read directly from the TOOL_COMPOSITION component.
 */
public class SmitheryArrow extends Arrow {

    /**
     * Vanilla-style entity-type constructor used by the entity-type factory and for
     * load-from-disk reconstitution.
     */
    public SmitheryArrow(EntityType<? extends SmitheryArrow> type, Level level) {
        super(type, level);
    }

    /**
     * Spawn-from-bow constructor. Manually replays the parts of {@code AbstractArrow}'s
     * full constructor needed to bind this registered EntityType (vanilla's
     * {@code Arrow(Level, LivingEntity, ItemStack, ItemStack)} constructor hard-codes
     * {@code EntityType.ARROW}).
     */
    public SmitheryArrow(Level level, LivingEntity owner, ItemStack pickupItemStack,
                         @Nullable ItemStack firedFromWeapon) {
        super(SmitheryEntityTypes.ARROW.get(), level);
        this.setPickupItemStack(pickupItemStack.copy());
        this.applyComponentsFromItemStack(pickupItemStack);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1F, owner.getZ());
        this.setOwner(owner);
    }

    /**
     * Dispenser-spawn factory. Mirrors vanilla's {@code ArrowItem#asProjectile} flow
     * (registered EntityType, pickup-stack-driven components, pickup allowed) and lives
     * here because {@code setPickupItemStack} is protected on {@code AbstractArrow}.
     */
    public static SmitheryArrow spawnFromDispenser(Level level, double x, double y, double z,
                                                   ItemStack itemStack) {
        SmitheryArrow arrow = new SmitheryArrow(SmitheryEntityTypes.ARROW.get(), level);
        arrow.setPickupItemStack(itemStack.copyWithCount(1));
        arrow.applyComponentsFromItemStack(itemStack);
        arrow.setPos(x, y, z);
        arrow.pickup = Pickup.ALLOWED;
        return arrow;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);

        if (this.level().isClientSide()) return;
        if (!(hitResult.getEntity() instanceof LivingEntity target)) return;

        ItemStack pickup = this.getPickupItemStackOrigin();
        ToolComposition comp = pickup.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null || !comp.isValid()) return;

        Entity ownerEntity = this.getOwner();
        LivingEntity attacker = ownerEntity instanceof LivingEntity le ? le : target;

        java.util.List<ModifierEffect> applied = pickup.getOrDefault(
                SmitheryDataComponents.APPLIED_MODIFIERS.get(), java.util.List.of());
        ToolStats stats = ToolStats.compute(comp, applied);
        if (stats.activeEffects.isEmpty()) return;

        Modifier.AttackContext ctx = new Modifier.AttackContext(pickup, attacker, target, 0f);
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            Modifier mod = SmitheryAPI.MODIFIERS.get(r.effect().modifierId());
            if (mod == null || mod.onAttack() == null) continue;
            try {
                mod.onAttack().onAttack(r.effect(), ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Arrow modifier {} onAttack failed: {}",
                        r.effect().modifierId(), t.toString());
            }
        }
    }
}
