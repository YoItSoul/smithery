package com.soul.smithery.entity;

import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolStats;
import com.soul.smithery.registry.SmitheryDataComponents;
import com.soul.smithery.registry.SmitheryEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Thrown shuriken projectile. Renders as its item stack (thrown-item renderer), deals the
 * shuriken composition's attack damage on entity hit, and sticks into the ground as a
 * recoverable item entity on block hit — thrown weapons that vanish feel terrible.
 */
public class SmitheryShuriken extends ThrowableItemProjectile {

    public SmitheryShuriken(EntityType<? extends SmitheryShuriken> type, Level level) {
        super(type, level);
    }

    public SmitheryShuriken(Level level, LivingEntity shooter, ItemStack stack) {
        super(SmitheryEntityTypes.SHURIKEN.get(), shooter, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return com.soul.smithery.registry.SmitheryItems.SHURIKEN.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) return;
        float damage = 2.0f;
        ToolComposition comp = getItem().get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp != null && comp.isValid()) {
            damage = Math.max(1.0f, ToolStats.compute(comp).attackDamage);
        }
        result.getEntity().hurt(damageSources().thrown(this, getOwner()), damage);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide()) return;
        ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
        drop.setPickUpDelay(10);
        level().addFreshEntity(drop);
        discard();
    }
}
