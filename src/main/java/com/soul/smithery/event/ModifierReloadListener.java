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
 * Server-side reload listener that loads modifier definitions from
 * {@code data/<namespace>/smithery/modifier/<id>.json}.
 *
 * <p>Each file declares passive + on-attack + on-break + on-block-drops + on-kill + on-mob-drops
 * + on-compose action lists composed by id; runtime callbacks iterate the lists in declaration
 * order. The file's resource id becomes the modifier id, so naming controls the registered id.
 *
 * <p>Re-runs on every {@code /reload}: previously JSON-loaded modifier ids are removed from
 * {@link SmitheryAPI#MODIFIERS} before re-registration so removed files do not linger. Code-defined
 * modifiers are untouched unless a JSON file shadows them by id. Files that reference unknown
 * action types fail to parse with a logged warning and are skipped.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class ModifierReloadListener
        extends SimpleJsonResourceReloadListener<ModifierReloadListener.ModifierJson> {

    /**
     * Parsed representation of one modifier JSON file.
     *
     * @param durabilityMultiplier multiplier applied to the tool's durability when the modifier is present
     * @param maxLevel             maximum stackable level (1 = one-shot)
     * @param levelCost            base unit cost to reach level 1
     * @param levelCostScaling     multiplier applied per additional level
     * @param passives             passive actions applied at compose time
     * @param onAttack             actions invoked when the tool damages an entity
     * @param onBreak              actions invoked when the tool breaks a block
     * @param onBlockDrops         actions invoked on the post-break drops list
     * @param onKill               actions invoked when the tool kills an entity
     * @param onMobDrops           actions invoked on the post-kill drops list
     * @param onCompose            actions invoked at compose time after passives
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
            List<ModifierAction.OnCompose>   onCompose,
            String appliesTo
    ) {
        /** Codec that pulls each action list through the matching action registry's dispatch codec. */
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
                        .optionalFieldOf("on_compose", List.of()).forGetter(ModifierJson::onCompose),
                Codec.STRING.optionalFieldOf("applies_to", "both")
                        .forGetter(ModifierJson::appliesTo)
        ).apply(i, ModifierJson::new));

        /**
         * Builds a runtime {@link Modifier} from this parsed JSON, wiring each non-empty action
         * list as a {@link Modifier.Builder} callback. Action errors are caught per-action and
         * logged so one bad action does not block its siblings.
         *
         * @param id the identifier to register the resulting modifier under
         * @return the assembled modifier
         */
        public Modifier toModifier(Identifier id) {
            Modifier.Builder b = Modifier.builder(id)
                    .durabilityMultiplier(durabilityMultiplier)
                    .maxLevel(maxLevel)
                    .levelCost(levelCost)
                    .levelCostScaling(levelCostScaling)
                    .appliesTo(switch (appliesTo) {
                        case "tools" -> Modifier.AppliesTo.TOOLS;
                        case "armor" -> Modifier.AppliesTo.ARMOR;
                        default      -> Modifier.AppliesTo.BOTH;
                    });
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
            boolean anyActive = !onAttack.isEmpty() || !onBreak.isEmpty()
                    || !onBlockDrops.isEmpty() || !onKill.isEmpty() || !onMobDrops.isEmpty();
            boolean anyPassive = !passives.isEmpty();
            b.category(anyActive && anyPassive ? Modifier.ModifierCategory.BOTH
                    : anyActive ? Modifier.ModifierCategory.ACTIVE
                    : Modifier.ModifierCategory.PASSIVE);
            return b.build();
        }
    }

    private static final Set<Identifier> DATA_LOADED_IDS = new HashSet<>();

    private ModifierReloadListener() {
        super(ModifierJson.CODEC, FileToIdConverter.json("smithery/modifier"));
    }

    @Override
    protected void apply(Map<Identifier, ModifierJson> entries,
                          ResourceManager manager, ProfilerFiller profiler) {
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

    /**
     * Registers this listener with the server reload pipeline under the {@code smithery:modifiers}
     * id.
     *
     * @param event the NeoForge add-reload-listeners event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "modifiers"),
                new ModifierReloadListener());
    }
}
