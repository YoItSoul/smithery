package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime client-resource pack that emits the per-material model + item-definition JSONs for
 * every registered (Material × PartType) and ToolType combination. Regenerated on every
 * resource reload from the live SmitheryAPI registry state, so adding a material via JSON
 * datapack at runtime produces a renderable item with no static asset files.
 *
 * Texture PNGs are NOT served by this pack — they live in src/main/resources as shared
 * grayscale templates, one per PartType / ToolType. Material color is applied via the
 * registered ItemTintSource at render time.
 */
public class SmitheryGeneratedPack implements PackResources {

    public static final String PACK_ID = "smithery_generated";

    private static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID,
            Component.literal("Smithery Generated"),
            PackSource.BUILT_IN,
            Optional.empty()
    );

    private static final String MODELS_PREFIX       = "models/item/";
    private static final String BLOCK_MODELS_PREFIX = "models/block/";
    private static final String BLOCKSTATES_PREFIX  = "blockstates/";
    private static final String ITEMS_PREFIX        = "items/";
    private static final String JSON_SUFFIX         = ".json";

    private static final String MOLTEN_PREFIX        = "molten_";
    private static final String MOLTEN_BUCKET_SUFFIX = "_bucket";

    /** Path prefix for the per-PartType "sand with cutout" block variants (voxelized model). */
    private static final String IMPRESSED_SAND_PREFIX = "casting_sand_impressed_";

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        // pack.mcmeta is served via getMetadataSection; nothing else lives at root.
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        String path = location.getPath();
        String namespace = location.getNamespace();

        if (!path.endsWith(JSON_SUFFIX)) return null;

        if (path.startsWith(MODELS_PREFIX)) {
            String name = path.substring(MODELS_PREFIX.length(), path.length() - JSON_SUFFIX.length());
            return resolveModelJson(namespace, name);
        }
        if (path.startsWith(BLOCK_MODELS_PREFIX)) {
            String name = path.substring(BLOCK_MODELS_PREFIX.length(), path.length() - JSON_SUFFIX.length());
            return resolveBlockModelJson(namespace, name);
        }
        if (path.startsWith(BLOCKSTATES_PREFIX)) {
            String name = path.substring(BLOCKSTATES_PREFIX.length(), path.length() - JSON_SUFFIX.length());
            return resolveBlockstateJson(namespace, name);
        }
        if (path.startsWith(ITEMS_PREFIX)) {
            String name = path.substring(ITEMS_PREFIX.length(), path.length() - JSON_SUFFIX.length());
            return resolveItemDefJson(namespace, name);
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;

        // CRITICAL: vanilla resource loaders (post_effect chains, equipment assets, etc.) all
        // share this method. We MUST filter by the requested directory or other loaders will
        // try to parse our model JSONs as their own formats.
        String prefix = directory.isEmpty() ? "" : directory.endsWith("/") ? directory : directory + "/";

        // Tools (smithery: namespace only)
        if (Smithery.MODID.equals(namespace)) {
            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                String path = tt.id().getPath();
                emitIfMatches(namespace, MODELS_PREFIX + path + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, path), output);
                emitIfMatches(namespace, ITEMS_PREFIX  + path + JSON_SUFFIX, prefix,
                        () -> resolveItemDefJson(namespace, path), output);
            }
        }

        // Parts: live in their material's namespace
        for (Material m : SmitheryAPI.MATERIALS.all()) {
            if (!m.id().getNamespace().equals(namespace)) continue;
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                String itemName = m.id().getPath() + "_" + pt.id().getPath();
                emitIfMatches(namespace, MODELS_PREFIX + itemName + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, itemName), output);
                emitIfMatches(namespace, ITEMS_PREFIX + itemName + JSON_SUFFIX, prefix,
                        () -> resolveItemDefJson(namespace, itemName), output);
            }
        }

        // Molten fluids + buckets: one set per material with meltingTemp > 0, all in smithery: namespace.
        if (Smithery.MODID.equals(namespace)) {
            for (Material m : SmitheryAPI.MATERIALS.all()) {
                if (m.stats().meltingTemp() <= 0f) continue;
                String matPath  = m.id().getPath();
                String fluidId  = MOLTEN_PREFIX + matPath;
                String bucketId = fluidId + MOLTEN_BUCKET_SUFFIX;
                emitIfMatches(namespace, BLOCKSTATES_PREFIX  + fluidId  + JSON_SUFFIX, prefix,
                        () -> resolveBlockstateJson(namespace, fluidId), output);
                emitIfMatches(namespace, BLOCK_MODELS_PREFIX + fluidId  + JSON_SUFFIX, prefix,
                        () -> resolveBlockModelJson(namespace, fluidId), output);
                emitIfMatches(namespace, ITEMS_PREFIX        + bucketId + JSON_SUFFIX, prefix,
                        () -> resolveItemDefJson(namespace, bucketId), output);
            }
        }

        // Per-PartType "impressed sand" assets: blockstate + block model (voxelized) +
        // item def + item model. Internal Block registrations in SmitheryBlocks rely
        // on these existing so the BER can resolve them as ItemStacks.
        if (Smithery.MODID.equals(namespace)) {
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                String name = IMPRESSED_SAND_PREFIX + pt.id().getPath();
                emitIfMatches(namespace, BLOCKSTATES_PREFIX  + name + JSON_SUFFIX, prefix,
                        () -> resolveBlockstateJson(namespace, name), output);
                emitIfMatches(namespace, BLOCK_MODELS_PREFIX + name + JSON_SUFFIX, prefix,
                        () -> resolveBlockModelJson(namespace, name), output);
                emitIfMatches(namespace, MODELS_PREFIX       + name + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, name), output);
                emitIfMatches(namespace, ITEMS_PREFIX        + name + JSON_SUFFIX, prefix,
                        () -> resolveItemDefJson(namespace, name), output);
            }
        }
    }

    private static void emitIfMatches(String namespace, String fullPath, String requestedPrefix,
                                      java.util.function.Supplier<IoSupplier<InputStream>> contentSupplier,
                                      ResourceOutput output) {
        if (!fullPath.startsWith(requestedPrefix)) return;
        IoSupplier<InputStream> content = contentSupplier.get();
        if (content == null) return;
        output.accept(Identifier.fromNamespaceAndPath(namespace, fullPath), content);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) return Set.of();
        Set<String> ns = new HashSet<>();
        ns.add(Smithery.MODID);
        for (Material m : SmitheryAPI.MATERIALS.all()) ns.add(m.id().getNamespace());
        return ns;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> meta) {
        if (PackMetadataSection.CLIENT_TYPE.equals(meta) || PackMetadataSection.FALLBACK_TYPE.equals(meta)) {
            PackFormat current = PackFormat.of(Integer.MAX_VALUE);
            return (T) new PackMetadataSection(
                    Component.literal("Smithery generated resources"),
                    new InclusiveRange<>(PackFormat.of(0), current)
            );
        }
        return null;
    }

    @Override
    public PackLocationInfo location() { return LOCATION; }

    @Override
    public void close() { /* nothing to release */ }

    // ---- JSON generation ----

    /**
     * Resolves "<material_path>_<part_path>" (parts) or "<tool_path>" (tools) to a model JSON.
     * Returns null if {@code itemName} doesn't match a known registered combination.
     */
    private @Nullable IoSupplier<InputStream> resolveModelJson(String namespace, String itemName) {
        // First check tools (smithery: only, single path component)
        if (Smithery.MODID.equals(namespace)) {
            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                if (tt.id().getPath().equals(itemName)) {
                    return jsonStream(buildToolModelJson(tt));
                }
            }
            // Impressed-sand item model parents the block model so the BER can render
            // it via the standard ItemStackRenderState pipeline.
            if (itemName.startsWith(IMPRESSED_SAND_PREFIX)) {
                String partPath = itemName.substring(IMPRESSED_SAND_PREFIX.length());
                if (isRegisteredPartTypePath(partPath)) {
                    return jsonStream(("""
                            {
                              "parent": "%s:block/%s"
                            }
                            """).formatted(Smithery.MODID, itemName));
                }
            }
        }
        // Then parts: find a registered (material × part) where material is in this namespace.
        for (Material m : SmitheryAPI.MATERIALS.all()) {
            if (!m.id().getNamespace().equals(namespace)) continue;
            String matPath = m.id().getPath();
            if (!itemName.startsWith(matPath + "_")) continue;
            String remaining = itemName.substring(matPath.length() + 1);
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (pt.id().getPath().equals(remaining)) {
                    return jsonStream(buildPartModelJson(pt));
                }
            }
        }
        return null;
    }

    private @Nullable IoSupplier<InputStream> resolveItemDefJson(String namespace, String itemName) {
        if (Smithery.MODID.equals(namespace)) {
            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                if (tt.id().getPath().equals(itemName)) {
                    return jsonStream(buildToolItemDefJson(tt));
                }
            }
            // Molten bucket: "molten_<material>_bucket"
            if (itemName.startsWith(MOLTEN_PREFIX) && itemName.endsWith(MOLTEN_BUCKET_SUFFIX)) {
                String matPath = itemName.substring(MOLTEN_PREFIX.length(),
                        itemName.length() - MOLTEN_BUCKET_SUFFIX.length());
                if (isMeltableMaterialPath(matPath)) {
                    return jsonStream(buildMoltenBucketItemDefJson());
                }
            }
            // Impressed-sand item def: references the dynamic item model.
            if (itemName.startsWith(IMPRESSED_SAND_PREFIX)) {
                String partPath = itemName.substring(IMPRESSED_SAND_PREFIX.length());
                if (isRegisteredPartTypePath(partPath)) {
                    return jsonStream(("""
                            {
                              "model": {
                                "type": "minecraft:model",
                                "model": "%s:item/%s"
                              }
                            }
                            """).formatted(Smithery.MODID, itemName));
                }
            }
        }
        for (Material m : SmitheryAPI.MATERIALS.all()) {
            if (!m.id().getNamespace().equals(namespace)) continue;
            String matPath = m.id().getPath();
            if (!itemName.startsWith(matPath + "_")) continue;
            String remaining = itemName.substring(matPath.length() + 1);
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (pt.id().getPath().equals(remaining)) {
                    return jsonStream(buildPartItemDefJson(m, pt));
                }
            }
        }
        return null;
    }

    private @Nullable IoSupplier<InputStream> resolveBlockstateJson(String namespace, String blockName) {
        if (!Smithery.MODID.equals(namespace)) return null;
        if (blockName.startsWith(MOLTEN_PREFIX)) {
            String matPath = blockName.substring(MOLTEN_PREFIX.length());
            if (!isMeltableMaterialPath(matPath)) return null;
            return jsonStream(buildMoltenBlockstateJson(blockName));
        }
        if (blockName.startsWith(IMPRESSED_SAND_PREFIX)) {
            String partPath = blockName.substring(IMPRESSED_SAND_PREFIX.length());
            if (!isRegisteredPartTypePath(partPath)) return null;
            // Standard single-variant blockstate pointing at the dynamic block model.
            return jsonStream(("""
                    {
                      "variants": {
                        "": { "model": "%s:block/%s" }
                      }
                    }
                    """).formatted(Smithery.MODID, blockName));
        }
        return null;
    }

    private @Nullable IoSupplier<InputStream> resolveBlockModelJson(String namespace, String blockName) {
        if (!Smithery.MODID.equals(namespace)) return null;
        if (blockName.startsWith(MOLTEN_PREFIX)) {
            String matPath = blockName.substring(MOLTEN_PREFIX.length());
            if (!isMeltableMaterialPath(matPath)) return null;
            return jsonStream(buildMoltenBlockModelJson());
        }
        if (blockName.startsWith(IMPRESSED_SAND_PREFIX)) {
            String partPath = blockName.substring(IMPRESSED_SAND_PREFIX.length());
            if (!isRegisteredPartTypePath(partPath)) return null;
            // Geometric (not textural) cutout: one 1×16×1 cuboid per sand pixel in
            // the part template. Part-shape pixels emit no cuboid → real hole, with
            // visible inner walls between sand cuboids and cutout pixels. Each cuboid
            // only emits the faces that face the cube perimeter or a cutout neighbor,
            // so internal sand-to-sand faces are skipped and don't z-fight.
            return () -> {
                try {
                    return new ByteArrayInputStream(
                            buildVoxelizedImpressedSandModel(partPath).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new java.io.IOException("Failed to build impressed sand model: " + partPath, e);
                }
            };
        }
        return null;
    }

    /** True if there's a registered PartType in the smithery namespace with this path. */
    private static boolean isRegisteredPartTypePath(String partPath) {
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.id().getNamespace().equals(Smithery.MODID)
                    && pt.id().getPath().equals(partPath)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable PartType findPartTypeByPath(String partPath) {
        for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
            if (pt.id().getNamespace().equals(Smithery.MODID)
                    && pt.id().getPath().equals(partPath)) {
                return pt;
            }
        }
        return null;
    }


    /**
     * Voxelized "sand with part-shaped hole" block model.
     *
     * For each pixel of the part template:
     *   - If the alpha is below the threshold (sand pixel): emit a 1×16×1 cuboid
     *     with the sand texture on every outward-facing face.
     *   - If the alpha is at/above the threshold (part-shape pixel): emit nothing.
     *     The absence of geometry IS the cutout — and because each adjacent sand
     *     cuboid emits its perimeter face, the walls of the cutout show sand from
     *     every angle (no see-through edges).
     *
     * Internal faces (sand pixel adjacent to sand pixel) are skipped to avoid
     * z-fighting and to keep the geometry minimal.
     */
    private static String buildVoxelizedImpressedSandModel(String partPath) throws IOException {
        PartType pt = findPartTypeByPath(partPath);
        if (pt == null) throw new IOException("No PartType: " + partPath);

        Identifier tmplId = pt.textureTemplate();
        BufferedImage tmpl = readClasspathPng(
                "/assets/" + tmplId.getNamespace() + "/textures/" + tmplId.getPath() + ".png");
        final int W = 16, H = 16;

        // Resample template to a 16×16 cutout mask. true = part shape (no geometry).
        boolean[][] cutout = new boolean[W][H];
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                int tx = (int)((long) x * tmpl.getWidth()  / W);
                int tz = (int)((long) z * tmpl.getHeight() / H);
                int alpha = (tmpl.getRGB(tx, tz) >>> 24) & 0xFF;
                cutout[x][z] = (alpha > 16);
            }
        }

        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\n");
        sb.append("  \"parent\": \"minecraft:block/block\",\n");
        sb.append("  \"textures\": {\n");
        sb.append("    \"particle\": \"").append(Smithery.MODID).append(":block/casting_sand\",\n");
        sb.append("    \"sand\":     \"").append(Smithery.MODID).append(":block/casting_sand\"\n");
        sb.append("  },\n");
        sb.append("  \"elements\": [");

        boolean firstElement = true;
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                if (cutout[x][z]) continue;

                if (!firstElement) sb.append(",");
                firstElement = false;

                sb.append("\n    {\n");
                sb.append("      \"from\": [").append(x).append(", 0, ").append(z).append("],\n");
                sb.append("      \"to\":   [").append(x + 1).append(", 16, ").append(z + 1).append("],\n");
                sb.append("      \"faces\": {\n");

                // Top + bottom: always visible (top is sand surface; bottom faces
                // outward from the cube and is the underside of the sand layer).
                appendFace(sb, "up",   x, z, x + 1, z + 1, true);
                sb.append(",\n");
                appendFace(sb, "down", x, z, x + 1, z + 1, false);

                // Side faces: emit only if the neighbor in that direction is
                // a cutout pixel or the cube edge. Sand-to-sand interior faces
                // are skipped.
                if (z == 0       || cutout[x][z - 1]) { sb.append(",\n"); appendSideFace(sb, "north", x, x + 1); }
                if (z == H - 1   || cutout[x][z + 1]) { sb.append(",\n"); appendSideFace(sb, "south", x, x + 1); }
                if (x == 0       || cutout[x - 1][z]) { sb.append(",\n"); appendSideFace(sb, "west",  z, z + 1); }
                if (x == W - 1   || cutout[x + 1][z]) { sb.append(",\n"); appendSideFace(sb, "east",  z, z + 1); }

                sb.append("\n      }\n");
                sb.append("    }");
            }
        }

        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    /** Top/bottom face JSON — UV picks the matching 1×1 patch of the sand texture. */
    private static void appendFace(StringBuilder sb, String dir,
                                   int uMin, int vMin, int uMax, int vMax, boolean isUp) {
        sb.append("        \"").append(dir).append("\": ")
          .append("{\"uv\": [").append(uMin).append(", ").append(vMin)
          .append(", ").append(uMax).append(", ").append(vMax).append("], ")
          .append("\"texture\": \"#sand\"}");
    }

    /**
     * Side face JSON — UV maps a 1-px-wide vertical strip of the sand texture across
     * the full 1×16 wall. Looks like a column of sand grain from the side, consistent
     * with the surrounding sand surface.
     */
    private static void appendSideFace(StringBuilder sb, String dir, int uMin, int uMax) {
        sb.append("        \"").append(dir).append("\": ")
          .append("{\"uv\": [").append(uMin).append(", 0, ").append(uMax).append(", 16], ")
          .append("\"texture\": \"#sand\"}");
    }

    private static BufferedImage readClasspathPng(String path) throws IOException {
        try (InputStream in = SmitheryGeneratedPack.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Resource not found on classpath: " + path);
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("Could not decode PNG at: " + path);
            return img;
        }
    }

    /** True if there's a registered material with this path AND a non-zero meltingTemp. */
    private static boolean isMeltableMaterialPath(String matPath) {
        for (Material m : SmitheryAPI.MATERIALS.all()) {
            if (m.id().getNamespace().equals(Smithery.MODID)
                    && m.id().getPath().equals(matPath)
                    && m.stats().meltingTemp() > 0f) {
                return true;
            }
        }
        return false;
    }

    private static String buildPartModelJson(PartType pt) {
        // Single shared template texture per PartType. Material color is applied via tint.
        Identifier tex = pt.textureTemplate();
        return """
                {
                  "parent": "item/generated",
                  "textures": {
                    "layer0": "%s"
                  }
                }
                """.formatted(tex);
    }

    private static String buildPartItemDefJson(Material m, PartType pt) {
        // Item definition declares the model + the tint source for layer 0.
        // The same JSON shape works for any material — the tint is dynamic per stack.
        Identifier modelId = Identifier.fromNamespaceAndPath(m.id().getNamespace(),
                "item/" + m.id().getPath() + "_" + pt.id().getPath());
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s",
                    "tints": [
                      { "type": "smithery:part_material" }
                    ]
                  }
                }
                """.formatted(modelId);
    }

    private static String buildToolModelJson(ToolType tt) {
        return """
                {
                  "parent": "item/handheld",
                  "textures": {
                    "layer0": "smithery:item/tool/%s"
                  }
                }
                """.formatted(tt.id().getPath());
    }

    private static String buildToolItemDefJson(ToolType tt) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "smithery:item/%s",
                    "tints": [
                      { "type": "smithery:tool_primary_material" }
                    ]
                  }
                }
                """.formatted(tt.id().getPath());
    }

    // ---- Molten fluid + bucket JSON ----

    /**
     * Blockstate for a molten-X LiquidBlock. Single empty-variant key, pointing at the
     * per-material block model. The actual fluid rendering uses the FluidModel registered
     * in SmitheryFluidsClient — this blockstate exists so the LiquidBlock has a valid
     * particle-texture resolution path.
     */
    private static String buildMoltenBlockstateJson(String blockName) {
        return """
                {
                  "variants": {
                    "": { "model": "%s:block/%s" }
                  }
                }
                """.formatted(Smithery.MODID, blockName);
    }

    /** Particle texture only — break particles use this; the actual fluid is FluidModel-rendered. */
    private static String buildMoltenBlockModelJson() {
        return """
                {
                  "textures": {
                    "particle": "%s:block/molten_still"
                  }
                }
                """.formatted(Smithery.MODID);
    }

    /**
     * Per-bucket item definition. All buckets share one two-layer model:
     *   layer0 = bucket casing (smithery:item/molten_bucket_base)
     *   layer1 = molten fluid swatch (smithery:item/molten_bucket_fluid, grayscale)
     *
     * The tints array is indexed by layer. Layer 0 gets a constant white tint
     * (= identity multiply, untouched bucket appearance). Layer 1 gets the
     * dynamic per-material color from MoltenBucketTintSource. This is what
     * keeps the bucket casing the same gray on every bucket while only the
     * fluid swatch picks up the material's moltenColor.
     */
    private static String buildMoltenBucketItemDefJson() {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/molten_bucket",
                    "tints": [
                      { "type": "minecraft:constant", "value": -1 },
                      { "type": "%s:molten_bucket" }
                    ]
                  }
                }
                """.formatted(Smithery.MODID, Smithery.MODID);
    }

    private static IoSupplier<InputStream> jsonStream(String content) {
        return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
