package com.soul.smithery.client;

import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and caches per-PartType <em>alpha grids</em> for the Part Press renderer.
 *
 * <p>Each entry is a {@code float[GRID][GRID]} where the value is the source texture's
 * alpha at that cell, normalized to {@code [0, 1]}. The press uses this to scale tooth
 * heights — fully opaque pixels (alpha=1) mean "no tooth here" and fully transparent
 * pixels (alpha=0) mean "full tooth". Partially transparent pixels yield proportional
 * tooth heights, so feathered part textures naturally produce ramped teeth.
 *
 * <p>Cache is forever-lived; texture reloads should call {@link #invalidate()}.
 */
public final class PartSilhouetteCache {
    public static final int GRID = 12;

    private static final Map<Identifier, float[][]> CACHE = new HashMap<>();
    private static final float[][] EMPTY = new float[GRID][GRID];

    private PartSilhouetteCache() {}

    /**
     * Per-cell alpha values, normalized to {@code [0, 1]}. {@code alpha[x][z]}; {@code 0} = fully
     * transparent (full tooth), {@code 1} = fully opaque (no tooth).
     */
    public static float[][] forPartType(PartType pt) {
        if (pt == null) return EMPTY;
        Identifier tmpl = pt.textureTemplate();
        if (tmpl == null) return EMPTY;
        return CACHE.computeIfAbsent(pt.id(), id -> readAlphaGrid(tmpl));
    }

    public static float[][] forPartTypeId(Identifier id) {
        PartType pt = SmitheryAPI.PART_TYPES.get(id);
        return forPartType(pt);
    }

    public static void invalidate() {
        CACHE.clear();
    }

    private static float[][] readAlphaGrid(Identifier tmpl) {
        Identifier resourceLoc = Identifier.fromNamespaceAndPath(
                tmpl.getNamespace(), "textures/" + tmpl.getPath() + ".png");
        try {
            Optional<Resource> resource =
                    Minecraft.getInstance().getResourceManager().getResource(resourceLoc);
            if (resource.isEmpty()) return EMPTY;
            BufferedImage raw;
            try (InputStream in = resource.get().open()) {
                raw = ImageIO.read(in);
            }
            if (raw == null) return EMPTY;
            // Normalize to ARGB so indexed/grayscale PNGs yield correct alpha readback.
            BufferedImage rgba = new BufferedImage(raw.getWidth(), raw.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rgba.createGraphics();
            g.drawImage(raw, 0, 0, null);
            g.dispose();

            // Area-average sampling: each cell covers a w/GRID × h/GRID slice of the source
            // texture; we average alpha over every source pixel that overlaps the cell. Nearest-
            // neighbor sampling (the obvious approach) skips source pixels entirely when the
            // source resolution doesn't evenly divide GRID — e.g. 16→12 drops src pixels 3, 7,
            // 11, 15 along each axis, which the user noticed as "pixels not accounted for".
            // Averaging keeps every source pixel's contribution proportional.
            int w = rgba.getWidth();
            int h = rgba.getHeight();
            float[][] grid = new float[GRID][GRID];
            for (int z = 0; z < GRID; z++) {
                int szStart = (int) Math.floor((double) z       * h / GRID);
                int szEnd   = Math.max(szStart + 1,
                              (int) Math.ceil ((double) (z + 1) * h / GRID));
                for (int x = 0; x < GRID; x++) {
                    int sxStart = (int) Math.floor((double) x       * w / GRID);
                    int sxEnd   = Math.max(sxStart + 1,
                                  (int) Math.ceil ((double) (x + 1) * w / GRID));
                    int sum = 0, count = 0;
                    for (int sz = szStart; sz < szEnd && sz < h; sz++) {
                        for (int sx = sxStart; sx < sxEnd && sx < w; sx++) {
                            sum += (rgba.getRGB(sx, sz) >>> 24) & 0xFF;
                            count++;
                        }
                    }
                    grid[x][z] = count > 0 ? (sum / (255f * count)) : 0f;
                }
            }
            return grid;
        } catch (Exception e) {
            return EMPTY;
        }
    }
}
