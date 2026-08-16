package com.soul.smithery.worldgen;

import com.mojang.datafixers.util.Pair;
import com.soul.smithery.Config;
import com.soul.smithery.Smithery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Adds the Smithery forge to village house pools at server start.
 *
 * <p>Injects into the existing pools rather than shipping a datapack that redefines them.
 * A redefinition would silently replace whatever every other mod put there — in a large
 * pack that means losing entire village overhauls — whereas appending leaves them intact.
 *
 * <p>Covers the overhauls' own pools as well as vanilla's. A pack carrying one of them
 * generates few vanilla villages, if any: the overhaul registers separate structures that
 * draw from separate pools, so injecting only into vanilla's leaves nearly every village in
 * the pack unable to hold a forge however high the weight is set.
 *
 * <p>The pool's element lists are private with no API to extend them, so this reaches them
 * reflectively. Every failure path is a logged warning rather than a throw: a village
 * without a forge in it is a far better outcome than a server that will not boot.
 *
 * <p>Appending alone would put a forge on any lot that rolls it, several times per village.
 * {@code JigsawPlacerMixin} holds it to one, using {@link #isForge} and {@link #capReached}
 * against the element this class injects.
 */
public final class VillageForgeInjector {

    private static final String PIECE = Smithery.MODID + ":village/forge";

    /**
     * The element appended to the house pools, or null while no forge is injected.
     *
     * <p>One instance is shared by every copy in every pool, so identity is enough to
     * recognise our piece among a village's candidates. Written once per server start
     * before any chunk generates and read from the worldgen threads, hence volatile.
     */
    private static volatile @Nullable StructurePoolElement injected;

    /** {@link Config#VILLAGE_FORGE_CHANCE}, snapshot alongside {@link #injected}. */
    private static volatile int chance;

    /** {@link Config#VILLAGE_FORGE_MIN_PIECES}, snapshot alongside {@link #injected}. */
    private static volatile int minPieces;

    /** The vanilla house pools, one per village biome. */
    private static final List<String> HOUSE_POOLS = List.of(
            "minecraft:village/plains/houses",
            "minecraft:village/desert/houses",
            "minecraft:village/savanna/houses",
            "minecraft:village/snowy/houses",
            "minecraft:village/taiga/houses");

    /**
     * Matches the house pool of a village, vanilla or modded.
     *
     * <p>Anchored on the whole path ending in {@code /house} or {@code /houses} so that only
     * the pool a village draws its ordinary buildings from is caught. The overhauls carry
     * plenty of neighbours that must not receive a 21x23 building: street and town-centre
     * pools, decorations, terminators such as {@code jungle_tree/house_terminator}, and
     * slot-specific lists like {@code mediterranean/houses/corner} whose entries are chosen
     * to fit one particular gap in a layout.
     */
    private static final Pattern HOUSE_POOL = Pattern.compile(".*village.*/houses?$");

    private VillageForgeInjector() {}

    /**
     * Appends the forge to every village house pool, weighted by config.
     */
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        injected = null;                            // a previous world's element, if any
        if (!Config.VILLAGE_FORGE.get()) return;
        int weight = Config.VILLAGE_FORGE_WEIGHT.get();
        if (weight <= 0) return;

        var access = event.getServer().registryAccess();
        Registry<StructureTemplatePool> pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = access.registryOrThrow(Registries.PROCESSOR_LIST);

        Holder<StructureProcessorList> empty = processors.getHolderOrThrow(
                ResourceKey.create(Registries.PROCESSOR_LIST, new ResourceLocation("minecraft:empty")));
        StructurePoolElement piece = StructurePoolElement
                .legacy(PIECE, empty)
                .apply(StructureTemplatePool.Projection.RIGID);

        if (TEMPLATES == null) {
            Smithery.LOGGER.error(
                    "Village forge disabled: no List<StructurePoolElement> field on "
                    + "StructureTemplatePool. Fields seen: {}",
                    java.util.Arrays.stream(StructureTemplatePool.class.getDeclaredFields())
                            .map(f -> f.getType().getSimpleName() + " " + f.getName())
                            .toList());
            return;
        }

        Set<ResourceLocation> targets = new LinkedHashSet<>();
        for (String id : HOUSE_POOLS) {
            targets.add(new ResourceLocation(id));
        }
        int named = targets.size();
        if (Config.VILLAGE_FORGE_MODDED.get()) {
            for (ResourceLocation id : pools.keySet()) {
                if (HOUSE_POOL.matcher(id.getPath()).matches()) targets.add(id);
            }
        }

        int added = 0;
        int missing = 0;
        for (ResourceLocation id : targets) {
            StructureTemplatePool pool = pools.get(id);
            if (pool == null) {                         // pool removed by a datapack
                missing++;
                continue;
            }
            if (append(pool, piece, weight)) {
                added++;
                Smithery.LOGGER.debug("Village forge added to pool {}", id);
            }
        }
        if (added > 0) {
            chance = Config.VILLAGE_FORGE_CHANCE.get();
            minPieces = Config.VILLAGE_FORGE_MIN_PIECES.get();
            injected = piece;
        }
        Smithery.LOGGER.info(
                "Village forge added to {} of {} house pools ({} named, {} discovered,"
                + " {} absent), weight {}, chance {}%, min pieces {}."
                + " Run with debug logging to list them.",
                added, targets.size(), named, targets.size() - named, missing, weight,
                Config.VILLAGE_FORGE_CHANCE.get(), Config.VILLAGE_FORGE_MIN_PIECES.get());
    }

    /**
     * Screens the forge out of one lot's candidates when that lot may not have it.
     *
     * <p>Called by {@code JigsawPlacerMixin} for every jigsaw connector in the game, so the
     * cheap answers come first: the list is returned untouched unless it is one we injected
     * into and the forge is actually barred. Dropping a candidate is free -- the placer
     * treats it as one that did not fit and moves to the next, so the lot takes an ordinary
     * house rather than the gap a later veto would leave.
     *
     * @param candidates the lot's shuffled pool entries
     * @param pieces     the placer's own piece list for the structure being laid out
     */
    public static List<StructurePoolElement> screen(List<StructurePoolElement> candidates,
                                                    List<?> pieces) {
        StructurePoolElement forge = injected;
        if (forge == null || !candidates.contains(forge)) return candidates;
        if (allows(forge, pieces)) return candidates;
        return candidates.stream().filter(candidate -> candidate != forge).toList();
    }

    /**
     * Whether the village described by {@code pieces} may take a forge on its next lot.
     *
     * <p>Three gates: one forge per village, a minimum village size, and a per-village roll.
     * The list is the placer's {@code List<? super PoolElementStructurePiece>} and grows as
     * the village is laid out, so the cap reads true from the moment a forge is accepted and
     * every later lot skips it.
     */
    private static boolean allows(StructurePoolElement forge, List<?> pieces) {
        if (pieces.size() < minPieces) return false;

        BlockPos origin = null;
        for (Object piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece placed)) continue;
            if (placed.getElement() == forge) return false;
            if (origin == null) origin = placed.getPosition();
        }
        return origin != null && rolls(origin);
    }

    /**
     * The per-village rarity roll, taken from the village's own origin.
     *
     * <p>Deliberately not drawn from the placer's random: consuming from that would shift
     * every draw after it, so installing the mod would rearrange vanilla villages. Hashing
     * the town centre's position instead gives an answer that is fixed per village and
     * costs the generator nothing.
     */
    private static boolean rolls(BlockPos origin) {
        if (chance >= 100) return true;
        if (chance <= 0) return false;
        return RandomSource.create(Mth.getSeed(origin)).nextInt(100) < chance;
    }

    /**
     * The flattened list the generator samples from, one entry per point of weight.
     *
     * <p>Found by its generic type rather than its name. Field names differ between the
     * development environment and a shipped jar -- a released build runs against SRG
     * names, so looking up "templates" works while testing and fails everywhere else,
     * which is exactly how this first shipped broken.
     */
    private static final Field TEMPLATES = findList(t -> t == StructurePoolElement.class);

    /** The weighted form, {@code List<Pair<StructurePoolElement, Integer>>}. */
    private static final Field RAW_TEMPLATES = findList(
            t -> t instanceof ParameterizedType p && p.getRawType() == Pair.class);

    private static @Nullable Field findList(Predicate<Type> elementMatches) {
        for (Field f : StructureTemplatePool.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            if (!(f.getGenericType() instanceof ParameterizedType pt)) continue;
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1 && elementMatches.test(args[0])) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /**
     * Appends {@code piece} to a pool.
     *
     * <p>Only the flattened list has to take: it is what generation reads. The weighted
     * list is kept in step where possible, but a failure there is not worth losing the
     * building over, so it is best-effort.
     */
    @SuppressWarnings("unchecked")
    private static boolean append(StructureTemplatePool pool, StructurePoolElement piece, int weight) {
        if (TEMPLATES == null) return false;
        try {
            List<StructurePoolElement> templates = (List<StructurePoolElement>) TEMPLATES.get(pool);
            for (int i = 0; i < weight; i++) {
                templates.add(piece);
            }
        } catch (ReflectiveOperationException | UnsupportedOperationException e) {
            Smithery.LOGGER.warn("Could not add the village forge to a house pool: {}", e.toString());
            return false;
        }

        if (RAW_TEMPLATES != null) {
            try {
                List<Pair<StructurePoolElement, Integer>> raw =
                        (List<Pair<StructurePoolElement, Integer>>) RAW_TEMPLATES.get(pool);
                List<Pair<StructurePoolElement, Integer>> copy = new ArrayList<>(raw);
                copy.add(Pair.of(piece, weight));
                RAW_TEMPLATES.set(pool, copy);
            } catch (ReflectiveOperationException | UnsupportedOperationException e) {
                Smithery.LOGGER.debug("Village forge: weighted list not updated ({})", e.toString());
            }
        }
        return true;
    }
}
