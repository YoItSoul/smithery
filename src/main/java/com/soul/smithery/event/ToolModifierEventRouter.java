package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.item.tool.SmitheryToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.level.BlockDropsEvent;
import net.minecraftforge.event.level.block.BreakBlockEvent;

/**
 * Central dispatcher that fans vanilla / NeoForge gameplay events out to the active-modifier
 * callbacks on each registered {@link Modifier}.
 *
 * <p>Covers block-break, block-drops, mob-drops, and kill-XP events on the player's main-hand
 * smithery tool. Attack dispatch lives in {@link SmitheryToolItem} so that the spear's pierce
 * burst routes once per pierced entity, with once-per-attack modifiers gating themselves.
 *
 * <p>Effect resolution (and its efficiency notes) lives in {@link ModifierDispatch}, shared
 * with {@link ArmorModifierEventRouter}.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class ToolModifierEventRouter {
    private ToolModifierEventRouter() {}

    /**
     * Routes attacker-side incoming-damage events to every {@code onDealDamage} callback on the
     * attacker's main-hand smithery tool. Fires before the damage lands, so callbacks can scale
     * it (Jagged-style wear bonuses, conditional damage).
     */
    @SubscribeEvent
    public static void onDealDamage(net.minecraftforge.event.entity.living.LivingIncomingDamageEvent event) {
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
    }

    /**
     * Routes block-drop events to every {@code onBlockDrops} callback on the player's main-hand
     * smithery tool. Fires after drops have been spawned, which is the correct point for drop
     * inspection / multiplication / XP adjustment. Also applies the kama's harvest identity:
     * crops broken by a kama yield double drops.
     *
     * @param event NeoForge's block-drops event
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem toolItem)) return;

        if ("kama".equals(toolItem.toolTypeId().getPath())
                && event.getState().is(net.minecraft.tags.BlockTags.CROPS)) {
            for (var drop : event.getDrops()) {
                var stack2x = drop.getItem().copy();
                stack2x.setCount(Math.min(stack2x.getMaxStackSize(), stack2x.getCount() * 2));
                drop.setItem(stack2x);
            }
        }

        Modifier.XpAccessor xp = new Modifier.XpAccessor() {
            @Override public int get() { return event.getDroppedExperience(); }
            @Override public void set(int v) { event.setDroppedExperience(v); }
        };
        Modifier.BlockDropsContext ctx = new Modifier.BlockDropsContext(
                tool, player, event.getLevel(), event.getPos(), event.getState(),
                event.getDrops(), xp);
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
     * @param event NeoForge's living-drops event
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof SmitheryToolItem)) return;

        // Cleaver identity: 10% innate chance the victim's head joins the drops.
        if (tool.getItem() instanceof SmitheryToolItem cleaverItem
                && "cleaver".equals(cleaverItem.toolTypeId().getPath())
                && player.getRandom().nextFloat() < 0.10f) {
            ItemStack head = headFor(event.getEntity());
            if (!head.isEmpty()) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
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
        if (type == net.minecraft.world.entity.EntityType.ZOMBIE)
            return new ItemStack(net.minecraft.world.item.Items.ZOMBIE_HEAD);
        if (type == net.minecraft.world.entity.EntityType.SKELETON)
            return new ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL);
        if (type == net.minecraft.world.entity.EntityType.WITHER_SKELETON)
            return new ItemStack(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL);
        if (type == net.minecraft.world.entity.EntityType.CREEPER)
            return new ItemStack(net.minecraft.world.item.Items.CREEPER_HEAD);
        if (type == net.minecraft.world.entity.EntityType.PIGLIN)
            return new ItemStack(net.minecraft.world.item.Items.PIGLIN_HEAD);
        if (victim instanceof Player)
            return new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        return ItemStack.EMPTY;
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
