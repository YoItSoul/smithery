package com.soul.smithery.api.material;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the current animated tint for materials declaring a {@code colorCycle}.
 *
 * <p>Deliberately free of any Minecraft client imports so common code (e.g. the armor item's
 * {@code DyeableLeatherItem#getColor}) can call it safely on either dist. The tick counter is
 * advanced once per client tick by {@code com.soul.smithery.client.MaterialEffectsTicker}; on a
 * dedicated server it simply never advances and every lookup degrades to the static part color.
 *
 * <p>Performance: the animated color for a given {@link MaterialStats} is computed at most once
 * per tick and memoized ({@code ~O(1)} map hit for every subsequent item rendered that tick),
 * so thousands of on-screen parts sharing a material cost one interpolation between them.
 * Materials without a cycle short-circuit to {@link MaterialStats#partColor()} without touching
 * the cache.
 */
public final class MaterialColorAnimator {

    private MaterialColorAnimator() {}

    /** Client tick counter; advanced by the client-side ticker, frozen on servers. */
    private static volatile long ticks = 0L;

    /** Per-tick memo of computed cycle colors, invalidated when the tick advances. */
    private static final Map<MaterialStats, Integer> CACHE = new ConcurrentHashMap<>();
    private static volatile long cacheTick = Long.MIN_VALUE;

    /** Advances the animation clock by one tick. Called once per client tick. */
    public static void advanceTick() {
        ticks++;
    }

    /**
     * Returns the ARGB tint to render {@code stats} with right now: the interpolated cycle
     * color for animated materials, or the static {@link MaterialStats#partColor()} otherwise.
     * Alpha is forced opaque.
     */
    public static int currentColor(MaterialStats stats) {
        if (stats == null) return 0xFFFFFFFF;
        if (!stats.hasColorCycle()) return stats.partColor() | 0xFF000000;
        long now = ticks;
        if (cacheTick != now) {
            CACHE.clear();
            cacheTick = now;
        }
        Integer cached = CACHE.get(stats);
        if (cached != null) return cached;
        int color = computeCycleColor(stats, now);
        CACHE.put(stats, color);
        return color;
    }

    /** Smoothly interpolates between the cycle keyframes at the given tick. */
    private static int computeCycleColor(MaterialStats stats, long tick) {
        int[] cycle = stats.colorCycleRaw();
        int period = stats.colorCyclePeriodTicks();
        int n = cycle.length;
        float pos = ((tick % period) / (float) period) * n;
        int idx = (int) pos;
        if (idx >= n) idx = n - 1; // float edge safety
        float frac = pos - idx;
        int from = cycle[idx];
        int to = cycle[(idx + 1) % n];
        return lerpArgb(from, to, frac) | 0xFF000000;
    }

    /** Per-channel linear interpolation between two ARGB colors. */
    private static int lerpArgb(int from, int to, float t) {
        int r = lerpChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerpChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int a, int b, float t) {
        return a + Math.round((b - a) * t);
    }
}
