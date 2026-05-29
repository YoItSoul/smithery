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
 * Built-in modifier registrations plus their canonical anvil source mappings.
 *
 * <p>Behaviour callbacks read parameters from the {@link ModifierEffect} attached at craft time
 * (or post-craft via the anvil), so the same modifier id can be reused across materials with
 * different parameter values. Active callbacks (onAttack, onBreak, …) are dispatched by
 * {@link com.soul.smithery.event.ToolModifierEventRouter}.
 */
public final class SmitheryModifiers {
    private static final Map<UUID, Long> LAST_LUNGE_TICK = new WeakHashMap<>();
    private static final long LUNGE_COOLDOWN_TICKS = 10L;

    /** Identifier of the SHARP passive damage-bonus modifier. */
    public static Identifier SHARP;
    /** Identifier of the MAGNETIZED drop-pulling modifier. */
    public static Identifier MAGNETIZED;
    /** Identifier of the VERDANT chance-to-poison modifier. */
    public static Identifier VERDANT;
    /** Identifier of the CORROSIVE chance-to-weaken modifier. */
    public static Identifier CORROSIVE;
    /** Identifier of the LUCKY_STRIKE kill-XP-multiplier modifier. */
    public static Identifier LUCKY_STRIKE;
    /** Identifier of the GILDED block-XP-multiplier modifier. */
    public static Identifier GILDED;
    /** Identifier of the NETHER_SHARPENED anvil-applied damage-bonus modifier. */
    public static Identifier NETHER_SHARPENED;
    /** Identifier of the LUNGE spear-exclusive forward-impulse modifier. */
    public static Identifier LUNGE;

    /**
     * Registers every built-in modifier and its anvil source mapping.
     *
     * <p>Must run after {@link SmitheryModifierActions#register()} so action types referenced
     * by JSON modifiers can resolve, and after tool/material registration so any material
     * grants referencing these modifier ids can find them.
     */
    public static void register() {
        // smithery:sharp is JSON-defined (data/smithery/smithery/modifier/sharp.json) as a
        // multi-level upgrade modifier fed by nether quartz, mirroring the haste/lapis_blessing
        // pattern. The identifier is kept here so synergies and material grants can reference it
        // by name before the JSON modifier reload runs.
        SHARP = id("sharp");

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

        CORROSIVE = id("corrosive");
        SmitheryAPI.registerModifier(Modifier.builder(CORROSIVE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onAttackEntity((effect, ctx) -> {
                    float chance = effect.paramFloat("chance", 0.25f);
                    int duration = effect.paramInt("duration_ticks", 100);
                    int amp = effect.paramInt("amplifier", 1);
                    if (!(ctx.target() instanceof LivingEntity target)) return;
                    if (target.level().getRandom().nextFloat() < chance) {
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amp));
                    }
                })
                .build());

        LUCKY_STRIKE = id("lucky_strike");
        SmitheryAPI.registerModifier(Modifier.builder(LUCKY_STRIKE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onKill((effect, ctx) -> {
                    float mult = effect.paramFloat("xp_multiplier", 1.25f);
                    ctx.setXp(Math.round(ctx.xp() * mult));
                })
                .build());

        GILDED = id("gilded");
        SmitheryAPI.registerModifier(Modifier.builder(GILDED)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onBlockDrops((effect, ctx) -> {
                    float mult = effect.paramFloat("xp_multiplier", 1.25f);
                    ctx.setXp(Math.round(ctx.xp() * mult));
                })
                .build());

        NETHER_SHARPENED = id("nether_sharpened");
        SmitheryAPI.registerModifier(Modifier.builder(NETHER_SHARPENED)
                .category(Modifier.ModifierCategory.PASSIVE)
                .passive((effect, stats) -> stats.bonusAttackDamage += effect.paramFloat("damage", 6.0f))
                .build());

        LUNGE = id("lunge");
        SmitheryAPI.registerModifier(Modifier.builder(LUNGE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .maxLevel(3)
                .onAttackEntity((effect, ctx) -> {
                    if (!(ctx.tool().getItem() instanceof SmitheryToolItem toolItem)) return;
                    if (!"spear".equals(toolItem.toolTypeId().getPath())) return;
                    if (!(ctx.attacker() instanceof Player player)) return;
                    if (player.level().isClientSide()) return;
                    if (player.isPassenger()) return;
                    if (player.isFallFlying()) return;
                    if (player.isInWater()) return;
                    boolean creative = player.getAbilities().instabuild;
                    if (!creative && player.getFoodData().getFoodLevel() < 7) return;

                    long now = player.level().getGameTime();
                    Long last = LAST_LUNGE_TICK.get(player.getUUID());
                    if (last != null && now - last < LUNGE_COOLDOWN_TICKS) return;
                    LAST_LUNGE_TICK.put(player.getUUID(), now);

                    int level = Math.max(1, effect.paramInt("level", 1));
                    float impulse = 0.458f * level;
                    float exhaustion = 4.0f * level;

                    Vec3 look = player.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0.0, look.z);
                    double len = flat.length();
                    if (len > 1.0e-4) {
                        Vec3 push = flat.scale(impulse / len);
                        player.push(push.x, 0.0, push.z);
                        player.hurtMarked = true;
                    }

                    if (!creative) player.causeFoodExhaustion(exhaustion);

                    ctx.tool().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

                    Holder<SoundEvent> sound = switch (player.getRandom().nextInt(3)) {
                        case 0  -> SoundEvents.LUNGE_1;
                        case 1  -> SoundEvents.LUNGE_2;
                        default -> SoundEvents.LUNGE_3;
                    };
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            sound.value(), player.getSoundSource(), 1.0f, 1.0f);
                })
                .build());

        ModifierSources.register(Items.NETHER_STAR,
                ModifierEffect.of(NETHER_SHARPENED, Map.of("damage", 6.0f)));
        ModifierSources.register(Items.PHANTOM_MEMBRANE,
                ModifierEffect.of(LUNGE, Map.of("level", 1)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryModifiers() {}
}
