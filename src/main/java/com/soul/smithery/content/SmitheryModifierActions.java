package com.soul.smithery.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierAction;
import com.soul.smithery.api.modifier.ModifierEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Built-in {@link ModifierAction} library that JSON-defined modifiers reference by id.
 *
 * <p>Provides the action vocabulary (bonus damage, mob-effect application, drop pulling,
 * enchantment writing, etc.) that smithery's code-defined modifiers ship with and that data
 * packs compose against.
 *
 * <p>{@link #register()} must run before {@link SmitheryModifiers#register()} and before any
 * JSON modifier reload — otherwise modifier files that reference these action ids fail to
 * parse with an "unknown action type" error.
 */
public final class SmitheryModifierActions {
    private SmitheryModifierActions() {}

    /**
     * Passive action that adds {@code amount × level} to the tool's bonus attack damage.
     *
     * <p>Level comes from the runtime {@link ModifierEffect} (anvil applications track it for
     * stacking modifiers); for at-craft material grants and synergy effects, level defaults to 1.
     *
     * @param amount per-level damage bonus; default supplied via the modifier effect's
     *               {@code amount} param
     */
    public record BonusDamage(float amount) implements ModifierAction.Passive {
        /** Codec-driven action type registered under {@code smithery:bonus_damage}. */
        public static final ModifierAction.ActionType<BonusDamage> TYPE = ModifierAction.ActionType.of(
                id("bonus_damage"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(BonusDamage::amount)
                ).apply(i, BonusDamage::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect) {
            stats.bonusAttackDamage += effect.paramFloat("amount", amount) * effect.paramInt("level", 1);
        }
    }

    /**
     * Passive action that adds {@code amount × level} to the tool's mining speed.
     *
     * <p>Level comes from the runtime {@link ModifierEffect} (anvil applications track it for
     * stacking modifiers); for at-craft material grants level defaults to 1.
     *
     * @param amount per-level mining-speed bonus
     */
    public record BonusMiningSpeed(float amount) implements ModifierAction.Passive {
        /** Codec-driven action type registered under {@code smithery:bonus_mining_speed}. */
        public static final ModifierAction.ActionType<BonusMiningSpeed> TYPE = ModifierAction.ActionType.of(
                id("bonus_mining_speed"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(BonusMiningSpeed::amount)
                ).apply(i, BonusMiningSpeed::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect) {
            stats.bonusMiningSpeed += effect.paramFloat("amount", amount) * effect.paramInt("level", 1);
        }
    }

    /**
     * Passive action that adds {@code amount × level} to bonus attack damage as a stand-in
     * for an attack-speed contribution.
     *
     * <p>Pair with {@link BonusMiningSpeed} for a Haste-flavoured composite modifier.
     *
     * @param amount per-level damage bonus
     */
    public record BonusAttackSpeed(float amount) implements ModifierAction.Passive {
        /** Codec-driven action type registered under {@code smithery:bonus_attack_speed}. */
        public static final ModifierAction.ActionType<BonusAttackSpeed> TYPE = ModifierAction.ActionType.of(
                id("bonus_attack_speed"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(BonusAttackSpeed::amount)
                ).apply(i, BonusAttackSpeed::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect) {
            stats.bonusAttackDamage += effect.paramFloat("amount", amount) * effect.paramInt("level", 1);
        }
    }

    /**
     * On-attack action that rolls a chance to apply a vanilla MobEffect to the target.
     *
     * @param effectId      identifier of the vanilla MobEffect to apply
     * @param chance        probability in [0, 1] of triggering on hit
     * @param durationTicks effect duration in ticks
     * @param amplifier     effect amplifier (0 = level I)
     */
    public record ApplyMobEffect(ResourceLocation effectId, float chance, int durationTicks, int amplifier)
            implements ModifierAction.OnAttack {
        /** Codec-driven action type registered under {@code smithery:apply_mob_effect}. */
        public static final ModifierAction.ActionType<ApplyMobEffect> TYPE = ModifierAction.ActionType.of(
                id("apply_mob_effect"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("effect").forGetter(ApplyMobEffect::effectId),
                        Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(ApplyMobEffect::chance),
                        Codec.INT.optionalFieldOf("duration_ticks", 60).forGetter(ApplyMobEffect::durationTicks),
                        Codec.INT.optionalFieldOf("amplifier", 0).forGetter(ApplyMobEffect::amplifier)
                ).apply(i, ApplyMobEffect::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void execute(Modifier.AttackContext ctx, ModifierEffect effect) {
            if (!(ctx.target() instanceof LivingEntity target)) return;
            if (target.level().isClientSide()) return;
            float roll = effect.paramFloat("chance", chance);
            if (target.level().getRandom().nextFloat() >= roll) return;
            int duration = effect.paramInt("duration_ticks", durationTicks);
            int amp = effect.paramInt("amplifier", amplifier);
            BuiltInRegistries.MOB_EFFECT.get(effectId).ifPresent(holder ->
                    target.addEffect(new MobEffectInstance(holder, duration, amp)));
        }
    }

    /**
     * On-break action that pulls every dropped ItemEntity within {@code radius} toward the
     * breaking player.
     *
     * @param radius search radius around the broken block in blocks
     */
    public record PullDrops(float radius) implements ModifierAction.OnBreak {
        /** Codec-driven action type registered under {@code smithery:pull_drops}. */
        public static final ModifierAction.ActionType<PullDrops> TYPE = ModifierAction.ActionType.of(
                id("pull_drops"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("radius").forGetter(PullDrops::radius)
                ).apply(i, PullDrops::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void execute(Modifier.BlockBreakContext ctx, ModifierEffect effect) {
            if (ctx.level().isClientSide() || ctx.player() == null) return;
            float r = effect.paramFloat("radius", radius);
            AABB box = new AABB(ctx.pos()).inflate(r);
            for (ItemEntity drop : ctx.level().getEntitiesOfClass(ItemEntity.class, box)) {
                Vec3 toward = ctx.player().position().subtract(drop.position()).normalize().scale(0.4);
                drop.setDeltaMovement(toward);
                drop.setPickUpDelay(0);
            }
        }
    }

    /**
     * On-attack action with a chance to randomly teleport the target within a small radius.
     *
     * @param chance probability in [0, 1] of triggering on hit
     * @param radius maximum teleport distance in blocks
     */
    public record TeleportTarget(float chance, float radius) implements ModifierAction.OnAttack {
        /** Codec-driven action type registered under {@code smithery:teleport_target}. */
        public static final ModifierAction.ActionType<TeleportTarget> TYPE = ModifierAction.ActionType.of(
                id("teleport_target"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.optionalFieldOf("chance", 0.30f).forGetter(TeleportTarget::chance),
                        Codec.FLOAT.optionalFieldOf("radius", 4.0f).forGetter(TeleportTarget::radius)
                ).apply(i, TeleportTarget::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void execute(Modifier.AttackContext ctx, ModifierEffect effect) {
            if (!(ctx.target() instanceof LivingEntity living)) return;
            if (living.level().isClientSide()) return;
            float roll = effect.paramFloat("chance", chance);
            float r = effect.paramFloat("radius", radius);
            net.minecraft.util.RandomSource rng = living.level().getRandom();
            if (rng.nextFloat() >= roll) return;
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = r * (0.5 + rng.nextDouble() * 0.5);
            living.randomTeleport(
                    living.getX() + Math.cos(angle) * dist,
                    living.getY(),
                    living.getZ() + Math.sin(angle) * dist,
                    true);
        }
    }

    /**
     * On-block-drops action that emulates the vanilla Fortune "ore_drops" formula without
     * writing an enchantment onto the stack.
     *
     * <p>For each item dropped by the broken block, rolls an extra multiplier in [0, level]
     * via Minecraft's exact {@code max(0, rng.nextInt(level + 2) - 1)} formula and appends
     * additional copies to the drops list. Same statistical distribution as Fortune on ores.
     *
     * @param blockTag optional block-tag id restricting the multiplier to matching blocks; when
     *                 absent, the bonus applies to any block
     */
    public record BonusDrops(java.util.Optional<ResourceLocation> blockTag) implements ModifierAction.OnBlockDrops {
        /** Codec-driven action type registered under {@code smithery:bonus_drops}. */
        public static final ModifierAction.ActionType<BonusDrops> TYPE = ModifierAction.ActionType.of(
                id("bonus_drops"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.optionalFieldOf("block_tag").forGetter(BonusDrops::blockTag)
                ).apply(i, BonusDrops::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void onDrops(Modifier.BlockDropsContext ctx, ModifierEffect effect) {
            if (blockTag.isPresent()) {
                net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag =
                        net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.BLOCK, blockTag.get());
                if (!ctx.state().is(tag)) return;
            }
            int level = effect.paramInt("level", 1);
            net.minecraft.util.RandomSource rng = ctx.level().getRandom();
            java.util.List<net.minecraft.world.entity.item.ItemEntity> originals =
                    new java.util.ArrayList<>(ctx.drops());
            for (net.minecraft.world.entity.item.ItemEntity drop : originals) {
                net.minecraft.world.item.ItemStack original = drop.getItem();
                if (original.isEmpty()) continue;
                int extraMult = Math.max(0, rng.nextInt(level + 2) - 1);
                if (extraMult == 0) continue;
                int extraCount = original.getCount() * extraMult;
                if (extraCount <= 0) continue;
                net.minecraft.world.item.ItemStack bonus = original.copy();
                bonus.setCount(extraCount);
                ctx.drops().add(new net.minecraft.world.entity.item.ItemEntity(
                        ctx.level(), drop.getX(), drop.getY(), drop.getZ(), bonus));
            }
        }
    }

    /**
     * On-mob-drops action that emulates the vanilla Looting count-scaling behaviour.
     *
     * <p>Per drop, applies the same {@code max(0, rng.nextInt(level + 2) - 1)} multiplier as
     * Fortune. Does not affect rare-drop probabilities the way real Looting does — only count.
     */
    public record BonusMobDrops() implements ModifierAction.OnMobDrops {
        /** Codec-driven action type registered under {@code smithery:bonus_mob_drops}. */
        public static final ModifierAction.ActionType<BonusMobDrops> TYPE = ModifierAction.ActionType.of(
                id("bonus_mob_drops"),
                com.mojang.serialization.MapCodec.unit(BonusMobDrops::new));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void onDrops(Modifier.MobDropsContext ctx, ModifierEffect effect) {
            int level = effect.paramInt("level", 1);
            net.minecraft.util.RandomSource rng = ctx.victim().level().getRandom();
            java.util.List<net.minecraft.world.entity.item.ItemEntity> originals =
                    new java.util.ArrayList<>(ctx.drops());
            for (net.minecraft.world.entity.item.ItemEntity drop : originals) {
                net.minecraft.world.item.ItemStack original = drop.getItem();
                if (original.isEmpty()) continue;
                int extraMult = Math.max(0, rng.nextInt(level + 2) - 1);
                if (extraMult == 0) continue;
                int extraCount = original.getCount() * extraMult;
                if (extraCount <= 0) continue;
                net.minecraft.world.item.ItemStack bonus = original.copy();
                bonus.setCount(extraCount);
                ctx.drops().add(new net.minecraft.world.entity.item.ItemEntity(
                        ctx.victim().level(), drop.getX(), drop.getY(), drop.getZ(), bonus));
            }
        }
    }

    /**
     * On-compose action that writes a vanilla enchantment to the tool's enchantments component.
     *
     * <p>Most smithery modifiers prefer to emulate enchantment effects via dedicated actions
     * (e.g. {@link BonusDrops} for Fortune) so the tooltip stays clean. Use this action only
     * when other mods need to see the literal enchantment on the stack.
     *
     * @param enchantmentId identifier of the vanilla enchantment to apply
     * @param level         enchantment level; can be overridden via the effect's {@code level} param
     */
    public record ApplyEnchantment(ResourceLocation enchantmentId, int level) implements ModifierAction.OnCompose {
        /** Codec-driven action type registered under {@code smithery:apply_enchantment}. */
        public static final ModifierAction.ActionType<ApplyEnchantment> TYPE = ModifierAction.ActionType.of(
                id("apply_enchantment"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("enchantment").forGetter(ApplyEnchantment::enchantmentId),
                        Codec.INT.optionalFieldOf("level", 1).forGetter(ApplyEnchantment::level)
                ).apply(i, ApplyEnchantment::new)));
        @Override public ResourceLocation type() { return TYPE.id(); }
        @Override public void apply(Modifier.ComposeContext ctx, ModifierEffect effect) {
            net.minecraft.core.HolderLookup.Provider lookup = ctx.lookup();
            if (lookup == null) return;
            var enchRegistry = lookup.lookup(net.minecraft.core.registries.Registries.ENCHANTMENT).orElse(null);
            if (enchRegistry == null) return;
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key =
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.ENCHANTMENT, enchantmentId);
            var holder = enchRegistry.get(key).orElse(null);
            if (holder == null) return;
            int lvl = effect.paramInt("level", level);
            if (ctx.stack().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS) == null) {
                ctx.stack().set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            }
            ctx.stack().enchant(holder, lvl);
        }
    }

    /**
     * Registers every built-in action type with its corresponding action registry.
     *
     * <p>Must run before {@link SmitheryModifiers#register()} and any JSON modifier reload.
     */
    public static void register() {
        ModifierAction.PASSIVE.register(BonusDamage.TYPE);
        ModifierAction.PASSIVE.register(BonusMiningSpeed.TYPE);
        ModifierAction.PASSIVE.register(BonusAttackSpeed.TYPE);
        ModifierAction.ON_ATTACK.register(ApplyMobEffect.TYPE);
        ModifierAction.ON_BREAK.register(PullDrops.TYPE);
        ModifierAction.ON_BLOCK_DROPS.register(BonusDrops.TYPE);
        ModifierAction.ON_MOB_DROPS.register(BonusMobDrops.TYPE);
        ModifierAction.ON_ATTACK.register(TeleportTarget.TYPE);
        ModifierAction.ON_COMPOSE.register(ApplyEnchantment.TYPE);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Smithery.MODID, path);
    }
}
