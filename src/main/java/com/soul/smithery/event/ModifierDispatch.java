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
 * <p>Stats are recomputed per event via {@link ToolStats#compute}. High-frequency callers — the
 * armor and tool tick routers, which reach here for six equipment slots per player per tick —
 * should pre-check {@link #hasComposition} before resolving; that check is tag-presence only and
 * does not decode, while {@link #effectsFor} decodes through the memo in
 * {@code SmitheryToolData.getComposition}.
 */
final class ModifierDispatch {
    private ModifierDispatch() {}

    /** A modifier resolved against the effect instance that granted it. */
    record ResolvedEffect(Modifier modifier, ModifierEffect effect) {}

    /**
     * Cheap component-presence check for early-outs before {@link #effectsFor}.
     *
     * <p>Tests only that the tag is there. Decoding it to answer a yes/no question threw the
     * result away, and the caller decodes it again immediately afterwards anyway.
     */
    static boolean hasComposition(ItemStack stack) {
        return SmitheryToolData.hasCompositionTag(stack);
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
