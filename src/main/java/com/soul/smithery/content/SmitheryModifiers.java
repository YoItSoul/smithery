package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

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

        // ---- Active: on BLOCK DROPS (after drops spawn), pull dropped items toward player ----
        // FIRES FROM BlockDropsEvent, NOT BlockBreakEvent. Drops don't exist yet at break-time;
        // they're spawned afterwards. Iterating the drops list NeoForge gives us guarantees
        // we hit every dropped item the block produced.
        MAGNETIZED = id("magnetized");
        SmitheryAPI.registerModifier(Modifier.builder(MAGNETIZED)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onBlockDrops((effect, ctx) -> {
                    if (ctx.player() == null) return;
                    Vec3 playerPos = ctx.player().position();
                    for (ItemEntity drop : ctx.drops()) {
                        Vec3 toward = playerPos.subtract(drop.position()).normalize().scale(0.4);
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

        // ---- Active: multiply XP dropped by killed entities ----
        LUCKY_STRIKE = id("lucky_strike");
        SmitheryAPI.registerModifier(Modifier.builder(LUCKY_STRIKE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onKill((effect, ctx) -> {
                    float mult = effect.paramFloat("xp_multiplier", 1.25f);
                    ctx.setXp(Math.round(ctx.xp() * mult));
                })
                .build());

        // ---- Active: multiply XP dropped by broken blocks (ores in particular) ----
        // BlockDropsEvent exposes both the drops list and the XP amount the block will drop.
        // We don't gate on "is this an ore" — the multiplier just applies to whatever XP the
        // block drops naturally (ores being the main source). Stone breaks drop no XP, so
        // they're naturally unaffected.
        GILDED = id("gilded");
        SmitheryAPI.registerModifier(Modifier.builder(GILDED)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onBlockDrops((effect, ctx) -> {
                    float mult = effect.paramFloat("xp_multiplier", 1.25f);
                    ctx.setXp(Math.round(ctx.xp() * mult));
                })
                .build());

        // ---- Post-craft, applied at Anvil from a Nether Star: +6 damage ----
        NETHER_SHARPENED = id("nether_sharpened");
        SmitheryAPI.registerModifier(Modifier.builder(NETHER_SHARPENED)
                .category(Modifier.ModifierCategory.PASSIVE)
                .passive((effect, stats) -> stats.bonusAttackDamage += effect.paramFloat("damage", 6.0f))
                .build());

        // ---- Built-in anvil source mappings ----
        // Modders extend by calling ModifierSources.register(...) from their own init class.
        // Nether Star → NETHER_SHARPENED with +6 damage is the canonical single-shot example.
        // maxLevel of NETHER_SHARPENED is 1 (default) — one Nether Star per tool, no stacking.
        ModifierSources.register(Items.NETHER_STAR,
                ModifierEffect.of(NETHER_SHARPENED, Map.of("damage", 6.0f)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryModifiers() {}
}
