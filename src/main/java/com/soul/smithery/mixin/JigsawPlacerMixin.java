package com.soul.smithery.mixin;

import com.soul.smithery.worldgen.VillageForgeInjector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Caps the village forge at one per village.
 *
 * <p>A jigsaw pool holds no notion of "at most one of these". The placer walks every
 * jigsaw connector on every street piece, shuffles the whole house pool for each one and
 * takes the first candidate that fits, so a village makes far more house draws than it has
 * buildings and a building with any real weight lands several times over. Lowering the
 * weight only moves the average; it cannot bound the count.
 *
 * <p>The one place that knows what a village holds so far is the placer's own piece list,
 * which is private and passed to nothing. Hence a mixin: it hands that list to
 * {@link VillageForgeInjector#screen}, which drops the forge from the candidates on any lot
 * that may not have it. Vetoing here costs nothing visible -- the placer simply moves to the
 * next candidate, so the lot gets an ordinary house rather than the gap that a veto later in
 * generation would leave.
 *
 * <p>The cap is exact rather than probabilistic: a candidate list is scanned until one
 * element is accepted, and the accepted piece is appended to the list this reads before the
 * next connector fetches its own candidates.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerMixin {

    @Shadow @Final private List<? super PoolElementStructurePiece> pieces;

    /**
     * Filters the forge out of a lot's candidates once the village already has one.
     *
     * <p>Redirects both call sites in the method, the main pool and the fallback pool.
     * Everything else passes the original list straight through, so villages without a
     * forge in them and every other jigsaw structure in the game are untouched.
     */
    @Redirect(
            method = "tryPlacingChildren",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;"
                             + "getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<StructurePoolElement> smithery$capVillageForge(StructureTemplatePool pool, RandomSource random) {
        return VillageForgeInjector.screen(pool.getShuffledTemplates(random), this.pieces);
    }
}
