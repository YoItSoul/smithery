package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.item.tool.SmitheryToolItem;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolStats;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.List;

/**
 * Routes vanilla / NeoForge gameplay events to the active-modifier callbacks declared on each
 * registered {@link Modifier}. Without this router, a modifier's {@code onAttackEntity} or
 * {@code onBlockBreak} callback would never fire — only its passive stats would land.
 *
 * <h3>Event coverage</h3>
 * <ul>
 *   <li><b>{@link BreakBlockEvent}</b> — fires when a player breaks a block. Every
 *       active effect's {@code onBlockBreak} is invoked with the player, level, position,
 *       and pre-break block state.</li>
 *   <li><b>{@link BlockDropsEvent}</b>, <b>{@link LivingDropsEvent}</b>,
 *       <b>{@link LivingExperienceDropEvent}</b> — drop / XP modifier hooks.</li>
 * </ul>
 *
 * <h3>Attack-modifier dispatch lives elsewhere</h3>
 * {@code onAttackEntity} is NOT routed from {@code AttackEntityEvent} here. It would only fire
 * from {@code Player.attack} (standard LMB melee) and miss spear charged-stab attacks, which
 * route via {@code PiercingWeapon.attack} → {@code LivingEntity.stabAttack} → {@code Item.hurtEnemy}
 * and never call {@code Player.attack}. Instead, {@code SmitheryToolItem.hurtEnemy} (server-side,
 * fires on BOTH LMB and stab paths) calls the modifiers directly with per-pierced-entity
 * granularity. Once-per-attack modifiers gate themselves (see {@code SmitheryModifiers.LUNGE}'s
 * per-player cooldown).
 *
 * <h3>Coverage caveats</h3>
 * <ul>
 *   <li>Only the player's <em>main-hand</em> stack is checked. Off-hand tool use doesn't
 *       trigger modifiers — matches vanilla mining / attack behaviour.</li>
 *   <li>If the attacker isn't a {@link Player} (e.g. a mob with a Smithery tool — possible
 *       only through unusual setups), the router silently skips. Modifier behaviour is
 *       defined for player-driven combat in v1.</li>
 * </ul>
 *
 * <h3>Stat caching</h3>
 * The router computes {@link ToolStats} fresh per event. For typical play (a few attacks per
 * second per player) this is trivial overhead — the compute path is allocation-light and
 * already runs at craft time. If profiling ever flags it, the natural cache is a transient
 * field on {@link SmitheryToolItem} stamped at component-change time.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class ToolModifierEventRouter {
    private ToolModifierEventRouter() {}

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        List<ResolvedEffect> effects = activeEffectsFor(tool);
        if (effects.isEmpty()) return;

        Modifier.BlockBreakContext ctx = new Modifier.BlockBreakContext(tool, player, level, pos, state);
        for (ResolvedEffect r : effects) {
            if (r.modifier.onBreak() == null) continue;
            try {
                r.modifier.onBreak().onBreak(r.effect, ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onBreak failed: {}",
                        r.modifier.id(), t.toString());
            }
        }
    }

    /**
     * Fires AFTER a block's drops have been spawned as ItemEntities. This is the right hook
     * for any modifier that needs to inspect or manipulate the drops (pull, multiply, replace)
     * or adjust the block's XP drop.
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        List<ResolvedEffect> effects = activeEffectsFor(tool);
        if (effects.isEmpty()) return;

        // Wrap NeoForge's xp getter/setter in our XpAccessor so the modifier context stays
        // decoupled from the specific event type.
        Modifier.XpAccessor xp = new Modifier.XpAccessor() {
            @Override public int get() { return event.getDroppedExperience(); }
            @Override public void set(int v) { event.setDroppedExperience(v); }
        };
        Modifier.BlockDropsContext ctx = new Modifier.BlockDropsContext(
                tool, player, event.getLevel(), event.getPos(), event.getState(),
                event.getDrops(), xp);
        for (ResolvedEffect r : effects) {
            if (r.modifier.onBlockDrops() == null) continue;
            try {
                r.modifier.onBlockDrops().onDrops(r.effect, ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onBlockDrops failed: {}",
                        r.modifier.id(), t.toString());
            }
        }
    }

    /**
     * Fires when a living entity drops its loot after being killed. Used by Looting-style
     * modifiers that scale per-drop count.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        List<ResolvedEffect> effects = activeEffectsFor(tool);
        if (effects.isEmpty()) return;

        Modifier.MobDropsContext ctx = new Modifier.MobDropsContext(
                tool, player, event.getEntity(), event.getDrops());
        for (ResolvedEffect r : effects) {
            if (r.modifier.onMobDrops() == null) continue;
            try {
                r.modifier.onMobDrops().onDrops(r.effect, ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onMobDrops failed: {}",
                        r.modifier.id(), t.toString());
            }
        }
    }

    /**
     * Fires when a living entity drops XP after being killed. Used by modifiers that
     * multiply or modify kill-XP (e.g. lucky_strike).
     */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null || killer.level().isClientSide()) return;
        ItemStack tool = killer.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        List<ResolvedEffect> effects = activeEffectsFor(tool);
        if (effects.isEmpty()) return;

        Modifier.XpAccessor xp = new Modifier.XpAccessor() {
            @Override public int get() { return event.getDroppedExperience(); }
            @Override public void set(int v) { event.setDroppedExperience(v); }
        };
        Modifier.KillContext ctx = new Modifier.KillContext(tool, killer, event.getEntity(), xp);
        for (ResolvedEffect r : effects) {
            if (r.modifier.onKill() == null) continue;
            try {
                r.modifier.onKill().onKill(r.effect, ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onKill failed: {}",
                        r.modifier.id(), t.toString());
            }
        }
    }

    /** Same record as {@link ToolStats.ResolvedEffect} — re-declared so the router doesn't
     *  depend on ToolStats' internals (and to keep iteration clean). */
    private record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

    /**
     * Resolves the union of (a) at-craft modifiers from the tool's composition and (b)
     * post-craft modifiers from the {@code APPLIED_MODIFIERS} component, returning every
     * effect whose modifier has an active callback (onAttack OR onBreak). Pure-passive
     * modifiers are filtered out — they baked into stats at compose time and don't need
     * runtime invocation.
     */
    private static List<ResolvedEffect> activeEffectsFor(ItemStack tool) {
        ToolComposition comp = tool.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (comp == null || !comp.isValid()) return List.of();
        List<ModifierEffect> applied = tool.getOrDefault(
                SmitheryDataComponents.APPLIED_MODIFIERS.get(), List.of());
        ToolStats stats = ToolStats.compute(comp, applied);

        List<ResolvedEffect> out = new java.util.ArrayList<>(stats.activeEffects.size());
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            Modifier mod = SmitheryAPI.MODIFIERS.get(r.effect().modifierId());
            if (mod == null) continue;
            // Include modifiers with ANY of the four runtime callbacks. Earlier this only
            // checked onAttack/onBreak, which silently dropped onBlockDrops + onKill modifiers
            // (golden_touch, magnetized post-event-migration, gilded, lucky_strike) before
            // the router could route to them.
            if (mod.onAttack() == null && mod.onBreak() == null
                    && mod.onBlockDrops() == null && mod.onKill() == null
                    && mod.onMobDrops() == null) continue;
            out.add(new ResolvedEffect(mod, r.effect()));
        }
        return out;
    }
}
