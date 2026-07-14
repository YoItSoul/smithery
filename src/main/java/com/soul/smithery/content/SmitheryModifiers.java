package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.modifier.ModifierSources;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import com.soul.smithery.item.tool.SmitheryToolData;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

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

    /** ResourceLocation of the SHARP passive damage-bonus modifier. */
    public static ResourceLocation SHARP;
    /** ResourceLocation of the MAGNETIZED drop-pulling modifier. */
    public static ResourceLocation MAGNETIZED;
    /** ResourceLocation of the VERDANT chance-to-poison modifier. */
    public static ResourceLocation VERDANT;
    /** ResourceLocation of the CORROSIVE chance-to-weaken modifier. */
    public static ResourceLocation CORROSIVE;
    /** ResourceLocation of the LUCKY_STRIKE kill-XP-multiplier modifier. */
    public static ResourceLocation LUCKY_STRIKE;
    /** ResourceLocation of the GILDED block-XP-multiplier modifier. */
    public static ResourceLocation GILDED;
    /** ResourceLocation of the NETHER_SHARPENED anvil-applied damage-bonus modifier. */
    public static ResourceLocation NETHER_SHARPENED;
    /** ResourceLocation of the LUNGE spear-exclusive forward-impulse modifier. */
    public static ResourceLocation LUNGE;
    /** ResourceLocation of the BOUNCY armor fall-damage-negation modifier. */
    public static ResourceLocation BOUNCY;
    /** ResourceLocation of the SPINY armor thorns-retaliation modifier. */
    public static ResourceLocation SPINY;
    /** ResourceLocation of the REINFORCED anvil-applied durability-multiplier modifier. */
    public static ResourceLocation REINFORCED;
    /** ResourceLocation of the SPEEDY anvil-applied movement-speed modifier. */
    public static ResourceLocation SPEEDY;
    /** ResourceLocation of the RESISTANT anvil-applied all-damage-reduction armor modifier. */
    public static ResourceLocation RESISTANT;
    /** ResourceLocation of the FIRE_RESISTANT typed armor damage-reduction modifier. */
    public static ResourceLocation FIRE_RESISTANT;
    /** ResourceLocation of the BLAST_RESISTANT typed armor damage-reduction modifier. */
    public static ResourceLocation BLAST_RESISTANT;
    /** ResourceLocation of the PROJECTILE_RESISTANT typed armor damage-reduction modifier. */
    public static ResourceLocation PROJECTILE_RESISTANT;
    /** ResourceLocation of the SOULBOUND keep-on-death modifier (handled by {@code SoulboundHandler}). */
    public static ResourceLocation SOULBOUND;
    /** ResourceLocation of the ECOLOGICAL slow self-repair armor modifier. */
    public static ResourceLocation ECOLOGICAL;
    /** ResourceLocation of the CONDUCTIVE lightning-damage-reduction armor modifier. */
    public static ResourceLocation CONDUCTIVE;
    /** ResourceLocation of the ALLURING XP-orb-pulling armor modifier. */
    public static ResourceLocation ALLURING;
    /** ResourceLocation of the STALWART knockback-resistance armor modifier. */
    public static ResourceLocation STALWART;
    /** ResourceLocation of the WARDED magic-damage-reduction armor modifier. */
    public static ResourceLocation WARDED;
    /** ResourceLocation of the AQUADYNAMIC swim-speed armor modifier. */
    public static ResourceLocation AQUADYNAMIC;
    /** ResourceLocation of the NIMBLE fall-distance-reduction armor modifier. */
    public static ResourceLocation NIMBLE;
    /** ResourceLocation of the STICKY attacker-slowing armor modifier. */
    public static ResourceLocation STICKY;
    /** ResourceLocation of the FIREWARD innate fire-damage-reduction trait (blaze armor). */
    public static ResourceLocation FIREWARD;
    /** ResourceLocation of the CRYSTALLINE innate all-damage-reduction trait (amethyst armor). */
    public static ResourceLocation CRYSTALLINE;
    /** ResourceLocation of the IMMOVABLE innate all-damage-reduction trait (bedrock armor). */
    public static ResourceLocation IMMOVABLE;
    /** ResourceLocation of the ENERGIZED innate movement-speed trait (redstone armor). */
    public static ResourceLocation ENERGIZED;
    /** ResourceLocation of the RESTORING XP-fed self-repair armor modifier (mending analog). */
    public static ResourceLocation RESTORING;
    /** ResourceLocation of the HIGH_STRIDE step-height armor modifier. */
    public static ResourceLocation HIGH_STRIDE;
    /** ResourceLocation of the AMPHIBIOUS oxygen-bonus armor modifier. */
    public static ResourceLocation AMPHIBIOUS;
    /** ResourceLocation of the POWERFUL attack-damage armor modifier (gauntlet trio). */
    public static ResourceLocation POWERFUL;
    /** ResourceLocation of the DEXTEROUS attack-speed armor modifier (gauntlet trio). */
    public static ResourceLocation DEXTEROUS;
    /** ResourceLocation of the TELEKINETIC reach armor modifier (gauntlet trio). */
    public static ResourceLocation TELEKINETIC;
    /** ResourceLocation of the MOMENTUM chained-mining haste trait. */
    public static ResourceLocation MOMENTUM;
    /** ResourceLocation of the JAGGED wear-scaled bonus-damage trait. */
    public static ResourceLocation JAGGED;
    /** ResourceLocation of the STONEBOUND wear-scaled mining-speed trait. */
    public static ResourceLocation STONEBOUND;
    /** ResourceLocation of the AUTOSMELT smelt-drops-on-break trait. */
    public static ResourceLocation AUTOSMELT;
    /** ResourceLocation of the FIERY ignite-on-hit modifier. */
    public static ResourceLocation FIERY;
    /** ResourceLocation of the EXCAVATING mining-hammer radius modifier (read by AoeMiningHandler). */
    public static ResourceLocation EXCAVATING;

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
                // Worn on armor: periodically draws loose drops toward the wearer. Every 5
                // ticks — item entities glide between pulls, so a per-tick pull only burns
                // server time without visible smoothness gain.
                .onArmorTick((effect, ctx) -> {
                    Player wearer = ctx.wearer();
                    if (wearer.tickCount % 5 != 0) return;
                    float radius = effect.paramFloat("radius", 5.0f);
                    AABB box = wearer.getBoundingBox().inflate(radius);
                    Vec3 playerPos = wearer.position();
                    for (ItemEntity drop : wearer.level().getEntitiesOfClass(ItemEntity.class, box)) {
                        if (drop.hasPickUpDelay()) continue;
                        Vec3 toward = playerPos.subtract(drop.position());
                        double dist = toward.length();
                        if (dist < 0.5 || dist > radius) continue;
                        drop.setDeltaMovement(toward.normalize().scale(0.35));
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
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .passive((effect, stats) -> stats.bonusAttackDamage += effect.paramFloat("damage", 6.0f))
                .build());

        LUNGE = id("lunge");
        SmitheryAPI.registerModifier(Modifier.builder(LUNGE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.TOOLS)
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

                    ctx.tool().hurtAndBreak(1, player,
                            p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));

                    // 1.20.1 has no dedicated lunge sounds; the riptide whooshes are the
                    // closest dash cue.
                    SoundEvent sound = switch (player.getRandom().nextInt(3)) {
                        case 0  -> SoundEvents.TRIDENT_RIPTIDE_1;
                        case 1  -> SoundEvents.TRIDENT_RIPTIDE_2;
                        default -> SoundEvents.TRIDENT_RIPTIDE_3;
                    };
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            sound, player.getSoundSource(), 1.0f, 1.0f);
                })
                .build());

        BOUNCY = id("bouncy");
        SmitheryAPI.registerModifier(Modifier.builder(BOUNCY)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onFall((effect, ctx) -> ctx.damageMultiplier().set(0f))
                .build());

        SPINY = id("spiny");
        SmitheryAPI.registerModifier(Modifier.builder(SPINY)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onDamaged((effect, ctx) -> {
                    float chance = effect.paramFloat("chance", 0.5f);
                    float damage = effect.paramFloat("damage", 2.0f);
                    if (!(ctx.source().getEntity() instanceof LivingEntity attacker)) return;
                    if (attacker == ctx.wearer()) return;
                    if (ctx.wearer().getRandom().nextFloat() >= chance) return;
                    attacker.hurt(ctx.wearer().damageSources().thorns(ctx.wearer()), damage);
                })
                .build());

        REINFORCED = id("reinforced");
        SmitheryAPI.registerModifier(Modifier.builder(REINFORCED)
                .category(Modifier.ModifierCategory.PASSIVE)
                .maxLevel(3)
                .levelCostScaling(2.0f)
                .passive((effect, stats) -> stats.durabilityMultiplier *=
                        1.0f + 0.25f * Math.max(1, effect.paramInt("level", 1)))
                .build());

        SPEEDY = id("speedy");
        SmitheryAPI.registerModifier(Modifier.builder(SPEEDY)
                .category(Modifier.ModifierCategory.PASSIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(5)
                .levelCostScaling(1.5f)
                .onCompose(composeArmorAttribute("speedy", () -> Attributes.MOVEMENT_SPEED,
                        "pct", 0.01f, AttributeModifier.Operation.MULTIPLY_TOTAL))
                .build());

        RESISTANT = registerResistance("resistant", null);
        FIRE_RESISTANT = registerResistance("fire_resistant",
                net.minecraft.tags.DamageTypeTags.IS_FIRE);
        BLAST_RESISTANT = registerResistance("blast_resistant",
                net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        PROJECTILE_RESISTANT = registerResistance("projectile_resistant",
                net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);

        // Innate material-trait twins of the resistances above. Deliberately separate ids:
        // applied modifiers replace same-id material grants (last-wins in ToolStats), so an
        // anvil-applied level-1 fire_resistant would DOWNGRADE blaze armor's innate ward if
        // they shared an id. Distinct ids let innate and applied protection stack instead.
        FIREWARD = registerResistance("fireward", net.minecraft.tags.DamageTypeTags.IS_FIRE);
        CRYSTALLINE = registerResistance("crystalline", null);
        IMMOVABLE = registerResistance("immovable", null);

        ENERGIZED = id("energized");
        SmitheryAPI.registerModifier(Modifier.builder(ENERGIZED)
                .category(Modifier.ModifierCategory.PASSIVE)
                .onCompose(composeArmorAttribute("energized", () -> Attributes.MOVEMENT_SPEED,
                        "pct", 0.02f, AttributeModifier.Operation.MULTIPLY_TOTAL))
                .build());

        RESTORING = id("restoring");
        SmitheryAPI.registerModifier(Modifier.builder(RESTORING)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .onArmorTick((effect, ctx) -> {
                    int interval = Math.max(20, effect.paramInt("interval_ticks", 200));
                    Player wearer = ctx.wearer();
                    if (wearer.tickCount % interval != 0) return;
                    var stack = ctx.armor();
                    if (stack.getDamageValue() <= 0) return;
                    if (wearer.totalExperience <= 0 && !wearer.getAbilities().instabuild) return;
                    stack.setDamageValue(stack.getDamageValue() - 1);
                    if (!wearer.getAbilities().instabuild) wearer.giveExperiencePoints(-1);
                })
                .build());

        HIGH_STRIDE = id("high_stride");
        SmitheryAPI.registerModifier(Modifier.builder(HIGH_STRIDE)
                .category(Modifier.ModifierCategory.PASSIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(2)
                .levelCostScaling(2.0f)
                .onCompose(composeArmorAttribute("high_stride", () -> ForgeMod.STEP_HEIGHT_ADDITION.get(),
                        "amount", 0.5f, AttributeModifier.Operation.ADDITION))
                .build());

        AMPHIBIOUS = id("amphibious");
        // 1.20.1 has no oxygen-bonus attribute; the same "drown slower" identity is
        // reconstructed by replenishing air while submerged, scaled by level.
        SmitheryAPI.registerModifier(Modifier.builder(AMPHIBIOUS)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(3)
                .levelCostScaling(1.5f)
                .onArmorTick((effect, ctx) -> {
                    Player wearer = ctx.wearer();
                    if (!wearer.isUnderWater()) return;
                    int level = Math.max(1, effect.paramInt("level", 1));
                    // Air drains 1/tick; refunding on a level-scaled cadence stretches
                    // breath time by ~(1 + level/2).
                    if (wearer.tickCount % Math.max(1, 4 - level) != 0) return;
                    wearer.setAirSupply(Math.min(wearer.getMaxAirSupply(),
                            wearer.getAirSupply() + 1));
                })
                .build());

        POWERFUL = id("powerful");
        SmitheryAPI.registerModifier(Modifier.builder(POWERFUL)
                .category(Modifier.ModifierCategory.PASSIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(3)
                .levelCostScaling(2.0f)
                .onCompose(composeArmorAttribute("powerful", () -> Attributes.ATTACK_DAMAGE,
                        "pct", 0.05f, AttributeModifier.Operation.MULTIPLY_TOTAL))
                .build());

        DEXTEROUS = id("dexterous");
        SmitheryAPI.registerModifier(Modifier.builder(DEXTEROUS)
                .category(Modifier.ModifierCategory.PASSIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(3)
                .levelCostScaling(2.0f)
                .onCompose(composeArmorAttribute("dexterous", () -> Attributes.ATTACK_SPEED,
                        "pct", 0.05f, AttributeModifier.Operation.MULTIPLY_TOTAL))
                .build());

        TELEKINETIC = id("telekinetic");
        Modifier.OnCompose entityReach = composeArmorAttribute("telekinetic_entity",
                () -> ForgeMod.ENTITY_REACH.get(), "amount", 1.0f,
                AttributeModifier.Operation.ADDITION);
        Modifier.OnCompose blockReach = composeArmorAttribute("telekinetic_block",
                () -> ForgeMod.BLOCK_REACH.get(), "amount", 1.0f,
                AttributeModifier.Operation.ADDITION);
        SmitheryAPI.registerModifier(Modifier.builder(TELEKINETIC)
                .category(Modifier.ModifierCategory.PASSIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(2)
                .levelCostScaling(2.0f)
                .onCompose((effect, ctx) -> {
                    entityReach.apply(effect, ctx);
                    blockReach.apply(effect, ctx);
                })
                .build());

        // Marker modifier — LivingDropsEvent/EntityJoinLevelEvent handling lives in
        // com.soul.smithery.event.SoulboundHandler, keyed by this id.
        SOULBOUND = id("soulbound");
        SmitheryAPI.registerModifier(Modifier.builder(SOULBOUND)
                .category(Modifier.ModifierCategory.ACTIVE)
                .build());

        ECOLOGICAL = id("ecological");
        SmitheryAPI.registerModifier(Modifier.builder(ECOLOGICAL)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onArmorTick((effect, ctx) -> {
                    int interval = Math.max(20, effect.paramInt("interval_ticks", 2400));
                    if (ctx.wearer().tickCount % interval != 0) return;
                    var stack = ctx.armor();
                    if (stack.getDamageValue() <= 0) return;
                    stack.setDamageValue(stack.getDamageValue() - 1);
                })
                .build());

        CONDUCTIVE = id("conductive");
        SmitheryAPI.registerModifier(Modifier.builder(CONDUCTIVE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onHurt((effect, ctx) -> {
                    if (!ctx.source().is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return;
                    float pct = effect.paramFloat("pct", 0.9f);
                    ctx.amount().set(ctx.amount().get() * (1.0f - Math.min(1.0f, pct)));
                })
                .build());

        ALLURING = id("alluring");
        SmitheryAPI.registerModifier(Modifier.builder(ALLURING)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onArmorTick((effect, ctx) -> {
                    Player wearer = ctx.wearer();
                    if (wearer.tickCount % 10 != 0) return;
                    float radius = effect.paramFloat("radius", 6.0f);
                    Vec3 playerPos = wearer.position();
                    AABB box = wearer.getBoundingBox().inflate(radius);
                    for (var orb : wearer.level().getEntitiesOfClass(
                            net.minecraft.world.entity.ExperienceOrb.class, box)) {
                        Vec3 toward = playerPos.subtract(orb.position());
                        double dist = toward.length();
                        if (dist < 0.5 || dist > radius) continue;
                        orb.setDeltaMovement(toward.normalize().scale(0.3));
                    }
                })
                .build());

        STALWART = id("stalwart");
        SmitheryAPI.registerModifier(Modifier.builder(STALWART)
                .category(Modifier.ModifierCategory.PASSIVE)
                .onCompose(composeArmorAttribute("stalwart", () -> Attributes.KNOCKBACK_RESISTANCE,
                        "amount", 0.05f, AttributeModifier.Operation.ADDITION))
                .build());

        WARDED = id("warded");
        SmitheryAPI.registerModifier(Modifier.builder(WARDED)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onHurt((effect, ctx) -> {
                    if (!ctx.source().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) return;
                    float pct = effect.paramFloat("pct", 0.15f);
                    ctx.amount().set(ctx.amount().get() * (1.0f - Math.min(1.0f, pct)));
                })
                .build());

        AQUADYNAMIC = id("aquadynamic");
        SmitheryAPI.registerModifier(Modifier.builder(AQUADYNAMIC)
                .category(Modifier.ModifierCategory.PASSIVE)
                .onCompose(composeArmorAttribute("aquadynamic",
                () -> ForgeMod.SWIM_SPEED.get(),
                        "amount", 0.15f, AttributeModifier.Operation.MULTIPLY_TOTAL))
                .build());

        NIMBLE = id("nimble");
        SmitheryAPI.registerModifier(Modifier.builder(NIMBLE)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onFall((effect, ctx) -> {
                    float pct = effect.paramFloat("pct", 0.5f);
                    ctx.distance().set(ctx.distance().get() * (1.0 - Math.min(1.0f, pct)));
                })
                .build());

        STICKY = id("sticky");
        SmitheryAPI.registerModifier(Modifier.builder(STICKY)
                .category(Modifier.ModifierCategory.ACTIVE)
                .onDamaged((effect, ctx) -> {
                    if (!(ctx.source().getEntity() instanceof LivingEntity attacker)) return;
                    if (attacker == ctx.wearer()) return;
                    int duration = effect.paramInt("duration_ticks", 60);
                    int amp = effect.paramInt("amplifier", 1);
                    attacker.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, duration, amp));
                })
                .build());

        MOMENTUM = id("momentum");
        SmitheryAPI.registerModifier(Modifier.builder(MOMENTUM)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .onBlockBreak((effect, ctx) -> {
                    int cap = effect.paramInt("max_amplifier", 2);
                    var current = ctx.player().getEffect(MobEffects.DIG_SPEED);
                    int nextAmp = current == null ? 0 : Math.min(cap, current.getAmplifier() + 1);
                    ctx.player().addEffect(new MobEffectInstance(
                            MobEffects.DIG_SPEED, 60, nextAmp, true, false));
                })
                .build());

        JAGGED = id("jagged");
        SmitheryAPI.registerModifier(Modifier.builder(JAGGED)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .onDealDamage((effect, ctx) -> {
                    var tool = ctx.tool();
                    if (!tool.isDamageableItem() || tool.getMaxDamage() <= 0) return;
                    float missing = (float) tool.getDamageValue() / tool.getMaxDamage();
                    float pct = effect.paramFloat("pct", 0.5f);
                    ctx.amount().set(ctx.amount().get() * (1.0f + pct * missing));
                })
                .build());

        STONEBOUND = id("stonebound");
        SmitheryAPI.registerModifier(Modifier.builder(STONEBOUND)
                .category(Modifier.ModifierCategory.BOTH)
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .durabilityScaled()
                .passive((effect, stats) -> {
                    stats.bonusMiningSpeed += effect.paramFloat("speed_bonus", 4.0f)
                            * stats.missingDurability;
                    stats.bonusAttackDamage -= effect.paramFloat("damage_penalty", 2.0f)
                            * stats.missingDurability;
                })
                .build());

        AUTOSMELT = id("autosmelt");
        SmitheryAPI.registerModifier(Modifier.builder(AUTOSMELT)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .onBlockDrops((effect, ctx) -> {
                    var level = ctx.level();
                    for (ItemEntity drop : ctx.drops()) {
                        var container = new net.minecraft.world.SimpleContainer(drop.getItem());
                        level.getRecipeManager().getRecipeFor(
                                        net.minecraft.world.item.crafting.RecipeType.SMELTING, container, level)
                                .ifPresent(recipe -> {
                                    net.minecraft.world.item.ItemStack smelted =
                                            recipe.assemble(container, level.registryAccess()).copy();
                                    if (smelted.isEmpty()) return;
                                    smelted.setCount(smelted.getCount() * drop.getItem().getCount());
                                    drop.setItem(smelted);
                                });
                    }
                })
                .build());

        FIERY = id("fiery");
        SmitheryAPI.registerModifier(Modifier.builder(FIERY)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .maxLevel(3)
                .levelCostScaling(2.0f)
                .onAttackEntity((effect, ctx) -> {
                    int level = Math.max(1, effect.paramInt("level", 1));
                    ctx.target().setSecondsOnFire(2 * level);
                })
                .build());

        ModifierSources.register(Items.NETHER_STAR,
                ModifierEffect.of(NETHER_SHARPENED, Map.of("damage", 6.0f)));
        ModifierSources.register(Items.PHANTOM_MEMBRANE,
                ModifierEffect.of(LUNGE, Map.of("level", 1)));
        ModifierSources.register(Items.OBSIDIAN,
                ModifierEffect.of(REINFORCED, Map.of("level", 1)));
        ModifierSources.register(Items.SUGAR,
                ModifierEffect.of(SPEEDY, Map.of("level", 1)));
        ModifierSources.register(Items.AMETHYST_SHARD,
                ModifierEffect.of(RESISTANT, Map.of("level", 1, "pct", 0.02f)));
        ModifierSources.register(Items.MAGMA_CREAM,
                ModifierEffect.of(FIRE_RESISTANT, Map.of("level", 1, "pct", 0.04f)));
        ModifierSources.register(Items.TNT,
                ModifierEffect.of(BLAST_RESISTANT, Map.of("level", 1, "pct", 0.04f)));
        ModifierSources.register(Items.SCUTE,
                ModifierEffect.of(PROJECTILE_RESISTANT, Map.of("level", 1, "pct", 0.04f)));
        ModifierSources.register(Items.TOTEM_OF_UNDYING,
                ModifierEffect.of(SOULBOUND, Map.of()));
        ModifierSources.register(Items.MOSS_BLOCK,
                ModifierEffect.of(RESTORING, Map.of("interval_ticks", 200)));
        ModifierSources.register(Items.PISTON,
                ModifierEffect.of(HIGH_STRIDE, Map.of("level", 1)));
        ModifierSources.register(Items.PRISMARINE_CRYSTALS,
                ModifierEffect.of(AMPHIBIOUS, Map.of("level", 1)));
        ModifierSources.register(Items.BLAZE_ROD,
                ModifierEffect.of(POWERFUL, Map.of("level", 1)));
        ModifierSources.register(Items.FEATHER,
                ModifierEffect.of(DEXTEROUS, Map.of("level", 1)));
        ModifierSources.register(Items.END_ROD,
                ModifierEffect.of(TELEKINETIC, Map.of("level", 1)));
        ModifierSources.register(Items.BLAZE_POWDER,
                ModifierEffect.of(FIERY, Map.of("level", 1)));

        // Hookless marker like soulbound — AoeMiningHandler reads it off APPLIED_MODIFIERS.
        EXCAVATING = id("excavating");
        SmitheryAPI.registerModifier(Modifier.builder(EXCAVATING)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.TOOLS)
                .maxLevel(2)
                .levelCostScaling(3.0f)
                .build());
        ModifierSources.register(Items.STICKY_PISTON,
                ModifierEffect.of(EXCAVATING, Map.of("level", 1)));
    }

    /**
     * Builds a compose hook that appends one attribute modifier to an armor stack, scaled by
     * the effect's {@code level} (default 1) × the {@code amountParam} value. The entry name
     * embeds the slot name so pieces of a set never collide. No-ops on non-armor stacks.
     *
     * <p>Bonuses ride the stack's extra-attribute NBT (see
     * {@link SmitheryToolData#putExtraAttribute}) and are merged into the armor's live
     * attribute map on top of the composed base defense.
     */
    private static Modifier.OnCompose composeArmorAttribute(
            String name, Supplier<Attribute> attribute,
            String amountParam, float defaultAmount, AttributeModifier.Operation operation) {
        return (effect, ctx) -> {
            var stack = ctx.stack();
            if (!(stack.getItem() instanceof SmitheryArmorItem armorItem)) return;
            ResourceLocation attributeId = ForgeRegistries.ATTRIBUTES.getKey(attribute.get());
            if (attributeId == null) return;
            int level = Math.max(1, effect.paramInt("level", 1));
            double amount = effect.paramFloat(amountParam, defaultAmount) * (double) level;
            EquipmentSlot slot = SmitheryArmorItem.slotForToolTypeId(armorItem.toolTypeId());
            SmitheryToolData.putExtraAttribute(stack, new SmitheryToolData.ExtraAttribute(
                    name + "." + slot.getName(), attributeId, amount, operation, slot));
        };
    }

    /**
     * Registers one damage-reduction armor modifier. Reduction is {@code pct × level} per worn
     * piece carrying the modifier (multiplicative across pieces), gated to damage matching
     * {@code typeFilter} — or all damage when the filter is null.
     */
    private static ResourceLocation registerResistance(
            String path, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> typeFilter) {
        ResourceLocation modId = id(path);
        SmitheryAPI.registerModifier(Modifier.builder(modId)
                .category(Modifier.ModifierCategory.ACTIVE)
                .appliesTo(Modifier.AppliesTo.ARMOR)
                .maxLevel(5)
                .levelCostScaling(1.5f)
                .onHurt((effect, ctx) -> {
                    if (typeFilter != null && !ctx.source().is(typeFilter)) return;
                    int level = Math.max(1, effect.paramInt("level", 1));
                    float pct = effect.paramFloat("pct", 0.02f);
                    float reduction = Math.min(0.8f, pct * level);
                    ctx.amount().set(ctx.amount().get() * (1.0f - reduction));
                })
                .build());
        return modId;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Smithery.MODID, path);
    }

    private SmitheryModifiers() {}
}
