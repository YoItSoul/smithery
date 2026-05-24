package com.soul.smithery.api.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atomic behaviour primitive composed into modifier definitions.
 *
 * <h2>Why actions exist</h2>
 * Modifier <em>behaviour</em> can't live in JSON directly — JSON can't express arbitrary code.
 * The way to make modifiers data-driven is to define a fixed set of action <em>types</em>
 * (each a Java implementation) that JSON references by id with params. Modders extend the
 * library by registering new action types from their own Java mods; modpack authors compose
 * actions in JSON without touching any code.
 *
 * <h2>Three categories</h2>
 * Actions are bucketed by which modifier hook they fire from:
 * <ul>
 *   <li>{@link Passive} — runs at tool-compose time to adjust {@link Modifier.MutablePassiveStats}
 *       (bonus damage, mining speed, durability multiplier).</li>
 *   <li>{@link OnAttack} — runs when a player attacks an entity with the tool.</li>
 *   <li>{@link OnBreak} — runs when a player breaks a block with the tool.</li>
 * </ul>
 * An action's "category" is determined by which sub-interface it implements (it can implement
 * multiple — e.g. a "shock" action that fires on both attack and break would implement both
 * {@link OnAttack} and {@link OnBreak}).
 *
 * <h2>Param resolution</h2>
 * Action instances carry their parameter values as final fields populated from JSON via their
 * {@link ActionType#codec()}. Each runtime callback also receives the live {@link ModifierEffect}
 * — actions <em>may</em> consult {@code effect.paramFloat("name", default)} to allow a material
 * grant or anvil source to override the JSON-baked default. The convention is JSON-baked values
 * are <em>defaults</em>, runtime ModifierEffect params <em>override</em>.
 *
 * <h2>Registering a new action type (Java)</h2>
 * <pre>{@code
 *   public record SummonLightningAction(float chance) implements ModifierAction.OnAttack {
 *       public static final ActionType<SummonLightningAction> TYPE = ActionType.of(
 *               Identifier.fromNamespaceAndPath("yourmod", "summon_lightning"),
 *               RecordCodecBuilder.mapCodec(i -> i.group(
 *                       Codec.FLOAT.fieldOf("chance").forGetter(SummonLightningAction::chance)
 *               ).apply(i, SummonLightningAction::new)));
 *       public Identifier type() { return TYPE.id(); }
 *       public void execute(AttackContext ctx, ModifierEffect effect) {
 *           float roll = effect.paramFloat("chance", chance);
 *           if (ctx.target().level().getRandom().nextFloat() >= roll) return;
 *           // ... spawn lightning at target.position()
 *       }
 *   }
 *
 *   // In your mod init:
 *   ModifierAction.ON_ATTACK.register(SummonLightningAction.TYPE);
 * }</pre>
 *
 * <h2>Using an action from JSON</h2>
 * Place it in the appropriate bucket of a modifier file at
 * {@code data/<ns>/smithery/modifier/<id>.json}:
 * <pre>{@code
 *   { "on_attack": [ { "type": "yourmod:summon_lightning", "chance": 0.10 } ] }
 * }</pre>
 */
public interface ModifierAction {

    /** The action type id, matching the {@code "type"} field of its JSON entry. */
    Identifier type();

    /** Marker for actions invoked at tool-compose time to adjust passive stats. */
    interface Passive extends ModifierAction {
        void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect);
    }

    /** Marker for actions invoked when a player attacks an entity with the tool. */
    interface OnAttack extends ModifierAction {
        void execute(Modifier.AttackContext ctx, ModifierEffect effect);
    }

    /** Marker for actions invoked when a player breaks a block with the tool. */
    interface OnBreak extends ModifierAction {
        void execute(Modifier.BlockBreakContext ctx, ModifierEffect effect);
    }

    /**
     * Fires AFTER a block has been broken and its drops spawned (NeoForge {@code BlockDropsEvent}).
     * Receives the live mutable drops list and an XP accessor — use for drop manipulation
     * (pull, multiply) or XP bonuses on block breaking.
     */
    interface OnBlockDrops extends ModifierAction {
        void onDrops(Modifier.BlockDropsContext ctx, ModifierEffect effect);
    }

    /**
     * Fires when an entity killed by the tool's owner drops XP (NeoForge {@code LivingExperienceDropEvent}).
     * Receives an XP accessor and the victim entity — use for kill-XP multipliers, on-kill
     * effects, and similar.
     */
    interface OnKill extends ModifierAction {
        void onKill(Modifier.KillContext ctx, ModifierEffect effect);
    }

    /**
     * Fires when an entity killed by the tool's owner drops items (NeoForge {@code LivingDropsEvent}).
     * Receives the mutable drops collection — used for Looting-style emulation that scales
     * drop counts per kill.
     */
    interface OnMobDrops extends ModifierAction {
        void onDrops(Modifier.MobDropsContext ctx, ModifierEffect effect);
    }

    /**
     * Marker for actions invoked at tool-assembly time. Receives a {@link Modifier.ComposeContext}
     * with the stack being assembled and an optional {@code HolderLookup.Provider} for any
     * registry access the action needs (the enchantment registry, for instance, is data-pack
     * driven and only reachable through a lookup provider).
     */
    interface OnCompose extends ModifierAction {
        void apply(Modifier.ComposeContext ctx, ModifierEffect effect);
    }

    /**
     * Type handle paired with its codec. Created via {@link #of} and registered into one of
     * the three category registries below.
     */
    record ActionType<A extends ModifierAction>(Identifier id, MapCodec<A> codec) {
        public static <A extends ModifierAction> ActionType<A> of(Identifier id, MapCodec<A> codec) {
            return new ActionType<>(id, codec);
        }
    }

    /**
     * Per-category registry. Keyed by action id; the {@code dispatchCodec()} reads the JSON
     * {@code "type"} field and looks up the right sub-codec to parse the remainder.
     */
    final class Registry<A extends ModifierAction> {
        private final Map<Identifier, ActionType<? extends A>> entries = new LinkedHashMap<>();
        private final Codec<A> dispatchCodec;

        @SuppressWarnings("unchecked")
        Registry() {
            // Dispatch: read "type", look up codec, parse rest of object. If "type" doesn't
            // match any registered action, parsing fails — surfaced as a /reload log entry.
            this.dispatchCodec = Identifier.CODEC.dispatch(
                    "type",
                    ModifierAction::type,
                    typeId -> {
                        ActionType<? extends A> at = entries.get(typeId);
                        return at == null ? null : (MapCodec<A>) at.codec();
                    });
        }

        public <T extends A> ActionType<T> register(ActionType<T> type) {
            entries.put(type.id(), type);
            return type;
        }

        public Codec<A> dispatchCodec() { return dispatchCodec; }

        public Map<Identifier, ActionType<? extends A>> all() {
            return Collections.unmodifiableMap(entries);
        }
    }

    /** Registry of passive (compose-time) action types. */
    Registry<Passive>  PASSIVE   = new Registry<>();
    /** Registry of on-attack (combat-time) action types. */
    Registry<OnAttack> ON_ATTACK = new Registry<>();
    /** Registry of on-break (block-break-time) action types. */
    Registry<OnBreak>  ON_BREAK  = new Registry<>();
    /** Registry of on-block-drops (post-drop, BlockDropsEvent) action types. */
    Registry<OnBlockDrops> ON_BLOCK_DROPS = new Registry<>();
    /** Registry of on-kill (LivingExperienceDropEvent) action types. */
    Registry<OnKill> ON_KILL = new Registry<>();
    /** Registry of on-mob-drops (LivingDropsEvent) action types. */
    Registry<OnMobDrops> ON_MOB_DROPS = new Registry<>();
    /** Registry of on-compose (tool-assembly-time) action types. */
    Registry<OnCompose> ON_COMPOSE = new Registry<>();
}
