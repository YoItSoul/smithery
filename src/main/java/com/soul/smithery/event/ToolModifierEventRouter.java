package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Central dispatcher that fans vanilla / Forge gameplay events out to the active-modifier
 * callbacks on each registered {@link Modifier}.
 *
 * <p>Covers block-break, block-drops, mob-drops, and kill-XP events on the player's main-hand
 * smithery tool. Attack dispatch lives in {@link SmitheryToolItem} so that the spear's pierce
 * burst routes once per pierced entity, with once-per-attack modifiers gating themselves.
 *
 * <p>1.20.1 has no block-drops event, so drop manipulation is reconstructed in two stages:
 * XP-side {@code onBlockDrops} work runs at {@link BlockEvent.BreakEvent} time (with an empty
 * drops list) while the XP amount is still writable, and drop-side work runs per spawned drop
 * by correlating same-tick {@link ItemEntity} spawns near the broken position. Hooks therefore
 * see either an empty drop list with live XP, or a one-drop list with inert XP — the two
 * archetypes (XP scaling, per-drop transformation) each act exactly once.
 *
 * <p>Effect resolution (and its efficiency notes) lives in {@link ModifierDispatch}, shared
 * with {@link ArmorModifierEventRouter}.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class ToolModifierEventRouter {
    private ToolModifierEventRouter() {}

    /** Battlesign identity: blocks 60% of blockable damage rather than a shield's 100%. */
    private static final float BATTLESIGN_BLOCK_FRACTION = 0.6f;

    /** One recent smithery-tool block break, pending correlation with its spawned drops. */
    private record BreakCapture(ServerLevel level, BlockPos pos, BlockState state,
                                Player player, ItemStack tool, long gameTime) {}

    private static final List<BreakCapture> RECENT_BREAKS = new ArrayList<>();

    /**
     * Routes attacker-side hurt events to every {@code onDealDamage} callback on the
     * attacker's main-hand smithery tool. Fires before the damage lands, so callbacks can scale
     * it (Jagged-style wear bonuses, conditional damage).
     */
    @SubscribeEvent
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide()) return;
        ItemStack tool = attacker.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem toolItem)) return;

        // Rapier identity: thrusts recover a share of the damage the target's armor would
        // absorb (+4% per armor point). Tool-type innate, not a modifier — every rapier has it.
        if ("rapier".equals(toolItem.toolTypeId().getPath())) {
            event.setAmount(event.getAmount() * (1.0f + 0.04f * event.getEntity().getArmorValue()));
        }

        Modifier.FloatAccessor amount = new Modifier.FloatAccessor() {
            @Override public float get() { return event.getAmount(); }
            @Override public void set(float v) { event.setAmount(Math.max(0f, v)); }
        };
        Modifier.DealDamageContext ctx = new Modifier.DealDamageContext(
                tool, attacker, event.getEntity(), amount);
        for (ModifierDispatch.ResolvedEffect r
                : ModifierDispatch.effectsFor(tool, m -> m.onDealDamage() != null)) {
            try {
                r.modifier().onDealDamage().onDealDamage(r.effect(), ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onDealDamage failed: {}",
                        r.modifier().id(), t.toString());
            }
        }
    }

    /**
     * Scales blocked damage down to the battlesign's partial block. The vanilla shield
     * pipeline blocks everything; the battlesign deliberately lets 40% through.
     */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        ItemStack active = event.getEntity().getUseItem();
        if (!(active.getItem() instanceof SmitheryToolItem toolItem)) return;
        if (!"battlesign".equals(toolItem.toolTypeId().getPath())) return;
        event.setBlockedDamage(event.getOriginalBlockedDamage() * BATTLESIGN_BLOCK_FRACTION);
    }

    /**
     * Routes block-break events to every {@code onBreak} callback on the player's main-hand
     * smithery tool, runs the XP stage of {@code onBlockDrops} while the XP amount is still
     * writable, and records the break for same-tick drop correlation.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        Modifier.BlockBreakContext ctx = new Modifier.BlockBreakContext(tool, player, level, pos, state);
        for (ModifierDispatch.ResolvedEffect r
                : ModifierDispatch.effectsFor(tool, m -> m.onBreak() != null)) {
            try {
                r.modifier().onBreak().onBreak(r.effect(), ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onBreak failed: {}",
                        r.modifier().id(), t.toString());
            }
        }

        // XP stage of onBlockDrops: empty drop list, live XP accessor.
        Modifier.XpAccessor xp = new Modifier.XpAccessor() {
            @Override public int get() { return event.getExpToDrop(); }
            @Override public void set(int v) { event.setExpToDrop(Math.max(0, v)); }
        };
        dispatchBlockDrops(tool, player, level, pos, state, new ArrayList<>(), xp);

        long now = level.getGameTime();
        RECENT_BREAKS.removeIf(c -> c.level() != level || c.gameTime() != now);
        RECENT_BREAKS.add(new BreakCapture(level, pos, state, player, tool, now));
    }

    /**
     * Drop stage of {@code onBlockDrops}: correlates item entities spawned in the same tick
     * near a recorded smithery-tool break and runs the drop-side hooks (plus the kama's
     * double-yield crop identity) on each. Hook-removed drops cancel the spawn; hook-added
     * extras are spawned alongside.
     */
    @SubscribeEvent
    public static void onDropSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity drop)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (RECENT_BREAKS.isEmpty()) return;

        long now = level.getGameTime();
        BreakCapture match = null;
        Iterator<BreakCapture> it = RECENT_BREAKS.iterator();
        while (it.hasNext()) {
            BreakCapture c = it.next();
            if (c.level() != level || c.gameTime() != now) {
                it.remove();
                continue;
            }
            if (drop.blockPosition().distSqr(c.pos()) <= 4.0) {
                match = c;
                break;
            }
        }
        if (match == null) return;

        ItemStack tool = match.tool();
        if (!(tool.getItem() instanceof SmitheryToolItem toolItem)) return;

        if ("kama".equals(toolItem.toolTypeId().getPath()) && match.state().is(BlockTags.CROPS)) {
            ItemStack doubled = drop.getItem().copy();
            doubled.setCount(Math.min(doubled.getMaxStackSize(), doubled.getCount() * 2));
            drop.setItem(doubled);
        }

        List<ItemEntity> drops = new ArrayList<>();
        drops.add(drop);
        // Inert XP accessor: the XP stage already ran at BreakEvent time.
        Modifier.XpAccessor xp = new Modifier.XpAccessor() {
            @Override public int get() { return 0; }
            @Override public void set(int v) { }
        };
        dispatchBlockDrops(tool, match.player(), level, match.pos(), match.state(), drops, xp);

        if (!drops.contains(drop)) {
            event.setCanceled(true);
        }
        for (ItemEntity extra : drops) {
            if (extra != drop) {
                level.addFreshEntity(extra);
            }
        }
    }

    private static void dispatchBlockDrops(ItemStack tool, Player player, ServerLevel level,
                                           BlockPos pos, BlockState state,
                                           List<ItemEntity> drops, Modifier.XpAccessor xp) {
        Modifier.BlockDropsContext ctx = new Modifier.BlockDropsContext(
                tool, player, level, pos, state, drops, xp);
        for (ModifierDispatch.ResolvedEffect r
                : ModifierDispatch.effectsFor(tool, m -> m.onBlockDrops() != null)) {
            try {
                r.modifier().onBlockDrops().onDrops(r.effect(), ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onBlockDrops failed: {}",
                        r.modifier().id(), t.toString());
            }
        }
    }

    /**
     * Routes living-drops events to every {@code onMobDrops} callback on the killer's main-hand
     * smithery tool. Used by Looting-style modifiers that scale per-drop count.
     *
     * @param event Forge's living-drops event
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem toolItem)) return;

        // Cleaver identity: 10% innate chance the victim's head joins the drops.
        if ("cleaver".equals(toolItem.toolTypeId().getPath())
                && player.getRandom().nextFloat() < 0.10f) {
            ItemStack head = headFor(event.getEntity());
            if (!head.isEmpty()) {
                event.getDrops().add(new ItemEntity(
                        player.level(), event.getEntity().getX(), event.getEntity().getY(),
                        event.getEntity().getZ(), head));
            }
        }

        Modifier.MobDropsContext ctx = new Modifier.MobDropsContext(
                tool, player, event.getEntity(), event.getDrops());
        for (ModifierDispatch.ResolvedEffect r
                : ModifierDispatch.effectsFor(tool, m -> m.onMobDrops() != null)) {
            try {
                r.modifier().onMobDrops().onDrops(r.effect(), ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onMobDrops failed: {}",
                        r.modifier().id(), t.toString());
            }
        }
    }

    /** Maps beheadable victims to their vanilla head item; empty stack for everything else. */
    private static ItemStack headFor(LivingEntity victim) {
        var type = victim.getType();
        if (type == EntityType.ZOMBIE)          return new ItemStack(Items.ZOMBIE_HEAD);
        if (type == EntityType.SKELETON)        return new ItemStack(Items.SKELETON_SKULL);
        if (type == EntityType.WITHER_SKELETON) return new ItemStack(Items.WITHER_SKELETON_SKULL);
        if (type == EntityType.CREEPER)         return new ItemStack(Items.CREEPER_HEAD);
        if (type == EntityType.PIGLIN)          return new ItemStack(Items.PIGLIN_HEAD);
        if (victim instanceof Player)           return new ItemStack(Items.PLAYER_HEAD);
        return ItemStack.EMPTY;
    }

    /**
     * Routes living-XP-drop events to every {@code onKill} callback on the killer's main-hand
     * smithery tool. Used by kill-XP-multiplier modifiers.
     *
     * @param event Forge's living-experience-drop event
     */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null || killer.level().isClientSide()) return;
        ItemStack tool = killer.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        Modifier.XpAccessor xp = new Modifier.XpAccessor() {
            @Override public int get() { return event.getDroppedExperience(); }
            @Override public void set(int v) { event.setDroppedExperience(v); }
        };
        Modifier.KillContext ctx = new Modifier.KillContext(tool, killer, event.getEntity(), xp);
        for (ModifierDispatch.ResolvedEffect r
                : ModifierDispatch.effectsFor(tool, m -> m.onKill() != null)) {
            try {
                r.modifier().onKill().onKill(r.effect(), ctx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onKill failed: {}",
                        r.modifier().id(), t.toString());
            }
        }
    }
}
