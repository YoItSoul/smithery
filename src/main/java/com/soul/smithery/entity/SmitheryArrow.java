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
 * Smithery arrow projectile. Extends vanilla {@link Arrow} so all stock vanilla behaviour
 * (flight physics, pickup, in-block sticking, potion-tipped effects) still works. The only
 * smithery-specific behaviour is on-hit modifier dispatch: when the arrow hits a living entity,
 * iterate the active modifiers declared by the arrow's {@link ToolComposition} (the arrow's
 * arrow_head material's at-craft modifiers, plus any post-craft modifiers applied via anvil)
 * and fire each modifier's {@code onAttackEntity} hook with the hit entity.
 *
 * <p>Composition lives on the arrow's pickup ItemStack (the source from which the arrow was
 * fired) — we read TOOL_COMPOSITION directly from there. No extra synced field is needed; the
 * pickup stack already round-trips through vanilla's arrow data sync.
 *
 * <h3>Custom entity-type init quirk</h3>
 * Vanilla {@link Arrow}'s {@code (Level, LivingEntity, ItemStack, ItemStack)} constructor
 * hard-codes {@code EntityType.ARROW} when delegating to {@code AbstractArrow}. To bind our
 * own registered {@code EntityType} we go through {@code Arrow}'s minimal
 * {@code (type, Level)} constructor instead and replay the pickup-stack / owner / position
 * init manually. {@code firedFromWeapon} (private to {@code AbstractArrow}, no setter) is
 * intentionally not propagated — it only feeds vanilla piercing/knockback enchantments,
 * which smithery doesn't expose anyway.
 */
public class SmitheryArrow extends Arrow {

    public SmitheryArrow(EntityType<? extends SmitheryArrow> type, Level level) {
        super(type, level);
    }

    public SmitheryArrow(Level level, LivingEntity owner, ItemStack pickupItemStack,
                         @Nullable ItemStack firedFromWeapon) {
        super(SmitheryEntityTypes.ARROW.get(), level);
        // Replay AbstractArrow's full constructor manually since we couldn't pass our
        // EntityType through vanilla Arrow's hard-coded super call. setPickupItemStack
        // is Arrow-overridden to call updateColor afterward, so potion-tipped tint still
        // updates correctly.
        this.setPickupItemStack(pickupItemStack.copy());
        this.applyComponentsFromItemStack(pickupItemStack);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1F, owner.getZ());
        this.setOwner(owner);
        // NOTE: firedFromWeapon intentionally dropped — see class javadoc.
    }

    /**
     * Dispenser-spawn factory. Mirrors {@link net.minecraft.world.item.ArrowItem#asProjectile}
     * for the smithery arrow: registered EntityType, pickup-stack-driven components, allowed
     * pickup. Lives here (not in SmitheryArrowItem) because {@code setPickupItemStack} is
     * {@code protected} on {@code AbstractArrow} and only reachable from a subclass.
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
        // Let vanilla do all the damage / pierce / knockback / potion bookkeeping first.
        super.onHitEntity(hitResult);

        if (this.level().isClientSide()) return;
        if (!(hitResult.getEntity() instanceof LivingEntity target)) return;

        ItemStack pickup = this.getPickupItemStackOrigin();
        ToolComposition comp = pickup.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null || !comp.isValid()) return;

        // The shooter (owner) is the "attacker" for modifier purposes. Anonymous arrows
        // (no owner — dispenser-fired, etc.) get a synthetic context with the target as
        // both fields; modifiers that genuinely need an attacker should null-check.
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
