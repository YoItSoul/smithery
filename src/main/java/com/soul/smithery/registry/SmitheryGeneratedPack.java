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
import java.util.List;
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

    /** Path prefix for the synthesized per-slot tool layer textures. */
    private static final String TOOL_LAYER_TEX_PREFIX = "textures/item/tool/";
    private static final String PNG_SUFFIX = ".png";

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

        // Synthesized per-slot tool layer PNGs — derived from vanilla iron tool textures by
        // classifying pixels (metal vs handle), masking out everything except the slot's region,
        // and desaturating to grayscale so the per-slot material tint multiplies cleanly.
        if (path.endsWith(PNG_SUFFIX) && path.startsWith(TOOL_LAYER_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            return resolveToolSlotTexture(path);
        }

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
                // Per-slot layer textures (synthesized from vanilla iron tool sources).
                // Must be advertised here — the item-sprite atlas builder discovers candidate
                // PNGs via listResources, not via direct getResource calls. Without this,
                // the atlas has no entry for our synthesized textures and tools render
                // with the missing-texture placeholder per layer.
                List<ToolType.Slot> slots = tt.slots();
                for (int i = 0; i < slots.size(); i++) {
                    String texName = path + "/" + i + "_" + slots.get(i).partType().id().getPath();
                    String pngPath = TOOL_LAYER_TEX_PREFIX + texName + PNG_SUFFIX;
                    final String capturedPng = pngPath;
                    emitIfMatches(namespace, pngPath, prefix,
                            () -> resolveToolSlotTexture(capturedPng), output);
                }
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
        BufferedImage tmpl = readTemplateTexture(tmplId);
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

    /**
     * Reads a PartType's template texture as a normalized ARGB image.
     *
     * Source resolution: routes through the client {@link net.minecraft.server.packs.resources.ResourceManager},
     * NOT the JVM classpath. In NeoForge's module-based class layout, vanilla minecraft assets live in a
     * separate classloader that mod classes can't see directly — {@code getResourceAsStream("/assets/minecraft/...")}
     * returns null. The resource manager, by contrast, has indexed every loaded pack (vanilla + forge + mod
     * resources) by the time our IoSupplier is invoked, so a single {@code getResource(...)} call covers
     * smithery's own templates, vanilla textures referenced by modder PartTypes, and anything contributed
     * by other mods.
     *
     * Output normalization: PNGs come in many colour models — iron_ingot.png is 8-bit grayscale,
     * ender_pearl.png is 4-bit indexed with a tRNS transparency chunk. {@code BufferedImage.getRGB()}
     * on indexed PNGs has been historically flaky across JDK versions for alpha readback. Redrawing
     * into TYPE_INT_ARGB guarantees the alpha-channel readback used by the voxelizer is correct
     * regardless of the source colour model.
     *
     * Client-only: this method is invoked exclusively from client-resource generation paths
     * (the early {@code if (type != PackType.CLIENT_RESOURCES) return null} in
     * {@link #getResource} prevents server-side reach).
     */
    private static BufferedImage readTemplateTexture(Identifier tmplId) throws IOException {
        Identifier resourceLoc = Identifier.fromNamespaceAndPath(
                tmplId.getNamespace(), "textures/" + tmplId.getPath() + ".png");
        net.minecraft.server.packs.resources.ResourceManager rm =
                net.minecraft.client.Minecraft.getInstance().getResourceManager();
        Optional<net.minecraft.server.packs.resources.Resource> resource = rm.getResource(resourceLoc);
        if (resource.isEmpty()) {
            throw new IOException("Texture not found in resource manager: " + resourceLoc);
        }
        try (InputStream in = resource.get().open()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("Could not decode PNG at: " + resourceLoc);
            if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = argb.createGraphics();
            try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
            return argb;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  Per-slot tool layer texture synthesizer
    // ═══════════════════════════════════════════════════════════════════════════════════════
    //
    // Generates one grayscale-with-alpha PNG per (tool × slot) by sampling the matching
    // vanilla iron tool texture, classifying each opaque pixel as METAL or HANDLE based on
    // colour, then applying a slot-specific mask:
    //
    //   Sword (from minecraft:item/iron_sword):
    //     slot 0 sword_blade : METAL pixels except the guard band
    //     slot 1 guard       : METAL pixels adjacent to HANDLE pixels (the cross-piece)
    //     slot 2 handle      : HANDLE pixels (the wooden grip)
    //     slot 3 binder      : small disc centered on the guard centroid
    //
    //   Pickaxe (from minecraft:item/iron_pickaxe):
    //     slot 0 pick_head   : METAL pixels except the binder disc
    //     slot 1 handle (main): HANDLE pixels in the upper half of the shaft
    //     slot 2 handle (fore): HANDLE pixels in the lower half of the shaft
    //     slot 3 binder      : small disc at the head's centroid
    //
    // Output pixels are desaturated to luminance + alpha so each layer's slot tint
    // (smithery:tool_slot_material) multiplies cleanly into the slot's partColor.

    private enum PixelKind { TRANSPARENT, METAL, HANDLE }

    /** Texture path format: textures/item/tool/<tool>/<slotIndex>_<partTypePath>.png */
    private @Nullable IoSupplier<InputStream> resolveToolSlotTexture(String path) {
        String stripped = path.substring(TOOL_LAYER_TEX_PREFIX.length(),
                path.length() - PNG_SUFFIX.length());
        int slash = stripped.indexOf('/');
        if (slash < 0) return null;
        String toolPath = stripped.substring(0, slash);
        String slotPart = stripped.substring(slash + 1);
        int underscore = slotPart.indexOf('_');
        if (underscore <= 0) return null;
        int slotIndex;
        try {
            slotIndex = Integer.parseInt(slotPart.substring(0, underscore));
        } catch (NumberFormatException e) {
            return null;
        }
        return () -> {
            try {
                BufferedImage png = synthesizeToolSlotTexture(toolPath, slotIndex);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                ImageIO.write(png, "PNG", out);
                return new ByteArrayInputStream(out.toByteArray());
            } catch (IOException e) {
                throw new IOException("Failed to synthesize tool slot texture: " + path, e);
            }
        };
    }

    private static BufferedImage synthesizeToolSlotTexture(String toolPath, int slotIndex) throws IOException {
        Identifier sourceId = switch (toolPath) {
            case "sword"   -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_sword");
            case "pickaxe" -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_pickaxe");
            default        -> null;
        };
        if (sourceId == null) {
            throw new IOException("No source-texture mapping for tool: " + toolPath);
        }
        BufferedImage source = readTemplateTexture(sourceId);
        int W = source.getWidth(), H = source.getHeight();
        PixelKind[][] kind = classifyPixels(source, W, H);

        // Compute per-slot mask of pixels to keep.
        boolean[][] mask = computeSlotMask(toolPath, slotIndex, kind, W, H);

        // Emit grayscale-with-alpha: tint multiplies cleanly into material colour.
        // Handle pixels get their luminance range stretched up so different handle
        // materials read as visually distinct after tinting (vanilla brown handles
        // are very dim: luma ~30–80, which when multiplied by a material colour
        // produces dark and nearly-indistinguishable shades of every material).
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!mask[x][y]) continue;
                int rgba = source.getRGB(x, y);
                out.setRGB(x, y, desaturate(rgba, kind[x][y]));
            }
        }
        return out;
    }

    private static PixelKind[][] classifyPixels(BufferedImage src, int W, int H) {
        PixelKind[][] kind = new PixelKind[W][H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                kind[x][y] = classifyPixel(src.getRGB(x, y));
            }
        }
        return kind;
    }

    private static PixelKind classifyPixel(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 32) return PixelKind.TRANSPARENT;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8)  & 0xFF;
        int b = (argb)        & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int chroma = max - min;
        // Warm-tinted (R > G > B with non-trivial chroma) → wooden handle
        if (r > g && g > b && chroma > 24) return PixelKind.HANDLE;
        // Low-saturation pixels (silvery) → metal
        if (chroma < 40) return PixelKind.METAL;
        // Anything else: classify by which channel dominates — warmth ⇒ handle, else metal
        return (r > b + 8) ? PixelKind.HANDLE : PixelKind.METAL;
    }

    /**
     * Desaturates to luminance preserving alpha; HANDLE pixels are rescaled into a much
     * brighter range so different handle materials read as visually distinct after the
     * multiplicative slot tint. Vanilla wooden handles have luma ~30–80; left as-is, every
     * handle material would render as a very dim shade and the iron/wood/copper variants
     * would be hard to tell apart. Mapping [30,80] → [180,240] preserves the wood-grain
     * shading while keeping the result bright enough for the slot's material colour to
     * dominate. METAL pixels are already bright in the source texture and pass through
     * untouched so dark pommel/outline shading reads as shadow detail, not as muddied tint.
     */
    private static int desaturate(int argb, PixelKind kind) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8)  & 0xFF;
        int b = (argb)        & 0xFF;
        // BT.601 luma
        int y = (299 * r + 587 * g + 114 * b + 500) / 1000;
        if (kind == PixelKind.HANDLE) {
            // Linear remap [30, 80] → [180, 240], clamp outside.
            y = 180 + (y - 30) * 60 / 50;
            y = Math.max(180, Math.min(240, y));
        } else {
            y = Math.max(0, Math.min(255, y));
        }
        return (a << 24) | (y << 16) | (y << 8) | y;
    }

    private static boolean[][] computeSlotMask(String toolPath, int slotIndex,
                                                PixelKind[][] kind, int W, int H) {
        switch (toolPath) {
            case "sword" -> {
                // Vanilla iron_sword runs bottom-left (handle / pommel) → top-right (blade tip).
                // Anti-diagonal progress separates the blade-half from the pommel-half so the
                // tiny grey pommel cluster at the bottom-left doesn't leak into the BLADE layer.
                // progress(x,y) ∈ [0,1]: 0 at the bottom-left, 1 at the top-right.
                int span = (W - 1) + (H - 1);
                java.util.function.BiPredicate<Integer, Integer> inBladeHalf =
                        (x, y) -> (x + (H - 1 - y)) * 100 >= 30 * span;       // >= 0.30 progress
                boolean[][] guardBand = metalBoundaryWithHandle(kind, W, H, inBladeHalf);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL
                                                          && inBladeHalf.test(x, y)
                                                          && !guardBand[x][y]);
                    case 1 -> guardBand;
                    case 2 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.HANDLE);
                    case 3 -> unionMasks(W, H,
                            // Pommel: METAL pixels in the lower-left half (not in BLADE)
                            maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL
                                                       && !inBladeHalf.test(x, y)),
                            // Plus a small accent disc at the guard centroid for visibility
                            centeredDisc(guardBand, W, H, 1));
                    default -> new boolean[W][H];
                };
            }
            case "pickaxe" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> handleHalf(kind, W, H, true);
                    case 2 -> handleHalf(kind, W, H, false);
                    case 3 -> binder;
                    default -> new boolean[W][H];
                };
            }
        }
        return new boolean[W][H];
    }

    private static boolean[][] unionMasks(int W, int H, boolean[][] a, boolean[][] b) {
        boolean[][] m = new boolean[W][H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) m[x][y] = a[x][y] || b[x][y];
        return m;
    }

    /** Functional predicate to boolean-mask conversion. */
    @FunctionalInterface
    private interface XY { boolean test(int x, int y); }

    private static boolean[][] maskWhere(int W, int H, XY p) {
        boolean[][] m = new boolean[W][H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) m[x][y] = p.test(x, y);
        return m;
    }

    /**
     * METAL pixels with at least one 4-neighbor HANDLE pixel — the guard band.
     * {@code restrict} filters which positions are eligible (used to keep the sword's
     * pommel out of the guard band even though the pommel is METAL adjacent to HANDLE).
     */
    private static boolean[][] metalBoundaryWithHandle(PixelKind[][] kind, int W, int H,
                                                       java.util.function.BiPredicate<Integer, Integer> restrict) {
        boolean[][] m = new boolean[W][H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (kind[x][y] != PixelKind.METAL) continue;
                if (restrict != null && !restrict.test(x, y)) continue;
                boolean adj =
                       (x > 0     && kind[x - 1][y] == PixelKind.HANDLE)
                    || (x < W - 1 && kind[x + 1][y] == PixelKind.HANDLE)
                    || (y > 0     && kind[x][y - 1] == PixelKind.HANDLE)
                    || (y < H - 1 && kind[x][y + 1] == PixelKind.HANDLE);
                if (adj) m[x][y] = true;
            }
        }
        return m;
    }

    /** Disc centered on the centroid of the input boolean mask. radius in pixels. */
    private static boolean[][] centeredDisc(boolean[][] anchor, int W, int H, int radius) {
        long sx = 0, sy = 0, n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (anchor[x][y]) { sx += x; sy += y; n++; }
        boolean[][] m = new boolean[W][H];
        if (n == 0) return m;
        int cx = (int) (sx / n), cy = (int) (sy / n);
        int r2 = radius * radius;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r2) m[x][y] = true;
            }
        }
        return m;
    }

    /**
     * Disc at the centroid of METAL pixels in the upper third of the image (the head proper).
     * Clipping to the upper region keeps the disc anchored to the head where the handle
     * attaches — without this, the long descending spike pulls the all-metal centroid
     * sideways and the binder lands off-center along the spike instead of inside the head.
     */
    private static boolean[][] headCenteredDisc(PixelKind[][] kind, int W, int H, int radius) {
        int upperY = Math.max(1, H / 3);  // top third = head region in 16-tall pickaxe textures
        long sx = 0, sy = 0, n = 0;
        for (int y = 0; y < upperY; y++) for (int x = 0; x < W; x++) {
            if (kind[x][y] == PixelKind.METAL) { sx += x; sy += y; n++; }
        }
        // Fall back to all-metal centroid if the top portion is empty (shouldn't happen
        // for vanilla iron_pickaxe, but defensive in case a modder remaps the source).
        if (n == 0) {
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                if (kind[x][y] == PixelKind.METAL) { sx += x; sy += y; n++; }
            }
        }
        boolean[][] m = new boolean[W][H];
        if (n == 0) return m;
        int cx = (int) (sx / n), cy = (int) (sy / n);
        int r2 = radius * radius;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r2 && kind[x][y] == PixelKind.METAL) m[x][y] = true;
            }
        }
        return m;
    }

    /**
     * HANDLE pixels split by Y into upper / lower halves using the handle's own centroid as
     * the cut. {@code upper=true} keeps pixels with Y < midY (closer to the head, which is at
     * the top of vanilla pickaxe textures).
     */
    private static boolean[][] handleHalf(PixelKind[][] kind, int W, int H, boolean upper) {
        long sy = 0, n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (kind[x][y] == PixelKind.HANDLE) { sy += y; n++; }
        }
        boolean[][] m = new boolean[W][H];
        if (n == 0) return m;
        int midY = (int) (sy / n);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (kind[x][y] != PixelKind.HANDLE) continue;
                if (upper ? (y < midY) : (y >= midY)) m[x][y] = true;
            }
        }
        return m;
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

    /**
     * Builds the model JSON for a tool with one texture layer per slot. Texture path convention:
     * {@code smithery:item/tool/<tool_path>/<slotIndex>_<partTypePath>} — slot index keeps
     * duplicate part types (e.g. pickaxe's two handles) disambiguated. Artists drop pre-shaded
     * silhouettes into {@code assets/smithery/textures/item/tool/<tool>/<index>_<part>.png}.
     */
    private static String buildToolModelJson(ToolType tt) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        sb.append("  \"parent\": \"item/handheld\",\n");
        sb.append("  \"textures\": {\n");
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            String partPath = slots.get(i).partType().id().getPath();
            sb.append("    \"layer").append(i).append("\": \"")
              .append(Smithery.MODID).append(":item/tool/")
              .append(tt.id().getPath()).append("/")
              .append(i).append("_").append(partPath).append("\"");
            if (i < slots.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Builds the item-definition JSON for a tool. One {@code tool_slot_material} tint per
     * slot in declaration order — each tint reads {@link com.soul.smithery.item.tool.ToolComposition}
     * and returns the partColor of the material occupying that slot, so every material in the
     * tool's composition shows up at render time in its corresponding silhouette layer.
     */
    private static String buildToolItemDefJson(ToolType tt) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        sb.append("  \"model\": {\n");
        sb.append("    \"type\": \"minecraft:model\",\n");
        sb.append("    \"model\": \"").append(Smithery.MODID).append(":item/")
          .append(tt.id().getPath()).append("\",\n");
        sb.append("    \"tints\": [\n");
        int n = tt.slots().size();
        for (int i = 0; i < n; i++) {
            sb.append("      { \"type\": \"smithery:tool_slot_material\", \"slot\": ").append(i).append(" }");
            if (i < n - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
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
