package com.soul.smithery.api.modifier;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Registered modifier definition holding metadata and behavior callbacks for every
 * {@link ModifierEffect} that points at it.
 *
 * <p>Behavior falls into three categories: passive (baked into composed tool stats at craft time),
 * active (event hooks fired during gameplay — attack, block break, etc.), and durability (a flat
 * multiplier applied to the final tool durability). Concrete behavior is supplied via callback
 * interfaces registered through {@link Builder}; this class stores metadata and the registry
 * routes events to the callbacks.
 */
public final class Modifier {
    private final Identifier id;
    private final ModifierCategory category;
    private final int maxLevel;
    private final int levelCost;
    private final float levelCostScaling;
    private final float durabilityMultiplier;
    private final PassiveBehavior passive;
    private final OnAttackEntity onAttack;
    private final OnBlockBreak onBreak;
    private final OnBlockDrops onBlockDrops;
    private final OnKill onKill;
    private final OnMobDrops onMobDrops;
    private final OnCompose onCompose;

    private Modifier(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        this.category = b.category;
        this.maxLevel = Math.max(1, b.maxLevel);
        this.levelCost = Math.max(1, b.levelCost);
        this.levelCostScaling = b.levelCostScaling;
        this.durabilityMultiplier = b.durabilityMultiplier;
        this.passive = b.passive;
        this.onAttack = b.onAttack;
        this.onBreak = b.onBreak;
        this.onBlockDrops = b.onBlockDrops;
        this.onKill = b.onKill;
        this.onMobDrops = b.onMobDrops;
        this.onCompose = b.onCompose;
    }

    /** Identifier for this modifier. */
    public Identifier id() { return id; }

    /** Behavior category bucket assigned by the builder. */
    public ModifierCategory category() { return category; }

    /** Maximum stack level this modifier can reach via anvil application (1 = single-shot). */
    public int maxLevel() { return maxLevel; }

    /**
     * Units required to advance from {@code currentLevel} to the next level.
     *
     * <p>Sources contribute their {@code unit_value} per item — mixed sources are additive. Cost
     * scales by {@link #levelCostScaling} per existing level.
     */
    public int levelCostFor(int currentLevel) {
        if (levelCostScaling <= 1.0f) return Math.max(1, levelCost);
        double cost = levelCost * Math.pow(levelCostScaling, Math.max(0, currentLevel));
        return Math.max(1, (int) Math.ceil(cost));
    }

    /** Units required for the first level. */
    public int levelCost() { return levelCost; }

    /** Geometric scaling factor applied to {@link #levelCost} per existing level. */
    public float levelCostScaling() { return levelCostScaling; }

    /** Final durability multiplier applied to the assembled tool. */
    public float durabilityMultiplier() { return durabilityMultiplier; }

    /** Optional passive (compose-time) stat-adjustment callback. */
    public PassiveBehavior passive() { return passive; }

    /** Optional on-attack callback. */
    public OnAttackEntity onAttack() { return onAttack; }

    /** Optional on-block-break callback (pre-break). */
    public OnBlockBreak onBreak() { return onBreak; }

    /** Optional on-block-drops callback (post-break). */
    public OnBlockDrops onBlockDrops() { return onBlockDrops; }

    /** Optional on-kill XP callback. */
    public OnKill onKill() { return onKill; }

    /** Optional on-mob-drops callback (Looting-style scaling). */
    public OnMobDrops onMobDrops() { return onMobDrops; }

    /** Optional tool-compose-time callback. */
    public OnCompose onCompose() { return onCompose; }

    /** Begins building a {@link Modifier} with the given id. */
    public static Builder builder(Identifier id) { return new Builder(id); }

    /** Behavior category bucket for a modifier. */
    public enum ModifierCategory {
        /** Compose-time stat adjustment only — no runtime event handling. */
        PASSIVE,
        /** Runtime event handling only — no passive stat adjustment. */
        ACTIVE,
        /** Both passive and active behavior present. */
        BOTH
    }

    /** Computes a passive stat adjustment from this modifier's params for an in-progress build. */
    @FunctionalInterface
    public interface PassiveBehavior {
        /** Mutates {@code stats} in place to apply this modifier's passive contribution. */
        void apply(ModifierEffect effect, MutablePassiveStats stats);
    }

