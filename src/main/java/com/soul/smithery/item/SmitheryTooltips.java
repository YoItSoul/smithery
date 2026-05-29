package com.soul.smithery.item;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.soul.smithery.Smithery;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Consumer;

/**
 * Cross-item helpers for Smithery's three-tier hover tooltips and the shared visual styling
 * (section headers, bullets, key-hint lines).
 *
 * <p>The tier model is uniform across parts, tools, and post-craft items:
 * <ul>
 *   <li>{@link Tier#BASIC} — always visible; the elevator pitch for the item and the key-hint footer.</li>
 *   <li>{@link Tier#DETAIL} — held Shift; per-part stats, modifier names, synergy names.</li>
 *   <li>{@link Tier#FULL} — held Shift + Ctrl; modifier descriptions, raw parameter values,
 *       multi-level cost ramps, and any other dense numeric detail.</li>
 * </ul>
 *
 * <p>Centralising these helpers lets every {@code appendHoverText} share the same look and the
 * same key bindings — tooltips don't drift between item classes as new fields are added.
 */
public final class SmitheryTooltips {
    private SmitheryTooltips() {}

    /** Hover-tooltip detail level driven by Shift / Ctrl key state at draw time. */
    public enum Tier {
        /** Always shown. The elevator pitch plus the key-hint footer. */
        BASIC,
        /** Shift held. Adds per-part stats, modifier names, synergy names. */
        DETAIL,
        /** Shift + Ctrl held. Adds descriptions and raw parameter values for every modifier. */
        FULL
    }

    /**
     * Reads live keyboard state and decides which {@link Tier} should render.
     *
     * <p>Always called from {@code appendHoverText}, which runs on the client render thread —
     * so {@link Minecraft#getInstance()} and {@code getWindow()} are safe here. Off-thread
     * tooltip calls (e.g. JEI ingredient listings) also hit this method; the key state simply
     * returns false there and the BASIC tier is used.
     *
     * @return active tier — {@link Tier#FULL} if both Shift and Ctrl are held, {@link Tier#DETAIL}
     *         if only Shift is held, otherwise {@link Tier#BASIC}
     */
    public static Tier currentTier() {
        Window win;
        try {
            win = Minecraft.getInstance().getWindow();
        } catch (Throwable ignored) {
            return Tier.BASIC;
        }
        boolean shift = InputConstants.isKeyDown(win, InputConstants.KEY_LSHIFT)
                     || InputConstants.isKeyDown(win, InputConstants.KEY_RSHIFT);
        boolean ctrl  = InputConstants.isKeyDown(win, InputConstants.KEY_LCONTROL)
                     || InputConstants.isKeyDown(win, InputConstants.KEY_RCONTROL);
        if (shift && ctrl) return Tier.FULL;
        if (shift)         return Tier.DETAIL;
        return Tier.BASIC;
    }

    /**
     * Appends the standard footer that explains which keys reveal which tier.
     *
     * <p>The footer is tier-aware: BASIC tier shows both Shift and Shift+Ctrl hints; DETAIL tier
     * shows only the Shift+Ctrl hint (Shift is already active); FULL tier shows nothing — the
     * player is already at the deepest tier.
     */
    public static void appendKeyHint(Consumer<Component> tooltip, Tier tier) {
        switch (tier) {
            case BASIC -> {
                tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".hint.shift_for_details")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".hint.shift_ctrl_for_full")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            case DETAIL -> {
                tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".hint.shift_ctrl_for_full")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            case FULL -> { /* deepest tier, no hint */ }
        }
    }

    /**
     * Section header styled with a gold colon — e.g. {@code "Stats:"}.
     *
     * <p>Sections inside a Smithery tooltip should all use this exact look so the eye can land
     * on "the next section" reliably.
     */
    public static MutableComponent sectionHeader(Component label) {
        return label.copy().withStyle(ChatFormatting.GOLD);
    }

    /**
     * Stat row: indented dark-gray triangle bullet followed by {@code body}. Used for numeric
     * stat lines like {@code Durability: 312}.
     */
    public static MutableComponent statLine(Component body) {
        return Component.literal(" ▸ ").append(body).withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * List row: indented dark-gray bullet followed by {@code body}. Used for parts list, modifier
     * names, anything that's a discrete enumeration.
     */
    public static MutableComponent bullet(Component body) {
        return Component.literal(" • ").append(body).withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Indented body row that hangs under a bullet — used for modifier descriptions and parameter
     * lines in FULL tier so they visually belong to the bullet above.
     */
    public static MutableComponent subLine(Component body) {
        return Component.literal("     ").append(body).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Italic light-gray description line — used for modifier {@code .description} keys in FULL tier. */
    public static MutableComponent description(Component body) {
        return body.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    /** Synergy bullet: a sparkle prefix in light purple. */
    public static MutableComponent synergyBullet(Component body) {
        return Component.literal(" ✦ ").append(body).withStyle(ChatFormatting.DARK_PURPLE);
    }
}
