package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.item.tool.SmitheryArmorItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.function.Predicate;

/**
 * Central dispatcher that fans wearer-lifecycle events (hurt, damaged, fall, jump, tick,
 * equip-change) out to the armor callbacks on each registered {@link Modifier}. Mirrors
 * {@link ToolModifierEventRouter}, which owns the held-tool events.
 *
 * <p>Every dispatch walks the wearer's four humanoid armor slots and fires once per worn
 * smithery piece carrying the hook. Broken pieces (see {@link SmitheryArmorItem#isBrokenArmor})
 * are skipped everywhere — broken armor grants nothing, traits included.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID)
public final class ArmorModifierEventRouter {
    private ArmorModifierEventRouter() {}

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Routes incoming damage to {@code onHurt} callbacks; the amount they write wins. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide()) return;

        Modifier.FloatAccessor amount = new Modifier.FloatAccessor() {
            @Override public float get() { return event.getAmount(); }
            @Override public void set(float v) { event.setAmount(Math.max(0f, v)); }
        };
        forEachWornPiece(wearer, m -> m.onHurt() != null, (stack, slot, r) -> {
            Modifier.HurtContext ctx = new Modifier.HurtContext(
                    stack, slot, wearer, event.getSource(), amount);
            r.modifier().onHurt().onHurt(r.effect(), ctx);
        });
    }

    /** Routes applied damage to {@code onDamaged} callbacks (retaliation, on-hit effects). */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide()) return;

        forEachWornPiece(wearer, m -> m.onDamaged() != null, (stack, slot, r) -> {
            Modifier.DamagedContext ctx = new Modifier.DamagedContext(
                    stack, slot, wearer, event.getSource(), event.getAmount());
            r.modifier().onDamaged().onDamaged(r.effect(), ctx);
        });
    }

    /** Routes fall landings to {@code onFall} callbacks; distance and multiplier are writable. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide()) return;

        Modifier.DoubleAccessor distance = new Modifier.DoubleAccessor() {
            @Override public double get() { return event.getDistance(); }
            @Override public void set(double v) { event.setDistance((float) Math.max(0.0, v)); }
        };
        Modifier.FloatAccessor multiplier = new Modifier.FloatAccessor() {
            @Override public float get() { return event.getDamageMultiplier(); }
            @Override public void set(float v) { event.setDamageMultiplier(Math.max(0f, v)); }
        };
        forEachWornPiece(wearer, m -> m.onFall() != null, (stack, slot, r) -> {
            Modifier.FallContext ctx = new Modifier.FallContext(
                    stack, slot, wearer, distance, multiplier);
            r.modifier().onFall().onFall(r.effect(), ctx);
        });
    }

    /** Routes jumps to {@code onJump} callbacks. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide()) return;

        forEachWornPiece(wearer, m -> m.onJump() != null, (stack, slot, r) -> {
            Modifier.JumpContext ctx = new Modifier.JumpContext(stack, slot, wearer);
            r.modifier().onJump().onJump(r.effect(), ctx);
        });
    }

    /**
     * Routes server player ticks (END phase) to {@code onArmorTick} callbacks. Player-only by
     * design — keeps per-tick dispatch bounded by player count rather than entity count.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player wearer = event.player;
        if (wearer.level().isClientSide()) return;

        forEachWornPiece(wearer, m -> m.onArmorTick() != null, (stack, slot, r) -> {
            Modifier.ArmorTickContext ctx = new Modifier.ArmorTickContext(stack, slot, wearer);
            r.modifier().onArmorTick().onTick(r.effect(), ctx);
        });
    }

    /** Routes equip/unequip transitions to {@code onEquipChange} callbacks on both stacks. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide()) return;
        EquipmentSlot slot = event.getSlot();
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return;

        dispatchEquipChange(event.getFrom(), slot, wearer, false);
        dispatchEquipChange(event.getTo(), slot, wearer, true);
    }

    private static void dispatchEquipChange(ItemStack stack, EquipmentSlot slot,
                                            LivingEntity wearer, boolean equipped) {
        if (!(stack.getItem() instanceof SmitheryArmorItem) || SmitheryArmorItem.isBrokenArmor(stack)) return;
        for (ModifierDispatch.ResolvedEffect r
                : ModifierDispatch.effectsFor(stack, m -> m.onEquipChange() != null)) {
            try {
                r.modifier().onEquipChange().onEquipChange(r.effect(),
                        new Modifier.EquipChangeContext(stack, slot, wearer, equipped));
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onEquipChange failed: {}",
                        r.modifier().id(), t.toString());
            }
        }
    }

    /** Per-piece dispatch body receiving the worn stack, its slot, and one resolved effect. */
    @FunctionalInterface
    private interface PieceDispatch {
        void dispatch(ItemStack stack, EquipmentSlot slot, ModifierDispatch.ResolvedEffect effect);
    }

    /**
     * Fires {@code body} for every resolved effect passing {@code hookFilter} on every worn,
     * non-broken smithery armor piece. A callback that throws is logged and skipped so one
     * broken modifier can't take down the event chain.
     */
    private static void forEachWornPiece(LivingEntity wearer, Predicate<Modifier> hookFilter,
                                         PieceDispatch body) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (!(stack.getItem() instanceof SmitheryArmorItem)) continue;
            if (SmitheryArmorItem.isBrokenArmor(stack)) continue;
            if (!ModifierDispatch.hasComposition(stack)) continue;

            List<ModifierDispatch.ResolvedEffect> effects =
                    ModifierDispatch.effectsFor(stack, hookFilter);
            for (ModifierDispatch.ResolvedEffect r : effects) {
                try {
                    body.dispatch(stack, slot, r);
                } catch (Throwable t) {
                    Smithery.LOGGER.error("Modifier {} armor hook failed: {}",
                            r.modifier().id(), t.toString());
                }
            }
        }
    }
}
