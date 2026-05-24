package com.soul.smithery.api.modifier;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * A registered Modifier. Defines the Java behavior shared by all ModifierEffect instances
 * pointing at it. Behavior categories:
 *
 *   - Passive: applied at craft time, baked into the assembled tool's stats.
 *   - Active:  hooked via event handlers at runtime (block break, attack, etc.).
 *   - Durability: multiplies final tool durability.
 *
 * Concrete behavior is supplied via callback interfaces (passed at registration). Modifier
 * itself just stores metadata; the registry routes events to the callbacks.
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

    public Identifier id() { return id; }
    public ModifierCategory category() { return category; }
    /** Maximum level this modifier can stack to via anvil application. 1 = single-shot (default).
     *  Higher values allow repeated anvil application; each level consumes one modifier slot. */
    public int maxLevel() { return maxLevel; }
    /** Units required to advance from currentLevel to currentLevel+1. Sources contribute their
     *  {@code unit_value} per item — so for a level cost of 90, you need 90 lapis dust
     *  ({@code unit_value=1}) OR 10 lapis blocks ({@code unit_value=9}) OR any mix that totals 90.
     *  Cost scales by {@link #levelCostScaling} per existing level. */
    public int levelCostFor(int currentLevel) {
        if (levelCostScaling <= 1.0f) return Math.max(1, levelCost);
        double cost = levelCost * Math.pow(levelCostScaling, Math.max(0, currentLevel));
        return Math.max(1, (int) Math.ceil(cost));
    }
    public int levelCost() { return levelCost; }
    public float levelCostScaling() { return levelCostScaling; }
    public float durabilityMultiplier() { return durabilityMultiplier; }
    public PassiveBehavior passive() { return passive; }
    public OnAttackEntity onAttack() { return onAttack; }
    public OnBlockBreak onBreak() { return onBreak; }
    public OnBlockDrops onBlockDrops() { return onBlockDrops; }
    public OnKill onKill() { return onKill; }
    public OnMobDrops onMobDrops() { return onMobDrops; }
    public OnCompose onCompose() { return onCompose; }

    public static Builder builder(Identifier id) { return new Builder(id); }

    public enum ModifierCategory { PASSIVE, ACTIVE, BOTH }

    /** Computes a passive stat adjustment from this modifier's params for an in-progress build. */
    @FunctionalInterface
    public interface PassiveBehavior {
        void apply(ModifierEffect effect, MutablePassiveStats stats);
    }

    @FunctionalInterface
    public interface OnAttackEntity {
        void onAttack(ModifierEffect effect, AttackContext ctx);
    }

    @FunctionalInterface
    public interface OnBlockBreak {
        void onBreak(ModifierEffect effect, BlockBreakContext ctx);
    }

    /**
     * Fires from NeoForge's {@code BlockDropsEvent} — AFTER the block has been broken and its
     * drops spawned as ItemEntities. Use this (rather than {@link OnBlockBreak}, which fires
     * pre-break) when the action needs to inspect or modify the drop list or XP amount.
     */
    @FunctionalInterface
    public interface OnBlockDrops {
        void onDrops(ModifierEffect effect, BlockDropsContext ctx);
    }

    /**
     * Fires from NeoForge's {@code LivingExperienceDropEvent} when an entity killed by the
     * tool's owner is about to drop XP. Use for kill-XP multipliers and similar.
     */
    @FunctionalInterface
    public interface OnKill {
        void onKill(ModifierEffect effect, KillContext ctx);
    }

    /**
     * Fires when an entity killed by the tool's owner drops items (NeoForge {@code LivingDropsEvent}).
     * Receives the mutable drops list — used for Looting-style emulation that scales per-drop counts.
     */
    @FunctionalInterface
    public interface OnMobDrops {
        void onDrops(ModifierEffect effect, MobDropsContext ctx);
    }

    /**
     * Fires at tool-compose time (inside {@code SmitheryToolItem.applyComposition}). Receives
     * a {@link ComposeContext} carrying the stack being composed AND a {@link
     * net.minecraft.core.HolderLookup.Provider} for any registry access the action needs
     * (notably the enchantment registry — enchantments are data-pack-driven in 1.21+ and
     * unavailable from {@code BuiltInRegistries}). The action mutates the stack directly,
     * typically by adding entries to its {@code ENCHANTMENTS} data component or similar.
     */
    @FunctionalInterface
    public interface OnCompose {
        void apply(ModifierEffect effect, ComposeContext ctx);
    }

    /** Holder for passive stat adjustments computed during tool assembly. */
    public static final class MutablePassiveStats {
        public float bonusAttackDamage = 0f;
        public float bonusMiningSpeed = 0f;
        public float durabilityMultiplier = 1f;
    }

    /** Context passed to active modifier hooks on attack. */
    public record AttackContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.LivingEntity attacker,
            net.minecraft.world.entity.Entity target,
            float damageDealt
    ) {}

    /** Context passed to active modifier hooks on block break. */
    public record BlockBreakContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state
    ) {}

    /**
     * Context passed to compose hooks. {@code lookup} may be {@code null} if the composition
     * runs in a context with no live registry access (e.g. client-side creative tab init
     * before world load) — actions that require it should check and no-op.
     */
    public record ComposeContext(
            net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider lookup
    ) {}

    /**
     * Context for OnBlockDrops hooks — fires AFTER the block drops. {@code drops} is the
     * live mutable list NeoForge will spawn into the world; actions can rearrange,
     * inject, or remove entries. {@code xpAccessor} reads/writes the XP that will drop.
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
        public int xp() { return xpAccessor.get(); }
        public void setXp(int value) { xpAccessor.set(value); }
    }

    /** Context for OnKill hooks. xpAccessor reads/writes the XP that will drop. */
    public record KillContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player attacker,
            net.minecraft.world.entity.LivingEntity victim,
            XpAccessor xpAccessor
    ) {
        public int xp() { return xpAccessor.get(); }
        public void setXp(int value) { xpAccessor.set(value); }
    }

    /** Tiny indirection so modifier callbacks can adjust XP without taking a whole event obj. */
    public interface XpAccessor {
        int get();
        void set(int value);
    }

    /**
     * Context for OnMobDrops hooks. {@code drops} is the live mutable drops collection
     * (NeoForge's {@code LivingDropsEvent.getDrops()}). Actions can append, remove, or
     * reposition entries.
     */
    public record MobDropsContext(
            net.minecraft.world.item.ItemStack tool,
            net.minecraft.world.entity.player.Player attacker,
            net.minecraft.world.entity.LivingEntity victim,
            java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops
    ) {}

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

        public Builder category(ModifierCategory c) { this.category = c; return this; }
        public Builder maxLevel(int v) { this.maxLevel = v; return this; }
        /** Units required for the first level. Subsequent levels scale by {@link #levelCostScaling}. */
        public Builder levelCost(int v) { this.levelCost = v; return this; }
        public Builder levelCostScaling(float v) { this.levelCostScaling = v; return this; }
        public Builder durabilityMultiplier(float v) { this.durabilityMultiplier = v; return this; }
        public Builder passive(PassiveBehavior b) { this.passive = b; if (category == ModifierCategory.ACTIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onAttackEntity(OnAttackEntity h) { this.onAttack = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onBlockBreak(OnBlockBreak h) { this.onBreak = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onBlockDrops(OnBlockDrops h) { this.onBlockDrops = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onKill(OnKill h) { this.onKill = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onMobDrops(OnMobDrops h) { this.onMobDrops = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }
        /** Compose hooks fire at tool-assembly time; treated as PASSIVE for category bookkeeping
         *  since they don't react to gameplay events. */
        public Builder onCompose(OnCompose h) { this.onCompose = h; return this; }

        public Modifier build() { return new Modifier(this); }
    }
}