    /** Callback invoked when a player attacks an entity with the tool. */
    @FunctionalInterface
    public interface OnAttackEntity {
        /** Fires on attack with this tool. */
        void onAttack(ModifierEffect effect, AttackContext ctx);
    }

    /** Callback invoked when a player begins breaking a block with the tool (pre-break). */
    @FunctionalInterface
    public interface OnBlockBreak {
        /** Fires on block break with this tool. */
        void onBreak(ModifierEffect effect, BlockBreakContext ctx);
    }

    /**
     * Callback fired AFTER a block has been broken and its drops spawned (NeoForge
     * {@code BlockDropsEvent}). Use this instead of {@link OnBlockBreak} when the action needs to
     * inspect or modify the drop list or XP amount.
     */
    @FunctionalInterface
    public interface OnBlockDrops {
        /** Fires after block drops have been computed. */
        void onDrops(ModifierEffect effect, BlockDropsContext ctx);
    }

    /**
     * Callback fired from NeoForge's {@code LivingExperienceDropEvent} when an entity killed by
     * the tool's owner is about to drop XP. Use for kill-XP multipliers and similar.
     */
    @FunctionalInterface
    public interface OnKill {
        /** Fires when a killed entity is about to drop XP. */
        void onKill(ModifierEffect effect, KillContext ctx);
    }

    /**
     * Callback fired when an entity killed by the tool's owner drops items
     * (NeoForge {@code LivingDropsEvent}). Receives the mutable drops list and is used for
     * Looting-style emulation that scales per-drop counts.
     */
    @FunctionalInterface
    public interface OnMobDrops {
        /** Fires when a killed entity drops items. */
        void onDrops(ModifierEffect effect, MobDropsContext ctx);
    }

    /**
     * Callback fired at tool-compose time inside {@code SmitheryToolItem.applyComposition}.
     *
     * <p>Receives a {@link ComposeContext} carrying the stack being composed AND an optional
     * registry lookup for any registry access the action needs (notably the enchantment registry,
     * which is data-pack-driven in 1.21+ and unavailable from {@code BuiltInRegistries}). The
     * action mutates the stack directly, typically by adding entries to its
     * {@code ENCHANTMENTS} data component or similar.
     */
    @FunctionalInterface
    public interface OnCompose {
        /** Fires at tool-compose time. */
        void apply(ModifierEffect effect, ComposeContext ctx);
    }

    /** Mutable holder for passive stat adjustments accumulated during tool assembly. */
    public static final class MutablePassiveStats {
        /** Bonus attack damage to add. */
        public float bonusAttackDamage = 0f;
        /** Bonus mining speed to add. */
        public float bonusMiningSpeed = 0f;
        /** Final durability multiplier (1.0 = unchanged). */
        public float durabilityMultiplier = 1f;
    }

