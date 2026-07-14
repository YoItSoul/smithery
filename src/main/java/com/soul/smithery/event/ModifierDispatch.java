package com.soul.smithery.event;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.item.tool.ToolComposition;
import com.soul.smithery.item.tool.ToolStats;
import com.soul.smithery.item.tool.SmitheryToolData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared effect resolution for the tool and armor modifier event routers.
 *
 * <p>Stats are recomputed per event via {@link ToolStats#compute}; the path is allocation-light
 * so caching is unnecessary at typical event frequencies. High-frequency callers (armor tick)
 * should pre-check {@link #hasComposition} before resolving.
 */
final class ModifierDispatch {
    private ModifierDispatch() {}

    /** A modifier resolved against the effect instance that granted it. */
    record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

    /** Cheap component-presence check for early-outs before {@link #effectsFor}. */
    static boolean hasComposition(ItemStack stack) {
        return SmitheryToolData.hasComposition(stack);
    }

    /**
     * Resolves the stack's active modifier effects (material grants + applied modifiers +
     * synergies) and keeps those whose {@link Modifier} passes {@code hookFilter}.
     * Returns an empty list for stacks without a valid composition.
     */
    static List<ResolvedEffect> effectsFor(ItemStack stack, Predicate<Modifier> hookFilter) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        if (comp == null || !comp.isValid()) return List.of();
        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        ToolStats stats = ToolStats.compute(comp, applied);

        List<ResolvedEffect> out = new ArrayList<>(stats.activeEffects.size());
        for (ToolStats.ResolvedEffect r : stats.activeEffects) {
            Modifier mod = SmitheryAPI.MODIFIERS.get(r.effect().modifierId());
            if (mod == null || !hookFilter.test(mod)) continue;
            out.add(new ResolvedEffect(mod, r.effect()));
        }
        return out;
    }
}
