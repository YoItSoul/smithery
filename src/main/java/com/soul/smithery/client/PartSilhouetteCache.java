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
 * Builds and caches per-PartType alpha grids used by the part-press renderer and JEI book pages.
 *
 * <p>Each entry is a {@code float[GRID][GRID]} of the source texture's alpha at that cell,
 * area-averaged into [0,1]. Fully opaque pixels mean "no tooth", fully transparent mean
 * "full tooth", in-between values yield ramped sub-pixel teeth. Entries are cached forever;
 * a resource reload should call {@link #invalidate()}.
 */
public final class PartSilhouetteCache {
    /** Grid resolution per axis. */
    public static final int GRID = 12;

    private static final Map<Identifier, float[][]> CACHE = new HashMap<>();
    private static final float[][] EMPTY = new float[GRID][GRID];

    private PartSilhouetteCache() {}

    /**
     * Returns the cached alpha grid for the given part type, building it on first access.
     *
     * @param pt part type to sample; {@code null} or one with no template texture returns an all-zero grid
     * @return {@code grid[x][z]} normalized alpha in [0,1]; 0 = fully transparent, 1 = fully opaque
     */
    public static float[][] forPartType(PartType pt) {
        if (pt == null) return EMPTY;
        Identifier tmpl = pt.textureTemplate();
        if (tmpl == null) return EMPTY;
        return CACHE.computeIfAbsent(pt.id(), id -> readAlphaGrid(tmpl));
    }

    /**
     * Convenience wrapper that resolves the {@link PartType} from its id before delegating
     * to {@link #forPartType(PartType)}.
     *
     * @param id part type identifier
     * @return alpha grid for the resolved part type, or an all-zero grid if not registered
     */
    public static float[][] forPartTypeId(Identifier id) {
        PartType pt = SmitheryAPI.PART_TYPES.get(id);
        return forPartType(pt);
    }

    /**
     * Drops every cached grid. Call after a resource pack reload so subsequent lookups
     * re-read updated PNGs.
     */
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
            BufferedImage rgba = new BufferedImage(raw.getWidth(), raw.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rgba.createGraphics();
            g.drawImage(raw, 0, 0, null);
            g.dispose();

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
