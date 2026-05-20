package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Built-in modifiers. Behavior callbacks read params from the ModifierEffect attached at
 * craft time (or post-craft via the Anvil); the same modifier ID can therefore be used
 * across materials with different parameter values.
 *
 * Active modifier callbacks (onAttack, onBreak) are invoked by the global event router
 * once tools and their modifier slots are wired up in Phase 6.
 */
public final class SmitheryModifiers {
    public static Identifier SHARP;
    public static Identifier MAGNETIZED;
    public static Identifier VERDANT;
    public static Identifier CORROSIVE;
    public static Identifier LUCKY_STRIKE;
    public static Identifier GILDED;
    public static Identifier NETHER_SHARPENED;

    public static void register() {
        // ---- Passive: +N attack damage ----
        SHARP = id("sharp");
        SmitheryAPI.registerModifier(Modifier.builder(SHARP)
                .category(Modifier.ModifierCategory.PASSIVE)
                .passive((effect, stats) -> stats.bonusAttackDamage += effect.paramFloat("damage", 2.0f))
                .build());

        // ---- Active: on block break, pull dropped items within radius toward player ----
        MAGNETIZED = id("magnetized");
        SmitheryAPI.registerModifier(Modifier.builder(MAGNETIZED)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onBlockBreak((effect, ctx) -> {
                    float radius = effect.paramFloat("radius", 5.0f);
                    if (ctx.level().isClientSide() || ctx.player() == null) return;
                    AABB box = new AABB(ctx.pos()).inflate(radius);
                    for (ItemEntity drop : ctx.level().getEntitiesOfClass(ItemEntity.class, box)) {
                        Vec3 toward = ctx.player().position().subtract(drop.position()).normalize().scale(0.4);
                        drop.setDeltaMovement(toward);
                        drop.setPickUpDelay(0);
                    }
                })
                .build());

        // ---- Active: chance to apply Poison on hit ----
        VERDANT = id("verdant");
        SmitheryAPI.registerModifier(Modifier.builder(VERDANT)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onAttackEntity((effect, ctx) -> {
                    float chance = effect.paramFloat("chance", 0.15f);
                    int duration = effect.paramInt("duration_ticks", 60);
                    int amp = effect.paramInt("amplifier", 0);
                    if (!(ctx.target() instanceof LivingEntity target)) return;
                    if (target.level().getRandom().nextFloat() < chance) {
                        target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, amp));
                    }
                })
                .build());

        // ---- Passive + Active: −armor on hit (small attack speed penalty as the cost) ----
        // The −armor side is applied in active onAttack; the speed cost (if any) is passive.
        CORROSIVE = id("corrosive");
        SmitheryAPI.registerModifier(Modifier.builder(CORROSIVE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onAttackEntity((effect, ctx) -> {
                    float chance = effect.paramFloat("chance", 0.25f);
                    int duration = effect.paramInt("duration_ticks", 100);
                    int amp = effect.paramInt("amplifier", 1);
                    if (!(ctx.target() instanceof LivingEntity target)) return;
                    if (target.level().getRandom().nextFloat() < chance) {
                        // Weakness lowers attack output; closest vanilla analog to "−armor"
                        // without writing a custom attribute modifier yet.
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amp));
                    }
                })
                .build());

        // ---- Passive: +XP from kills (sword orientation) ----
        LUCKY_STRIKE = id("lucky_strike");
        SmitheryAPI.registerModifier(Modifier.builder(LUCKY_STRIKE)
                .category(Modifier.ModifierCategory.PASSIVE)
                // Multiplier is consumed by the kill-XP hook in Phase 6.
                .build());

        // ---- Passive: +XP from ore mining (pick orientation) ----
        GILDED = id("gilded");
        SmitheryAPI.registerModifier(Modifier.builder(GILDED)
                .category(Modifier.ModifierCategory.PASSIVE)
                .build());

        // ---- Post-craft, applied at Anvil from a Nether Star: +6 damage ----
        NETHER_SHARPENED = id("nether_sharpened");
        SmitheryAPI.registerModifier(Modifier.builder(NETHER_SHARPENED)
                .category(Modifier.ModifierCategory.PASSIVE)
                .passive((effect, stats) -> stats.bonusAttackDamage += effect.paramFloat("damage", 6.0f))
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryModifiers() {}
}
