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
 * Central dispatcher that fans vanilla / NeoForge gameplay events out to the active-modifier
 * callbacks on each registered {@link Modifier}.
 *
 * <p>Covers block-break, block-drops, mob-drops, and kill-XP events on the player's main-hand
 * smithery tool. Attack dispatch lives in {@link SmitheryToolItem} so that the spear's pierce
 * burst routes once per pierced entity, with once-per-attack modifiers gating themselves.
 *
 * <p>Stats are recomputed per event via {@link ToolStats#compute}; the path is allocation-light
 * so caching is unnecessary at typical attack frequencies.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class ToolModifierEventRouter {
    private ToolModifierEventRouter() {}

    /**
     * Routes block-break events to every {@code onBreak} callback on the player's main-hand
     * smithery tool.
     *
     * @param event NeoForge's block-break event
     */
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
     * Routes block-drop events to every {@code onBlockDrops} callback on the player's main-hand
     * smithery tool. Fires after drops have been spawned, which is the correct point for drop
     * inspection / multiplication / XP adjustment.
     *
     * @param event NeoForge's block-drops event
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        List<ResolvedEffect> effects = activeEffectsFor(tool);
        if (effects.isEmpty()) return;

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
     * Routes living-drops events to every {@code onMobDrops} callback on the killer's main-hand
     * smithery tool. Used by Looting-style modifiers that scale per-drop count.
     *
     * @param event NeoForge's living-drops event
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
     * Routes living-XP-drop events to every {@code onKill} callback on the killer's main-hand
     * smithery tool. Used by kill-XP-multiplier modifiers.
     *
     * @param event NeoForge's living-experience-drop event
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

    private record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

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
            if (mod.onAttack() == null && mod.onBreak() == null
                    && mod.onBlockDrops() == null && mod.onKill() == null
                    && mod.onMobDrops() == null) continue;
            out.add(new ResolvedEffect(mod, r.effect()));
        }
        return out;
    }
}
