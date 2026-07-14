package com.soul.smithery.entity;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolStats;
import com.soul.smithery.registry.SmitheryEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/**
 * Smithery arrow projectile. Extends vanilla {@link Arrow} so all stock behaviour
 * (flight physics, pickup, block sticking, tipped-arrow effects) still works; the
 * additional behaviour is composition-driven base damage and on-hit modifier dispatch.
 * The composition lives on the arrow's pickup ItemStack, carried on this entity and
 * persisted with it.
 */
public class SmitheryArrow extends Arrow {

    /** NBT key this entity persists its composed pickup stack under. */
    private static final String KEY_PICKUP_ITEM = Smithery.MODID + ":pickup_item";

    /**
     * Stack NBT key carrying the launching weapon's damage scalar, stamped onto charged
     * projectiles by {@link com.soul.smithery.item.tool.SmitheryCrossbowItem} (1.20.1's
     * crossbow pipeline offers no shoot-time hook). Read and stripped on construction so
     * recovered arrows stack cleanly with fresh ones.
     */
    public static final String KEY_WEAPON_SCALAR = Smithery.MODID + ":weapon_scalar";

    private ItemStack pickupStack = ItemStack.EMPTY;

    /**
     * Vanilla-style entity-type constructor used by the entity-type factory and for
     * load-from-disk reconstitution.
     */
    public SmitheryArrow(EntityType<? extends SmitheryArrow> type, Level level) {
        super(type, level);
    }

    /**
     * Spawn-from-weapon constructor. Manually replays the parts of {@code AbstractArrow}'s
     * full constructor needed to bind this registered EntityType (vanilla's
     * {@code Arrow(Level, LivingEntity)} constructor hard-codes {@code EntityType.ARROW}).
     */
    public SmitheryArrow(Level level, LivingEntity owner, ItemStack pickupItemStack) {
        super(SmitheryEntityTypes.ARROW.get(), level);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        this.setOwner(owner);
        if (owner instanceof Player) {
            this.pickup = Pickup.ALLOWED;
        }
        setComposedPickupStack(pickupItemStack.copyWithCount(1));
    }

    /**
     * Binds the composed pickup stack, consuming any weapon-scalar stamp and deriving the
     * arrow's base damage from its composition.
     */
    private void setComposedPickupStack(ItemStack stack) {
        float weaponScalar = 1.0f;
        var tag = stack.getTag();
        if (tag != null && tag.contains(KEY_WEAPON_SCALAR)) {
            weaponScalar = tag.getFloat(KEY_WEAPON_SCALAR);
            tag.remove(KEY_WEAPON_SCALAR);
        }
        this.pickupStack = stack;

        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null || !comp.isValid()) return;
        ToolStats stats = ToolStats.compute(comp);
        double damage = stats.attackDamage * weaponScalar;
        if (damage > 0.0) {
            this.setBaseDamage(damage);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Recovered smithery arrows return the composed stack, not a vanilla arrow.
     */
    @Override
    public ItemStack getPickupItem() {
        return pickupStack.isEmpty() ? super.getPickupItem() : pickupStack.copy();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!pickupStack.isEmpty()) {
            tag.put(KEY_PICKUP_ITEM, pickupStack.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(KEY_PICKUP_ITEM)) {
            this.pickupStack = ItemStack.of(tag.getCompound(KEY_PICKUP_ITEM));
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);

        if (this.level().isClientSide()) return;
        if (!(hitResult.getEntity() instanceof LivingEntity target)) return;

        ToolComposition comp = SmitheryToolData.getComposition(pickupStack);
        if (comp == null || !comp.isValid()) return;

        Entity ownerEntity = this.getOwner();
        LivingEntity attacker = ownerEntity instanceof LivingEntity le ? le : target;

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(pickupStack);
        ToolStats stats = ToolStats.compute(comp, applied);
        if (stats.activeEffects.isEmpty()) return;

        Modifier.AttackContext ctx = new Modifier.AttackContext(pickupStack, attacker, target, 0f);
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
