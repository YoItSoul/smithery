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
    private final float durabilityMultiplier;
    private final PassiveBehavior passive;
    private final OnAttackEntity onAttack;
    private final OnBlockBreak onBreak;

    private Modifier(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        this.category = b.category;
        this.durabilityMultiplier = b.durabilityMultiplier;
        this.passive = b.passive;
        this.onAttack = b.onAttack;
        this.onBreak = b.onBreak;
    }

    public Identifier id() { return id; }
    public ModifierCategory category() { return category; }
    public float durabilityMultiplier() { return durabilityMultiplier; }
    public PassiveBehavior passive() { return passive; }
    public OnAttackEntity onAttack() { return onAttack; }
    public OnBlockBreak onBreak() { return onBreak; }

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

    public static final class Builder {
        private final Identifier id;
        private ModifierCategory category = ModifierCategory.PASSIVE;
        private float durabilityMultiplier = 1.0f;
        private PassiveBehavior passive;
        private OnAttackEntity onAttack;
        private OnBlockBreak onBreak;

        private Builder(Identifier id) { this.id = id; }

        public Builder category(ModifierCategory c) { this.category = c; return this; }
        public Builder durabilityMultiplier(float v) { this.durabilityMultiplier = v; return this; }
        public Builder passive(PassiveBehavior b) { this.passive = b; if (category == ModifierCategory.ACTIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onAttackEntity(OnAttackEntity h) { this.onAttack = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }
        public Builder onBlockBreak(OnBlockBreak h) { this.onBreak = h; if (category == ModifierCategory.PASSIVE) category = ModifierCategory.BOTH; return this; }

        public Modifier build() { return new Modifier(this); }
    }
}