    /**
     * Context passed to active modifier hooks on attack.
     *
     * @param tool         the tool stack used in the attack
     * @param attacker     the entity that initiated the attack
     * @param target       the entity hit
     * @param damageDealt  the final damage dealt
     */
    public record AttackContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.LivingEntity attacker,
            net.minecraft.world.entity.Entity target,
            float damageDealt
    ) {}

    /**
     * Context passed to active modifier hooks on block break.
     *
     * @param tool   the tool stack used to break the block
     * @param player the player breaking the block
     * @param level  the level the break is happening in
     * @param pos    the block position
     * @param state  the block state at {@code pos} before breaking
     */
    public record BlockBreakContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state
    ) {}

    /**
     * Context passed to compose hooks.
     *
     * @param stack  the tool stack being assembled
     * @param lookup optional registry lookup; may be {@code null} in client-side contexts with no
     *               live registry access — actions that require it should check and no-op
     */
    public record ComposeContext(
            net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup
    ) {}

    /**
     * Context for {@link OnBlockDrops} hooks — fires AFTER the block drops.
     *
     * @param tool        the tool stack used to break the block
     * @param player      the player who broke the block
     * @param level       the server level the break happened in
     * @param pos         the block position
     * @param state       the block state at {@code pos} before breaking
     * @param drops       the live mutable list of drops; actions may rearrange, inject, or remove
     * @param xpAccessor  read/write access to the XP that will drop
     */
    public record BlockDropsContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            java.util.List<net.minecraft.world.entity.item.ItemEntity> drops,
            XpAccessor xpAccessor
    ) {
        /** Current XP about to drop. */
        public int xp() { return xpAccessor.get(); }
        /** Override the XP about to drop. */
        public void setXp(int value) { xpAccessor.set(value); }
    }

    /**
     * Context for {@link OnKill} hooks.
     *
     * @param tool        the tool stack used in the kill
     * @param attacker    the player who killed the victim
     * @param victim      the killed entity
     * @param xpAccessor  read/write access to the XP that will drop
     */
    public record KillContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player attacker,
            net.minecraft.world.entity.LivingEntity victim,
            XpAccessor xpAccessor
    ) {
        /** Current XP about to drop. */
        public int xp() { return xpAccessor.get(); }
        /** Override the XP about to drop. */
        public void setXp(int value) { xpAccessor.set(value); }
    }

    /** Tiny indirection so modifier callbacks can adjust XP without taking a whole event object. */
    public interface XpAccessor {
        /** Reads the current XP value. */
        int get();
        /** Overrides the XP value. */
        void set(int value);
    }

    /**
     * Context for {@link OnMobDrops} hooks.
     *
     * @param tool      the tool stack used in the kill
     * @param attacker  the player who killed the victim
     * @param victim    the killed entity
     * @param drops     the live mutable drops collection; actions may append, remove, or reposition
     */
    public record MobDropsContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player attacker,
            net.minecraft.world.entity.LivingEntity victim,
            java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops
    ) {}

    /** Fluent builder for {@link Modifier}. */
    public static final class Builder {
        private final Identifier id;
        private ModifierCategory category = ModifierCategory.PASSIVE;
        private int maxLevel = 1;
        private int levelCost = 1;
        private float levelCostScaling = 1.0f;
        private float durabilityMultiplier = 1.0f;
        private PassiveBehavior passive;
        private OnAttackEntity onAttack;
        private OnBlockBreak onBreak;
        private OnBlockDrops onBlockDrops;
        private OnKill onKill;
        private OnMobDrops onMobDrops;
        private OnCompose onCompose;

        private Builder(Identifier id) { this.id = id; }

        /** Overrides the auto-derived category bucket. */
        public Builder category(ModifierCategory c) { this.category = c; return this; }

        /** Sets the maximum stack level reachable via anvil application. */
        public Builder maxLevel(int v) { this.maxLevel = v; return this; }

        /** Units required for the first level; subsequent levels scale by {@link #levelCostScaling}. */
        public Builder levelCost(int v) { this.levelCost = v; return this; }

        /** Geometric per-level scaling factor for {@link #levelCost}. */
        public Builder levelCostScaling(float v) { this.levelCostScaling = v; return this; }

        /** Sets the final durability multiplier applied to the assembled tool. */
        public Builder durabilityMultiplier(float v) { this.durabilityMultiplier = v; return this; }

        /** Attaches a passive (compose-time) stat-adjustment behavior. */
        public Builder passive(PassiveBehavior b) { this.passive = b; if (category == ModifierCategory.ACTIVE) category = ModifierCategory.BOTH; return this; }

        /** Attaches an on-attack callback. */
        public Builder onAttackEntity(OnAttackEntity h) { this.onAttack = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }

        /** Attaches an on-block-break callback (pre-break). */
        public Builder onBlockBreak(OnBlockBreak h) { this.onBreak = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }

        /** Attaches an on-block-drops callback (post-break). */
        public Builder onBlockDrops(OnBlockDrops h) { this.onBlockDrops = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }

        /** Attaches an on-kill XP callback. */
        public Builder onKill(OnKill h) { this.onKill = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }

        /** Attaches an on-mob-drops callback. */
        public Builder onMobDrops(OnMobDrops h) { this.onMobDrops = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }

        /** Attaches a tool-compose-time callback; treated as PASSIVE for category bookkeeping. */
        public Builder onCompose(OnCompose h) { this.onCompose = h; return this; }

        /** Finalizes and returns the built {@link Modifier}. */
        public Modifier build() { return new Modifier(this); }
    }
}
