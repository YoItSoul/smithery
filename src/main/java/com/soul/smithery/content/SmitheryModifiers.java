package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Built-in modifiers. Behavior callbacks read params from the ModifierEffect attached at
 * craft time (or post-craft via the Anvil); the same modifier ID can therefore be used
 * across materials with different parameter values.
 *
 * Active modifier callbacks (onAttack, onBreak) are invoked by the global event router
 * once tools and their modifier slots are wired up in Phase 6.
 */
public final class SmitheryModifiers {
    /**
     * Per-player cooldown for once-per-attack modifiers (currently just LUNGE). Server-side only —
     * event handlers run on the server thread so no synchronization is needed. Keyed by player UUID
     * with a game-time value; entries are looked up and rewritten each tick on use, so it self-cleans
     * over time via WeakHashMap when player references are released on disconnect.
     */
    private static final Map<UUID, Long> LAST_LUNGE_TICK = new WeakHashMap<>();
    /** Min game-ticks between LUNGE firings on the same player. KineticWeapon's
     *  HIT_FEEDBACK_TICKS = 10 covers the entire multi-pierce burst in a single tick;
     *  10 ticks (0.5s) is a safe cooldown that also rate-limits LMB spam. */
    private static final long LUNGE_COOLDOWN_TICKS = 10L;

    public static Identifier SHARP;
    public static Identifier MAGNETIZED;
    public static Identifier VERDANT;
    public static Identifier CORROSIVE;
    public static Identifier LUCKY_STRIKE;
    public static Identifier GILDED;
    public static Identifier NETHER_SHARPENED;
    public static Identifier LUNGE;

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

        // ---- Active: spear-exclusive Lunge. Replicates vanilla minecraft:lunge enchantment ----
        // Vanilla Lunge fires on post_piercing_attack and applies: +1 item damage, forward impulse
        // (0.458 × level along the player's look direction, xz-plane only), exhaustion (4.0 × level),
        // and a random LUNGE_{1..3} sound. Gates: not in vehicle, not fall-flying, not in water,
        // and either creative OR food ≥ 7. We hook onAttackEntity (which fires once per LMB attack
        // via AttackEntityEvent — same call-once semantics as vanilla's post_piercing_attack hook
        // at the end of Player.attack()) and replay the same effects in Java.
        //
        // Tool-type gate: only fires on smithery:spear tools. Vanilla restricts Lunge to the
        // #minecraft:spears tag; we check the held tool's ToolType id at runtime instead so the
        // modifier is harmless if anvil-applied to a non-spear by mistake.
        LUNGE = id("lunge");
        SmitheryAPI.registerModifier(Modifier.builder(LUNGE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .maxLevel(3)
                .onAttackEntity((effect, ctx) -> {
                    if (!(ctx.tool().getItem() instanceof SmitheryToolItem toolItem)) return;
                    if (!"spear".equals(toolItem.toolTypeId().getPath())) return;
                    if (!(ctx.attacker() instanceof Player player)) return;
                    if (player.level().isClientSide()) return;
                    // Vanilla gates: not in a vehicle, not fall-flying, not in water,
                    // and (creative OR food ≥ 7).
                    if (player.isPassenger()) return;
                    if (player.isFallFlying()) return;
                    if (player.isInWater()) return;
                    boolean creative = player.getAbilities().instabuild;
                    if (!creative && player.getFoodData().getFoodLevel() < 7) return;

                    // Once-per-attack gate. Both LMB melee (one Item.hurtEnemy call) and the
                    // spear's charged stab (one Item.hurtEnemy call per pierced entity) route
                    // here; without this gate, a multi-pierce stab would apply N impulses
                    // and N×exhaustion. KineticWeapon completes the whole pierce burst in a
                    // single tick, so the cooldown only has to swallow same-tick repeats.
                    long now = player.level().getGameTime();
                    Long last = LAST_LUNGE_TICK.get(player.getUUID());
                    if (last != null && now - last < LUNGE_COOLDOWN_TICKS) return;
                    LAST_LUNGE_TICK.put(player.getUUID(), now);

                    int level = Math.max(1, effect.paramInt("level", 1));
                    float impulse = 0.458f * level;
                    float exhaustion = 4.0f * level;

                    // Forward impulse in player look direction, xz only (matches coordinate_scale [1,0,1]).
                    Vec3 look = player.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0.0, look.z);
                    double len = flat.length();
                    if (len > 1.0e-4) {
                        Vec3 push = flat.scale(impulse / len);
                        player.push(push.x, 0.0, push.z);
                        player.hurtMarked = true;       // resync velocity to the client
                    }

                    if (!creative) player.causeFoodExhaustion(exhaustion);

                    // Damage the spear (1 per use), matching the vanilla change_item_damage effect.
                    ctx.tool().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

                    // Random lunge sound, matched to vanilla's [LUNGE_1, LUNGE_2, LUNGE_3] pool.
                    Holder<SoundEvent> sound = switch (player.getRandom().nextInt(3)) {
                        case 0  -> SoundEvents.LUNGE_1;
                        case 1  -> SoundEvents.LUNGE_2;
                        default -> SoundEvents.LUNGE_3;
                    };
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            sound.value(), player.getSoundSource(), 1.0f, 1.0f);
                })
                .build());

        // ---- Built-in anvil source mappings ----
        // Modders extend by calling ModifierSources.register(...) from their own init class.
        // Nether Star → NETHER_SHARPENED with +6 damage is the canonical single-shot example.
        // maxLevel of NETHER_SHARPENED is 1 (default) — one Nether Star per tool, no stacking.
        ModifierSources.register(Items.NETHER_STAR,
                ModifierEffect.of(NETHER_SHARPENED, Map.of("damage", 6.0f)));
        // Phantom Membrane → LUNGE. Thematic: phantoms dive-strike from above, mirroring the
        // spear lunge. Each membrane contributes one unit toward the next level (max 3 levels);
        // applied to non-spear tools the modifier silently no-ops (the runtime gate above).
        ModifierSources.register(Items.PHANTOM_MEMBRANE,
                ModifierEffect.of(LUNGE, Map.of("level", 1)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryModifiers() {}
}
