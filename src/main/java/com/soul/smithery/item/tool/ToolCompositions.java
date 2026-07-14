package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.modifier.Modifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

/**
 * Single entry point for stamping a {@link ToolComposition} onto a stack. Dispatches to
 * {@link SmitheryArmorItem#applyComposition} or {@link SmitheryToolItem#applyComposition}
 * by item class so callers (assembly recipe, anvil, JEI previews, creative tabs) never
 * need to know which family the stack belongs to.
 */
public final class ToolCompositions {
    private ToolCompositions() {}

    /** True if {@code stack}'s item is composition-driven (smithery tool or armor). */
    public static boolean isComposable(ItemStack stack) {
        return stack.getItem() instanceof SmitheryToolItem
                || stack.getItem() instanceof SmitheryArmorItem;
    }

    /**
     * Convenience overload that resolves the current server's HolderLookup.Provider
     * implicitly. Compose actions needing registry access (such as enchantment writes)
     * silently no-op when no server is running.
     */
    public static ItemStack apply(ItemStack stack, ToolComposition comp) {
        HolderLookup.Provider lookup = null;
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) lookup = server.registryAccess();
        } catch (Throwable ignored) { }
        return apply(stack, comp, lookup);
    }

    /**
     * Stamps {@code comp} onto {@code stack} via the family-appropriate implementation.
     *
     * @param lookup registry access for compose actions; pass null only when no live
     *               registry is available, in which case affected actions skip silently
     */
    public static ItemStack apply(ItemStack stack, ToolComposition comp,
                                  HolderLookup.@Nullable Provider lookup) {
        return stack.getItem() instanceof SmitheryArmorItem
                ? SmitheryArmorItem.applyComposition(stack, comp, lookup)
                : SmitheryToolItem.applyComposition(stack, comp, lookup);
    }

    /**
     * Fires every {@link Modifier.OnCompose} hook on the stats' compose-effect list.
     * Shared tail of both applyComposition implementations; a hook that throws is logged
     * and skipped so one broken modifier can't void the whole stack.
     */
    static void fireComposeHooks(ItemStack stack, ToolStats stats,
                                 HolderLookup.@Nullable Provider lookup) {
        Modifier.ComposeContext composeCtx = new Modifier.ComposeContext(stack, lookup);
        for (ToolStats.ResolvedEffect r : stats.composeEffects) {
            Modifier mod = SmitheryAPI.MODIFIERS.get(r.effect().modifierId());
            if (mod == null || mod.onCompose() == null) continue;
            try {
                mod.onCompose().apply(r.effect(), composeCtx);
            } catch (Throwable t) {
                Smithery.LOGGER.error("Modifier {} onCompose failed: {}",
                        r.effect().modifierId(), t.toString());
            }
        }
    }
}
