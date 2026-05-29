package com.soul.smithery.api.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atomic behavior primitive composed into modifier definitions.
 *
 * <p>Modifier behavior can't live in JSON directly, so Smithery exposes a fixed set of action
 * <em>types</em> (each a Java implementation) that JSON references by id with parameters. Modders
 * extend the library by registering new action types from their own Java mods; modpack authors
 * compose actions in JSON without touching any code.
 *
 * <p>Actions are bucketed by which modifier hook they fire from — {@link Passive},
 * {@link OnAttack}, {@link OnBreak}, {@link OnBlockDrops}, {@link OnKill}, {@link OnMobDrops},
 * {@link OnCompose}. An action can implement more than one bucket (e.g. a "shock" action that
 * fires on both attack and break).
 *
 * <p>Action instances carry their parameter values as final fields populated from JSON via their
 * {@link ActionType#codec()}. Each runtime callback also receives the live {@link ModifierEffect};
 * actions <em>may</em> consult {@code effect.paramFloat(...)} to allow a material grant or anvil
 * source to override the JSON-baked default.
 */
public interface ModifierAction {

    /** The action type id, matching the {@code "type"} field of its JSON entry. */
    Identifier type();

    /** Marker for actions invoked at tool-compose time to adjust passive stats. */
    interface Passive extends ModifierAction {
        /** Mutates {@code stats} based on the effect's params. */
        void apply(Modifier.MutablePassiveStats stats, ModifierEffect effect);
    }

    /** Marker for actions invoked when a player attacks an entity with the tool. */
    interface OnAttack extends ModifierAction {
        /** Executes the action against the given attack context. */
        void execute(Modifier.AttackContext ctx, ModifierEffect effect);
    }

    /** Marker for actions invoked when a player breaks a block with the tool (pre-break). */
    interface OnBreak extends ModifierAction {
        /** Executes the action against the given block-break context. */
        void execute(Modifier.BlockBreakContext ctx, ModifierEffect effect);
    }

    /**
     * Marker for actions fired AFTER a block has been broken and its drops spawned (NeoForge
     * {@code BlockDropsEvent}). Used for drop manipulation (pull, multiply) or XP bonuses.
     */
    interface OnBlockDrops extends ModifierAction {
        /** Executes the action against the post-drop context. */
        void onDrops(Modifier.BlockDropsContext ctx, ModifierEffect effect);
    }

    /**
     * Marker for actions fired when an entity killed by the tool's owner drops XP (NeoForge
     * {@code LivingExperienceDropEvent}). Used for kill-XP multipliers and on-kill effects.
     */
    interface OnKill extends ModifierAction {
        /** Executes the action against the kill context. */
        void onKill(Modifier.KillContext ctx, ModifierEffect effect);
    }

    /**
     * Marker for actions fired when an entity killed by the tool's owner drops items (NeoForge
     * {@code LivingDropsEvent}). Used for Looting-style emulation that scales drop counts.
     */
    interface OnMobDrops extends ModifierAction {
        /** Executes the action against the mob-drops context. */
        void onDrops(Modifier.MobDropsContext ctx, ModifierEffect effect);
    }

    /**
     * Marker for actions invoked at tool-assembly time. Receives a {@link Modifier.ComposeContext}
     * with the stack being assembled and an optional registry lookup for any registry access the
     * action needs (the enchantment registry, for instance, is data-pack-driven and only
     * reachable through a lookup provider).
     */
    interface OnCompose extends ModifierAction {
        /** Executes the action against the compose context. */
        void apply(Modifier.ComposeContext ctx, ModifierEffect effect);
    }

    /**
     * Type handle paired with its codec.
     *
     * @param <A>   the concrete action type
     * @param id    the action type id (matches the JSON {@code "type"} field)
     * @param codec the {@link MapCodec} used to parse the remainder of the JSON object
     */
    record ActionType<A extends ModifierAction>(Identifier id, MapCodec<A> codec) {
        /** Convenience constructor wrapping a type id and codec into an {@link ActionType}. */
        public static <A extends ModifierAction> ActionType<A> of(Identifier id, MapCodec<A> codec) {
            return new ActionType<>(id, codec);
        }
    }

    /**
     * Per-category registry. Keyed by action id; the {@link #dispatchCodec()} reads the JSON
     * {@code "type"} field and looks up the right sub-codec to parse the remainder.
     *
     * @param <A> the marker sub-interface this registry handles
     */
    final class Registry<A extends ModifierAction> {
        private final Map<Identifier, ActionType<? extends A>> entries = new LinkedHashMap<>();
        private final Codec<A> dispatchCodec;

        @SuppressWarnings("unchecked")
        Registry() {
            this.dispatchCodec = Identifier.CODEC.dispatch(
                    "type",
                    ModifierAction::type,
                    typeId -> {
                        ActionType<? extends A> at = entries.get(typeId);
                        return at == null ? null : (MapCodec<A>) at.codec();
                    });
        }

        /** Registers an action type into this category and returns it. */
        public <T extends A> ActionType<T> register(ActionType<T> type) {
            entries.put(type.id(), type);
            return type;
        }

        /** Dispatch codec keyed by {@code "type"} that parses any registered action of this category. */
        public Codec<A> dispatchCodec() { return dispatchCodec; }

        /** Unmodifiable view of every registered (id to action type) entry. */
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
