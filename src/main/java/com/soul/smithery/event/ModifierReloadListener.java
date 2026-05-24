package com.soul.smithery.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierAction;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads modifier definitions from {@code data/<namespace>/smithery/modifier/<modifier_id>.json}.
 * Each file defines one modifier composed of passive + on_attack + on_break action lists.
 *
 * <h2>JSON shape</h2>
 * <pre>{@code
 *   // data/yourmod/smithery/modifier/your_modifier.json — registers smithery:your_modifier? NO —
 *   // the file's resource id (namespace from the data pack root + path under modifier/) is what
 *   // the modifier is registered as. So data/yourmod/smithery/modifier/sparky.json registers
 *   // the modifier "yourmod:sparky".
 *   {
 *     "durability_multiplier": 1.0,
 *     "passives": [
 *       { "type": "smithery:bonus_damage", "amount": 2.0 }
 *     ],
 *     "on_attack": [
 *       { "type": "smithery:apply_mob_effect",
 *         "effect": "minecraft:poison",
 *         "chance": 0.25,
 *         "duration_ticks": 60,
 *         "amplifier": 0 }
 *     ],
 *     "on_break": [
 *       { "type": "smithery:pull_drops", "radius": 5.0 }
 *     ]
 *   }
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * The listener runs at server start (initial datapack load) and on every {@code /reload}.
 * Each pass clears the set of previously JSON-loaded modifier ids from
 * {@link SmitheryAPI#MODIFIERS} and re-registers from JSON. Code-defined modifiers (registered
 * via {@code SmitheryAPI.registerModifier} in mod init) are <em>not</em> touched unless a
 * JSON file uses the same id — in which case the JSON definition shadows the code one until
 * the JSON file is removed from all packs and a {@code /reload} runs (after which the code
 * version is gone too, until the server restarts and the code init re-registers it).
 *
 * <p>Recommendation: modders use namespaced ids that don't collide with smithery built-ins
 * (e.g. {@code yourmod:custom_modifier}, not {@code smithery:sharp}). Datapack authors
 * deliberately overriding a built-in can do so, but should expect to ship a full server-restart
 * to revert.
 *
 * <h2>Action type errors</h2>
 * If a JSON file references an action type id that isn't registered (typo, missing mod), the
 * file fails to parse and is skipped with a logged warning. The modifier is not registered.
 * Other modifier files continue to load.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class ModifierReloadListener
        extends SimpleJsonResourceReloadListener<ModifierReloadListener.ModifierJson> {

    /**
     * One JSON file → one record. The category-list codecs use each action registry's
     * dispatch codec, so the {@code "type"} field inside each action dispatches to the
     * registered action's params.
     */
    public record ModifierJson(
            float durabilityMultiplier,
            int maxLevel,
            int levelCost,
            float levelCostScaling,
            List<ModifierAction.Passive>     passives,
            List<ModifierAction.OnAttack>    onAttack,
            List<ModifierAction.OnBreak>     onBreak,
            List<ModifierAction.OnBlockDrops> onBlockDrops,
            List<ModifierAction.OnKill>      onKill,
            List<ModifierAction.OnMobDrops>  onMobDrops,
            List<ModifierAction.OnCompose>   onCompose
    ) {
        public static final Codec<ModifierJson> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("durability_multiplier", 1.0f)
                        .forGetter(ModifierJson::durabilityMultiplier),
                Codec.INT.optionalFieldOf("max_level", 1)
                        .forGetter(ModifierJson::maxLevel),
                Codec.INT.optionalFieldOf("level_cost", 1)
                        .forGetter(ModifierJson::levelCost),
                Codec.FLOAT.optionalFieldOf("level_cost_scaling", 1.0f)
                        .forGetter(ModifierJson::levelCostScaling),
                ModifierAction.PASSIVE.dispatchCodec().listOf()
                        .optionalFieldOf("passives", List.of()).forGetter(ModifierJson::passives),
                ModifierAction.ON_ATTACK.dispatchCodec().listOf()
                        .optionalFieldOf("on_attack", List.of()).forGetter(ModifierJson::onAttack),
                ModifierAction.ON_BREAK.dispatchCodec().listOf()
                        .optionalFieldOf("on_break", List.of()).forGetter(ModifierJson::onBreak),
                ModifierAction.ON_BLOCK_DROPS.dispatchCodec().listOf()
                        .optionalFieldOf("on_block_drops", List.of()).forGetter(ModifierJson::onBlockDrops),
                ModifierAction.ON_KILL.dispatchCodec().listOf()
                        .optionalFieldOf("on_kill", List.of()).forGetter(ModifierJson::onKill),
                ModifierAction.ON_MOB_DROPS.dispatchCodec().listOf()
                        .optionalFieldOf("on_mob_drops", List.of()).forGetter(ModifierJson::onMobDrops),
                ModifierAction.ON_COMPOSE.dispatchCodec().listOf()
                        .optionalFieldOf("on_compose", List.of()).forGetter(ModifierJson::onCompose)
        ).apply(i, ModifierJson::new));

        /**
         * Converts the parsed JSON into a runtime {@link Modifier} by wiring each action list
         * into the appropriate {@link Modifier.Builder} callback. Each callback iterates its
         * action list and invokes them in declaration order — actions compose left-to-right.
         */
        public Modifier toModifier(Identifier id) {
            Modifier.Builder b = Modifier.builder(id)
                    .durabilityMultiplier(durabilityMultiplier)
                    .maxLevel(maxLevel)
                    .levelCost(levelCost)
                    .levelCostScaling(levelCostScaling);
            if (!passives.isEmpty()) {
                b.passive((effect, stats) -> {
                    for (ModifierAction.Passive a : passives) a.apply(stats, effect);
                });
            }
            if (!onAttack.isEmpty()) {
                b.onAttackEntity((effect, ctx) -> {
                    for (ModifierAction.OnAttack a : onAttack) {
                        try { a.execute(ctx, effect); }
                        catch (Throwable t) {
                            Smithery.LOGGER.error("Action {} (modifier {}) onAttack failed: {}",
                                    a.type(), id, t.toString());
                        }
                    }
                });
            }
            if (!onBreak.isEmpty()) {
                b.onBlockBreak((effect, ctx) -> {
                    for (ModifierAction.OnBreak a : onBreak) {
                        try { a.execute(ctx, effect); }
                        catch (Throwable t) {
                            Smithery.LOGGER.error("Action {} (modifier {}) onBreak failed: {}",
                                    a.type(), id, t.toString());
                        }
                    }
                });
            }
            if (!onBlockDrops.isEmpty()) {
                b.onBlockDrops((effect, ctx) -> {
                    for (ModifierAction.OnBlockDrops a : onBlockDrops) {
                        try { a.onDrops(ctx, effect); }
                        catch (Throwable t) {
                            Smithery.LOGGER.error("Action {} (modifier {}) onBlockDrops failed: {}",
                                    a.type(), id, t.toString());
                        }
                    }
                });
            }
            if (!onKill.isEmpty()) {
                b.onKill((effect, ctx) -> {
                    for (ModifierAction.OnKill a : onKill) {
                        try { a.onKill(ctx, effect); }
                        catch (Throwable t) {
                            Smithery.LOGGER.error("Action {} (modifier {}) onKill failed: {}",
                                    a.type(), id, t.toString());
                        }
                    }
                });
            }
            if (!onMobDrops.isEmpty()) {
                b.onMobDrops((effect, ctx) -> {
                    for (ModifierAction.OnMobDrops a : onMobDrops) {
                        try { a.onDrops(ctx, effect); }
                        catch (Throwable t) {
                            Smithery.LOGGER.error("Action {} (modifier {}) onMobDrops failed: {}",
                                    a.type(), id, t.toString());
                        }
                    }
                });
            }
            if (!onCompose.isEmpty()) {
                b.onCompose((effect, ctx) -> {
                    for (ModifierAction.OnCompose a : onCompose) {
                        try { a.apply(ctx, effect); }
                        catch (Throwable t) {
                            Smithery.LOGGER.error("Action {} (modifier {}) onCompose failed: {}",
                                    a.type(), id, t.toString());
                        }
                    }
                });
            }
            // Set category based on which lists are non-empty (cosmetic / introspection only).
            boolean anyActive = !onAttack.isEmpty() || !onBreak.isEmpty()
                    || !onBlockDrops.isEmpty() || !onKill.isEmpty() || !onMobDrops.isEmpty();
            boolean anyPassive = !passives.isEmpty();
            b.category(anyActive && anyPassive ? Modifier.ModifierCategory.BOTH
                    : anyActive ? Modifier.ModifierCategory.ACTIVE
                    : Modifier.ModifierCategory.PASSIVE);
            return b.build();
        }
    }

    /** Ids of modifiers loaded from JSON last reload. Cleared and repopulated each pass. */
    private static final Set<Identifier> DATA_LOADED_IDS = new HashSet<>();

    private ModifierReloadListener() {
        super(ModifierJson.CODEC, FileToIdConverter.json("smithery/modifier"));
    }

    @Override
    protected void apply(Map<Identifier, ModifierJson> entries,
                          ResourceManager manager, ProfilerFiller profiler) {
        // Drop previously-JSON-loaded modifiers so removed JSON files don't linger.
        for (Identifier id : DATA_LOADED_IDS) SmitheryAPI.MODIFIERS.remove(id);
        DATA_LOADED_IDS.clear();

        int registered = 0;
        for (Map.Entry<Identifier, ModifierJson> e : entries.entrySet()) {
            Identifier id = e.getKey();
            ModifierJson parsed = e.getValue();
            Modifier mod = parsed.toModifier(id);
            SmitheryAPI.registerModifier(mod);
            DATA_LOADED_IDS.add(id);
            registered++;
        }
        Smithery.LOGGER.info("Loaded {} modifiers from data packs", registered);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "modifiers"),
                new ModifierReloadListener());
    }
}
