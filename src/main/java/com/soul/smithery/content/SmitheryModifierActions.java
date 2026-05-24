package com.soul.smithery.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierAction;
import com.soul.smithery.api.modifier.ModifierEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Built-in {@link ModifierAction} library. These cover the behaviours used by smithery's
 * code-defined modifiers (Sharp, Magnetized, Verdant, Corrosive, Ender Affinity, Nether
 * Sharpened) so JSON-defined modifiers can compose the same effects without Java.
 *
 * <h2>Action catalog</h2>
 * <ul>
 *   <li>{@link #BONUS_DAMAGE} — {@code smithery:bonus_damage} (passive). Adds a flat amount
 *       to the tool's attack damage at compose time.</li>
 *   <li>{@link #BONUS_MINING_SPEED} — {@code smithery:bonus_mining_speed} (passive). Adds
 *       a flat amount to the tool's mining speed.</li>
 *   <li>{@link #APPLY_MOB_EFFECT} — {@code smithery:apply_mob_effect} (on_attack). Chance
 *       to apply a vanilla MobEffect to the target on hit.</li>
 *   <li>{@link #PULL_DROPS} — {@code smithery:pull_drops} (on_break). Yanks dropped
 *       ItemEntities within radius toward the player.</li>
 *   <li>{@link #TELEPORT_TARGET} — {@code smithery:teleport_target} (on_attack). Chance to
 *       randomly teleport the target a short distance.</li>
 * </ul>
 *
 * <h2>Register order</h2>
 * Must run BEFORE {@code SmitheryModifiers.register()} and BEFORE any JSON modifier reload
 * — otherwise modifier files that reference these action ids will fail to parse with an
 * "unknown action type" error.
 */
public final class SmitheryModifierActions {
    private SmitheryModifierActions() {}

    // ─────────────────────────── Passive: bonus_damage ───────────────────────────

    public record BonusDamage(float amount) implements ModifierAction.Passive {
        public static final ModifierAction.ActionType<BonusDamage> TYPE = ModifierAction.ActionType.of(
                id("bonus_damage"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(BonusDamage::amount)
                ).apply(i, BonusDamage::new)));
        @Override public Identifier type() { return TYPE.id(); }
        @Override public void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect) {
            stats.bonusAttackDamage += effect.paramFloat("amount", amount);
        }
    }

    // ─────────────────────────── Passive: bonus_mining_speed (level-scaling) ───────────────────────────

    /**
     * Adds {@code amount × level} to the tool's mining speed. {@code level} comes from the
     * runtime ModifierEffect (anvil applications track it for stacking modifiers like Haste);
     * for at-craft material grants level defaults to 1. So a modifier definition like
     * {@code { "type": "smithery:bonus_mining_speed", "amount": 1.5 }} adds +1.5 per level,
     * letting redstone-style multi-level modifiers compound linearly.
     */
    public record BonusMiningSpeed(float amount) implements ModifierAction.Passive {
        public static final ModifierAction.ActionType<BonusMiningSpeed> TYPE = ModifierAction.ActionType.of(
                id("bonus_mining_speed"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(BonusMiningSpeed::amount)
                ).apply(i, BonusMiningSpeed::new)));
        @Override public Identifier type() { return TYPE.id(); }
        @Override public void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect) {
            stats.bonusMiningSpeed += effect.paramFloat("amount", amount) * effect.paramInt("level", 1);
        }
    }

    // ─────────────────────────── Passive: bonus_attack_speed (level-scaling) ───────────────────────────

    /**
     * Adds {@code amount × level} to the tool's bonus attack damage as a stand-in for "attack speed"
     * (vanilla 1.21 attack-speed attribute is exposed differently; we route into bonus damage so
     * the modifier still has bite). Pair with bonus_mining_speed to make a Haste-like modifier.
     */
    public record BonusAttackSpeed(float amount) implements ModifierAction.Passive {
        public static final ModifierAction.ActionType<BonusAttackSpeed> TYPE = ModifierAction.ActionType.of(
                id("bonus_attack_speed"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(BonusAttackSpeed::amount)
                ).apply(i, BonusAttackSpeed::new)));
        @Override public Identifier type() { return TYPE.id(); }
        @Override public void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect) {
            stats.bonusAttackDamage += effect.paramFloat("amount", amount) * effect.paramInt("level", 1);
        }
    }

    // ─────────────────────────── OnAttack: apply_mob_effect ───────────────────────────

    public record ApplyMobEffect(Identifier effectId, float chance, int durationTicks, int amplifier)
            implements ModifierAction.OnAttack {
        public static final ModifierAction.ActionType<ApplyMobEffect> TYPE = ModifierAction.ActionType.of(
                id("apply_mob_effect"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Identifier.CODEC.fieldOf("effect").forGetter(ApplyMobEffect::effectId),
                        Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(ApplyMobEffect::chance),
                        Codec.INT.optionalFieldOf("duration_ticks", 60).forGetter(ApplyMobEffect::durationTicks),
                        Codec.INT.optionalFieldOf("amplifier", 0).forGetter(ApplyMobEffect::amplifier)
                ).apply(i, ApplyMobEffect::new)));
        @Override public Identifier type() { return TYPE.id(); }
        @Override public void execute(Modifier.AttackContext ctx, ModifierEffect effect) {
            if (!(ctx.target() instanceof LivingEntity target)) return;
            if (target.level().isClientSide()) return;
            float roll = effect.paramFloat("chance", chance);
            if (target.level().getRandom().nextFloat() >= roll) return;
            int duration = effect.paramInt("duration_ticks", durationTicks);
            int amp = effect.paramInt("amplifier", amplifier);
            // Resolve the vanilla MobEffect by id (e.g. minecraft:poison, minecraft:weakness).
            // Lookup via Holder so the effect carries proper registry data.
            BuiltInRegistries.MOB_EFFECT.get(effectId).ifPresent(holder ->
                    target.addEffect(new MobEffectInstance(holder, duration, amp)));
        }
    }

    // ─────────────────────────── OnBreak: pull_drops ───────────────────────────

    public record PullDrops(float radius) implements ModifierAction.OnBreak {
        public static final ModifierAction.ActionType<PullDrops> TYPE = ModifierAction.ActionType.of(
                id("pull_drops"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("radius").forGetter(PullDrops::radius)
                ).apply(i, PullDrops::new)));
        @Override public Identifier type() { return TYPE.id(); }
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

    // ─────────────────────────── OnAttack: teleport_target ───────────────────────────

    public record TeleportTarget(float chance, float radius) implements ModifierAction.OnAttack {
        public static final ModifierAction.ActionType<TeleportTarget> TYPE = ModifierAction.ActionType.of(
                id("teleport_target"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.FLOAT.optionalFieldOf("chance", 0.30f).forGetter(TeleportTarget::chance),
                        Codec.FLOAT.optionalFieldOf("radius", 4.0f).forGetter(TeleportTarget::radius)
                ).apply(i, TeleportTarget::new)));
        @Override public Identifier type() { return TYPE.id(); }
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

    // ─────────────────────────── OnBlockDrops: bonus_drops (Fortune emulation) ───────────────────────────

    /**
     * Emulates the vanilla Fortune "ore_drops" formula without writing a vanilla enchantment
     * onto the stack. Hooks {@code BlockDropsEvent} and, for each item dropped by the broken
     * block, rolls a random multiplier in {@code [1, level+1]} and adds extra copies to match.
     * Same statistical distribution as Fortune III on iron ore, etc.
     *
     * <h4>Block-tag filter</h4>
     * {@code block_tag} (optional) restricts the multiplier to blocks in that tag. Defaults to
     * "any block" if omitted. For Fortune-like ore bonuses use {@code c:ores}; for "all
     * harvestable" use {@code minecraft:mineable/pickaxe}; for "everything" omit the field.
     *
     * <h4>Why this isn't apply_enchantment</h4>
     * Writing {@code minecraft:fortune} to the stack's ENCHANTMENTS component would:
     * <ul>
     *   <li>Confuse the tooltip ("Fortune I" appearing alongside our smithery modifier)</li>
     *   <li>Expose the tool to anvil + book combinations through edge cases</li>
     *   <li>Mix the modifier system with vanilla enchantment semantics in ways that surprise
     *       modders who expected modifiers to be smithery-internal</li>
     * </ul>
     * Emulating the EFFECT keeps the modifier system the sole source of behaviour.
     */
    public record BonusDrops(java.util.Optional<Identifier> blockTag) implements ModifierAction.OnBlockDrops {
        public static final ModifierAction.ActionType<BonusDrops> TYPE = ModifierAction.ActionType.of(
                id("bonus_drops"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Identifier.CODEC.optionalFieldOf("block_tag").forGetter(BonusDrops::blockTag)
                ).apply(i, BonusDrops::new)));
        @Override public Identifier type() { return TYPE.id(); }
        @Override public void onDrops(Modifier.BlockDropsContext ctx, ModifierEffect effect) {
            // Tag filter: skip blocks outside the configured tag if one was provided.
            if (blockTag.isPresent()) {
                net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag =
                        net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.BLOCK, blockTag.get());
                if (!ctx.state().is(tag)) return;
            }
            int level = effect.paramInt("level", 1);
            net.minecraft.util.RandomSource rng = ctx.level().getRandom();
            // Snapshot the current drop list — we append to it, and don't want to iterate our
            // own appended entries.
            java.util.List<net.minecraft.world.entity.item.ItemEntity> originals =
                    new java.util.ArrayList<>(ctx.drops());
            for (net.minecraft.world.entity.item.ItemEntity drop : originals) {
                net.minecraft.world.item.ItemStack original = drop.getItem();
                if (original.isEmpty()) continue;
                // Vanilla apply_bonus / ore_drops formula (exact):
                //   newCount = originalCount + originalCount * max(0, rng.nextInt(level + 2) - 1)
                // Level 1: 67% no bonus, 33% 2x. Level 2: 50% no, 25% 2x, 25% 3x. Level 3:
                // 40% no, 20% 2x, 20% 3x, 20% 4x. Matches Minecraft's Fortune for ores exactly.
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

    // ─────────────────────────── OnMobDrops: bonus_mob_drops (Looting emulation) ───────────────────────────

    /**
     * Emulates the vanilla Looting effect on mob drops. Same ore_drops-style formula per drop:
     * for each ItemEntity dropped by a killed mob, roll a multiplier in [1, level+1] (with the
     * vanilla max(0, …) discount). Looting on a sword fires here when the player kills mobs
     * — extra copies of each drop are spawned at the death position. Does NOT affect XP drops
     * (XP is handled by separate LivingExperienceDropEvent hooks if needed).
     *
     * <h4>Differences vs. real vanilla Looting</h4>
     * Vanilla Looting also affects rare-drop probabilities (e.g. flesh, skulls). This action
     * only scales count, not probability. For modders who need the rare-drop chance behaviour,
     * fall back to writing the actual minecraft:looting enchantment via apply_enchantment.
     */
    public record BonusMobDrops() implements ModifierAction.OnMobDrops {
        public static final ModifierAction.ActionType<BonusMobDrops> TYPE = ModifierAction.ActionType.of(
                id("bonus_mob_drops"),
                com.mojang.serialization.MapCodec.unit(BonusMobDrops::new));
        @Override public Identifier type() { return TYPE.id(); }
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

    // ─────────────────────────── OnCompose: apply_enchantment ───────────────────────────

    /**
     * Writes a vanilla enchantment to the tool's {@code ENCHANTMENTS} data component at
     * compose time. The enchantment registry is data-pack driven in 1.21+, so we look up
     * via the {@link Modifier.ComposeContext}'s {@code HolderLookup.Provider}. If the lookup
     * is null (e.g. creative-tab init before world load) this action no-ops; the enchantment
     * will be applied on the next composition that has a live registry (typically next
     * server-side recipe assembly / anvil application).
     *
     * <h4>Wrapping vanilla enchantments as smithery modifiers</h4>
     * Each vanilla enchantment can be wrapped in its own JSON modifier file. Example:
     * <pre>{@code
     *   // data/smithery/smithery/modifier/golden_touch.json — smithery:golden_touch
     *   { "on_compose": [{ "type": "smithery:apply_enchantment",
     *                      "enchantment": "minecraft:fortune", "level": 1 }] }
     * }</pre>
     * Materials then grant the wrapper modifier the same way they grant any other modifier:
     * {@code .addModifier(SmitheryToolTypes.PICKAXE, ModifierEffect.of(GOLDEN_TOUCH))}.
     */
    public record ApplyEnchantment(Identifier enchantmentId, int level) implements ModifierAction.OnCompose {
        public static final ModifierAction.ActionType<ApplyEnchantment> TYPE = ModifierAction.ActionType.of(
                id("apply_enchantment"),
                RecordCodecBuilder.mapCodec(i -> i.group(
                        Identifier.CODEC.fieldOf("enchantment").forGetter(ApplyEnchantment::enchantmentId),
                        Codec.INT.optionalFieldOf("level", 1).forGetter(ApplyEnchantment::level)
                ).apply(i, ApplyEnchantment::new)));
        @Override public Identifier type() { return TYPE.id(); }
        @Override public void apply(Modifier.ComposeContext ctx, ModifierEffect effect) {
            // Note: this action writes the ACTUAL vanilla enchantment to the stack's
            // ENCHANTMENTS component. Most smithery modifiers prefer to EMULATE enchantment
            // effects (e.g. smithery:bonus_drops mimics Fortune via BlockDropsEvent) so the
            // tooltip stays clean and modifier system remains the sole source of behaviour.
            // Use apply_enchantment only when you specifically need other mods that inspect
            // the ENCHANTMENTS component to see this enchantment on the tool.
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
            // EnchantmentHelper.updateEnchantments early-returns on null ENCHANTMENTS. Ensure
            // baseline before delegating so this action works on any item, not just ones that
            // pre-set the component via Item.Properties.enchantable(N).
            if (ctx.stack().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS) == null) {
                ctx.stack().set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            }
            ctx.stack().enchant(holder, lvl);
        }
    }

    // ─────────────────────────── Registration ───────────────────────────

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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }
}
