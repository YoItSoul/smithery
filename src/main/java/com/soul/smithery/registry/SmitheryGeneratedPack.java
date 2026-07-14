package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

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
 * Runtime client-resource pack that emits per-material model and item-definition JSONs for
 * every registered (Material × PartType) and {@link ToolType} combination.
 *
 * <p>Regenerated on every resource reload from the live {@link SmitheryAPI} registry state,
 * so a material added via JSON datapack at runtime produces a fully renderable item with no
 * static asset files. Texture PNGs are NOT served by this pack — they live in
 * {@code src/main/resources} as shared grayscale templates, one per PartType / ToolType, with
 * material color applied via a registered {@code ItemTintSource} at render time.
 */
public class SmitheryGeneratedPack implements PackResources {

    /** Stable pack id used by {@link SmitheryPackProvider} when registering the pack. */
    public static final String PACK_ID = "smithery_generated";

    private static final String MODELS_PREFIX       = "models/item/";
    private static final String BLOCK_MODELS_PREFIX = "models/block/";
    private static final String BLOCKSTATES_PREFIX  = "blockstates/";
    private static final String JSON_SUFFIX         = ".json";

    private static final String MOLTEN_PREFIX        = "molten_";
    private static final String MOLTEN_BUCKET_SUFFIX = "_bucket";

    private static final String IMPRESSED_SAND_PREFIX = "casting_sand_impressed_";

    private static final String TOOL_LAYER_TEX_PREFIX = "textures/item/tool/";
    private static final String PNG_SUFFIX = ".png";

    private static final String PART_TEMPLATE_TEX_PREFIX = "textures/item/part/";

    private static final String BLOCK_TEX_PREFIX = "textures/block/";
    private static final String RED_SLIME_BLOCK = "red_slime_block";

    private static final String SIMPLE_ITEM_TEX_PREFIX = "textures/item/";

    private record SimpleItem(ResourceLocation source, int tintArgb) {}

    private static final java.util.Map<String, SimpleItem> SIMPLE_ITEMS;
    static {
        ResourceLocation vanillaString    = ResourceLocation.fromNamespaceAndPath("minecraft", "item/string");
        ResourceLocation vanillaSlimeBall = ResourceLocation.fromNamespaceAndPath("minecraft", "item/slime_ball");
        SIMPLE_ITEMS = java.util.Map.of(
                "flamestring",              new SimpleItem(vanillaString,    0xFFFF6622),
                "breezestring",             new SimpleItem(vanillaString,    0xFFB0E2FF),
                "kelp_string",              new SimpleItem(vanillaString,    0xFF3F8E45),
                "unfinished_kelp_string_1", new SimpleItem(vanillaString,    0xFF7C9479),
                "unfinished_kelp_string_2", new SimpleItem(vanillaString,    0xFF5F9059),
                "unfinished_kelp_string_3", new SimpleItem(vanillaString,    0xFF4A8F49),
                "red_slime",                new SimpleItem(vanillaSlimeBall, 0xFFCC2233)
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns null — this pack does not expose any root-level resources;
     * {@code pack.mcmeta} is served via {@link #getMetadataSection}.
     */
    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves a requested resource to either a synthesized PNG (tool-layer textures,
     * part templates, the red slime block texture, simple-item textures) or a generated JSON
     * (model, blockstate, item definition). Returns null for anything outside the
     * client-resources pack type or that doesn't match a recognized path.
     */
    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        String path = location.getPath();
        String namespace = location.getNamespace();

        if (path.endsWith(PNG_SUFFIX) && path.startsWith(TOOL_LAYER_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            return resolveToolSlotTexture(path);
        }

        if (path.endsWith(PNG_SUFFIX) && path.startsWith(PART_TEMPLATE_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            String partName = path.substring(PART_TEMPLATE_TEX_PREFIX.length(),
                    path.length() - PNG_SUFFIX.length());
            return resolveSynthesizedPartTemplate(partName);
        }

        if (path.endsWith(PNG_SUFFIX) && path.startsWith(BLOCK_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            String blockName = path.substring(BLOCK_TEX_PREFIX.length(),
                    path.length() - PNG_SUFFIX.length());
            if (RED_SLIME_BLOCK.equals(blockName)) {
                return () -> emitPng(synthesizeRedSlimeTexture());
            }
        }

        if (path.endsWith(PNG_SUFFIX) && path.startsWith(SIMPLE_ITEM_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            String name = path.substring(SIMPLE_ITEM_TEX_PREFIX.length(),
                    path.length() - PNG_SUFFIX.length());
            SimpleItem si = SIMPLE_ITEMS.get(name);
            if (si != null) {
                return () -> emitPng(synthesizeSimpleItemTexture(si));
            }
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
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Advertises every generated resource path (tool models, item defs, layer textures,
     * impressed-sand variants, molten fluid + bucket assets, red slime block, simple items)
     * to consumers that discover candidates by listing rather than direct fetch — the item
     * sprite atlas builder is the primary example. Filters by the requested directory because
     * vanilla loaders share this method and would otherwise try to parse our model JSONs as
     * their own formats.
     */
    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;

        String prefix = directory.isEmpty() ? "" : directory.endsWith("/") ? directory : directory + "/";

        if (Smithery.MODID.equals(namespace)) {
            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                String path = tt.id().getPath();
                emitIfMatches(namespace, MODELS_PREFIX + path + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, path), output);

                boolean isBow = "bow".equals(path);
                int frameCount = isBow ? 3 : 0;
                for (int frame = 0; frame < frameCount; frame++) {
                    String pullName = "bow_pulling_" + frame;
                    final String capturedPull = pullName;
                    emitIfMatches(namespace, MODELS_PREFIX + pullName + JSON_SUFFIX, prefix,
                            () -> resolveModelJson(namespace, capturedPull), output);
                }

                List<ToolType.Slot> slots = tt.slots();
                for (int i = 0; i < slots.size(); i++) {
                    String partPath = slots.get(i).partType().id().getPath();
                    String texBase = path + "/" + i + "_" + partPath;
                    emitToolLayerTexture(namespace, texBase, prefix, output);
                    if (isBow) {
                        for (int frame = 0; frame < frameCount; frame++) {
                            emitToolLayerTexture(namespace, texBase + "_pulling_" + frame, prefix, output);
                        }
                    }
                }
            }
        }

        for (Material m : SmitheryAPI.MATERIALS.all()) {
            if (!m.id().getNamespace().equals(namespace)) continue;
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (pt.syntheticCast()) continue;
                if (!com.soul.smithery.api.part.PartEligibility.isAllowed(pt.id(), m.id())) continue;
                String itemName = m.id().getPath() + "_" + pt.id().getPath();
                emitIfMatches(namespace, MODELS_PREFIX + itemName + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, itemName), output);
            }
        }

        // List every part template this pack can synthesize — membership is defined by the
        // resolver itself so new synthesized parts can't silently miss the atlas scan again.
        if (Smithery.MODID.equals(namespace)) {
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (!pt.id().getNamespace().equals(Smithery.MODID)) continue;
                String partName = pt.id().getPath();
                if (resolveSynthesizedPartTemplate(partName) == null) continue;
                String pngPath = PART_TEMPLATE_TEX_PREFIX + partName + PNG_SUFFIX;
                emitIfMatches(namespace, pngPath, prefix,
                        () -> resolveSynthesizedPartTemplate(partName), output);
            }
        }

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
                emitIfMatches(namespace, MODELS_PREFIX       + bucketId + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, bucketId), output);
            }
        }

        if (Smithery.MODID.equals(namespace)) {
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                String name = IMPRESSED_SAND_PREFIX + pt.id().getPath();
                emitIfMatches(namespace, BLOCKSTATES_PREFIX  + name + JSON_SUFFIX, prefix,
                        () -> resolveBlockstateJson(namespace, name), output);
                emitIfMatches(namespace, BLOCK_MODELS_PREFIX + name + JSON_SUFFIX, prefix,
                        () -> resolveBlockModelJson(namespace, name), output);
                emitIfMatches(namespace, MODELS_PREFIX       + name + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, name), output);
            }
        }

        if (Smithery.MODID.equals(namespace)) {
            String name = RED_SLIME_BLOCK;
            emitIfMatches(namespace, BLOCKSTATES_PREFIX  + name + JSON_SUFFIX, prefix,
                    () -> resolveBlockstateJson(namespace, name), output);
            emitIfMatches(namespace, BLOCK_MODELS_PREFIX + name + JSON_SUFFIX, prefix,
                    () -> resolveBlockModelJson(namespace, name), output);
            emitIfMatches(namespace, MODELS_PREFIX       + name + JSON_SUFFIX, prefix,
                    () -> resolveModelJson(namespace, name), output);
            emitIfMatches(namespace, BLOCK_TEX_PREFIX    + name + PNG_SUFFIX, prefix,
                    () -> {
                        try {
                            BufferedImage img = synthesizeRedSlimeTexture();
                            return () -> emitPng(img);
                        } catch (IOException e) {
                            return null;
                        }
                    }, output);
        }

        if (Smithery.MODID.equals(namespace)) {
            for (var entry : SIMPLE_ITEMS.entrySet()) {
                String name = entry.getKey();
                final SimpleItem captured = entry.getValue();
                emitIfMatches(namespace, MODELS_PREFIX       + name + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, name), output);
                emitIfMatches(namespace, SIMPLE_ITEM_TEX_PREFIX + name + PNG_SUFFIX, prefix,
                        () -> () -> {
                            try { return emitPng(synthesizeSimpleItemTexture(captured)); }
                            catch (IOException e) { throw new IOException("simple item texture: " + name, e); }
                        }, output);
            }
        }
    }

    private static void emitIfMatches(String namespace, String fullPath, String requestedPrefix,
                                      java.util.function.Supplier<IoSupplier<InputStream>> contentSupplier,
                                      ResourceOutput output) {
        if (!fullPath.startsWith(requestedPrefix)) return;
        IoSupplier<InputStream> content = contentSupplier.get();
        if (content == null) return;
        output.accept(ResourceLocation.fromNamespaceAndPath(namespace, fullPath), content);
    }

    private void emitToolLayerTexture(String namespace, String texName, String requestedPrefix,
                                       ResourceOutput output) {
        String pngPath = TOOL_LAYER_TEX_PREFIX + texName + PNG_SUFFIX;
        final String captured = pngPath;
        emitIfMatches(namespace, pngPath, requestedPrefix,
                () -> resolveToolSlotTexture(captured), output);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reports the Smithery namespace plus every namespace that owns at least one
     * registered {@link Material}.
     */
    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) return Set.of();
        Set<String> ns = new HashSet<>();
        ns.add(Smithery.MODID);
        for (Material m : SmitheryAPI.MATERIALS.all()) ns.add(m.id().getNamespace());
        return ns;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serves a synthesized {@code pack.mcmeta} that always reports the running game's
     * current client pack format, so the pack never becomes incompatible with the game version
     * it is generated inside of.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (PackMetadataSection.TYPE.equals(deserializer)) {
            return (T) new PackMetadataSection(
                    Component.literal("Smithery generated resources"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String packId() { return PACK_ID; }

    /** {@inheritDoc} */
    @Override
    public boolean isBuiltin() { return true; }

    /** {@inheritDoc} */
    @Override
    public void close() {  }

    private @Nullable IoSupplier<InputStream> resolveModelJson(String namespace, String itemName) {
        if (Smithery.MODID.equals(namespace)) {
            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                if (tt.id().getPath().equals(itemName)) {
                    return jsonStream(buildToolModelJson(tt));
                }
                if ("bow".equals(tt.id().getPath())) {
                    for (int frame = 0; frame < 3; frame++) {
                        String pullName = "bow_pulling_" + frame;
                        if (pullName.equals(itemName)) {
                            return jsonStream(buildToolModelJsonFrame(tt, pullName.substring(4)));
                        }
                    }
                }
            }
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
            if (RED_SLIME_BLOCK.equals(itemName)) {
                return jsonStream(("""
                        {
                          "parent": "%s:block/%s"
                        }
                        """).formatted(Smithery.MODID, itemName));
            }
            if (SIMPLE_ITEMS.containsKey(itemName)) {
                return jsonStream(("""
                        {
                          "parent": "item/generated",
                          "textures": {
                            "layer0": "%s:item/%s"
                          }
                        }
                        """).formatted(Smithery.MODID, itemName));
            }
            if (itemName.startsWith(MOLTEN_PREFIX) && itemName.endsWith(MOLTEN_BUCKET_SUFFIX)) {
                String matPath = itemName.substring(MOLTEN_PREFIX.length(),
                        itemName.length() - MOLTEN_BUCKET_SUFFIX.length());
                if (isMeltableMaterialPath(matPath)) {
                    // Shared two-layer bucket model from static resources; layer 1 (the fluid
                    // window) is tinted per material via the item color handler.
                    return jsonStream(("""
                            {
                              "parent": "%s:item/molten_bucket"
                            }
                            """).formatted(Smithery.MODID));
                }
            }
        }
        for (Material m : SmitheryAPI.MATERIALS.all()) {
            if (!m.id().getNamespace().equals(namespace)) continue;
            String matPath = m.id().getPath();
            if (!itemName.startsWith(matPath + "_")) continue;
            String remaining = itemName.substring(matPath.length() + 1);
            for (PartType pt : SmitheryAPI.PART_TYPES.all()) {
                if (!pt.id().getPath().equals(remaining)) continue;
                if (pt.syntheticCast()) continue;
                if (!com.soul.smithery.api.part.PartEligibility.isAllowed(pt.id(), m.id())) continue;
                return jsonStream(buildPartModelJson(pt));
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
            return jsonStream(("""
                    {
                      "variants": {
                        "": { "model": "%s:block/%s" }
                      }
                    }
                    """).formatted(Smithery.MODID, blockName));
        }
        if (RED_SLIME_BLOCK.equals(blockName)) {
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
        if (RED_SLIME_BLOCK.equals(blockName)) {
            return jsonStream(("""
                    {
                      "parent": "minecraft:block/slime_block",
                      "textures": {
                        "particle": "%s:block/%s",
                        "texture":  "%s:block/%s"
                      }
                    }
                    """).formatted(Smithery.MODID, RED_SLIME_BLOCK, Smithery.MODID, RED_SLIME_BLOCK));
        }
        if (blockName.startsWith(IMPRESSED_SAND_PREFIX)) {
            String partPath = blockName.substring(IMPRESSED_SAND_PREFIX.length());
            if (!isRegisteredPartTypePath(partPath)) return null;
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


    private static String buildVoxelizedImpressedSandModel(String partPath) throws IOException {
        PartType pt = findPartTypeByPath(partPath);
        if (pt == null) throw new IOException("No PartType: " + partPath);

        ResourceLocation tmplId = pt.textureTemplate();
        BufferedImage tmpl = readTemplateTexture(tmplId);
        final int W = 16, H = 16;

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

                appendFace(sb, "up",   x, z, x + 1, z + 1, true);
                sb.append(",\n");
                appendFace(sb, "down", x, z, x + 1, z + 1, false);

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

    private static void appendFace(StringBuilder sb, String dir,
                                   int uMin, int vMin, int uMax, int vMax, boolean isUp) {
        sb.append("        \"").append(dir).append("\": ")
          .append("{\"uv\": [").append(uMin).append(", ").append(vMin)
          .append(", ").append(uMax).append(", ").append(vMax).append("], ")
          .append("\"texture\": \"#sand\"}");
    }

    private static void appendSideFace(StringBuilder sb, String dir, int uMin, int uMax) {
        sb.append("        \"").append(dir).append("\": ")
          .append("{\"uv\": [").append(uMin).append(", 0, ").append(uMax).append(", 16], ")
          .append("\"texture\": \"#sand\"}");
    }

    private static BufferedImage readTemplateTexture(ResourceLocation tmplId) throws IOException {
        ResourceLocation resourceLoc = ResourceLocation.fromNamespaceAndPath(
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

    private enum PixelKind { TRANSPARENT, METAL, HANDLE }

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
        String partAndFrame = slotPart.substring(underscore + 1);
        String frameSuffix = null;
        int pullingIdx = partAndFrame.lastIndexOf("_pulling_");
        if (pullingIdx > 0) {
            frameSuffix = partAndFrame.substring(pullingIdx + 1);
        }
        final String capturedFrame = frameSuffix;
        return () -> {
            try {
                BufferedImage png = synthesizeToolSlotTextureFrame(toolPath, slotIndex, capturedFrame);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                ImageIO.write(png, "PNG", out);
                return new ByteArrayInputStream(out.toByteArray());
            } catch (IOException e) {
                throw new IOException("Failed to synthesize tool slot texture: " + path, e);
            }
        };
    }

    private @Nullable IoSupplier<InputStream> resolveSynthesizedPartTemplate(String partName) {
        return switch (partName) {
            case "bow_limb"        -> () -> emitPng(synthesizeBowLimbTemplate());
            case "bowstring"       -> () -> emitPng(synthesizeBowstringTemplate());
            case "arrow_shaft"     -> () -> emitPng(synthesizeArrowShaftTemplate());
            case "fletching"       -> () -> emitPng(synthesizeFletchingTemplate());
            case "helmet_core"     -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_helmet"));
            case "chestplate_core" -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_chestplate"));
            case "leggings_core"   -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_leggings"));
            case "boots_core"      -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_boots"));
            case "armor_plates"    -> () -> emitPng(synthesizeArmorPlatesTemplate());
            case "armor_trim"      -> () -> emitPng(synthesizeArmorTrimTemplate());
            case "sharpening_stone" -> () -> emitPng(synthesizeNormalizedTemplate("item/flint"));
            case "polishing_stone"  -> () -> emitPng(synthesizeNormalizedTemplate("item/brick"));
            case "large_blade"      -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_sword"));
            case "hammer_head"      -> () -> emitPng(synthesizeNormalizedTemplate("block/iron_block"));
            case "large_plate"      -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_ingot"));
            case "kama_head"        -> () -> emitPng(synthesizeArmorPartTemplate("item/iron_hoe"));
            case "shuriken_blade"   -> () -> emitPng(synthesizeNormalizedTemplate("item/nether_star"));
            default                -> null;
        };
    }

    private static BufferedImage synthesizeBowstringTemplate() throws IOException {
        return readTemplateTexture(ResourceLocation.fromNamespaceAndPath("minecraft", "item/string"));
    }

    private static BufferedImage synthesizeBowLimbTemplate() throws IOException {
        BufferedImage src = readTemplateTexture(
                ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "item/part/pick_head"));
        BufferedImage rotated = rotateAroundCenter(src, -45.0);
        clearLowerHalf(rotated);
        return rotated;
    }

    private static BufferedImage synthesizeArrowShaftTemplate() throws IOException {
        BufferedImage src = readTemplateTexture(
                ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "item/part/handle"));
        return centeredUpperHalf(src);
    }

    private static BufferedImage synthesizeFletchingTemplate() throws IOException {
        BufferedImage src = readTemplateTexture(
                ResourceLocation.fromNamespaceAndPath("minecraft", "item/feather"));
        return centeredUpperHalf(src);
    }

    /**
     * Reads a vanilla armor-piece item texture (iron helmet/chest/leggings/boots) and desaturates
     * it to grayscale so the per-material part-color tint can recolor it at render time.
     *
     * <p>Used to ship a viable placeholder armor-part icon for every (material × armor core)
     * combination without hand-painting each one. The vanilla iron piece's silhouette is the
     * familiar shape; the grayscale conversion strips iron's gray-blue cast so tinting reads
     * cleanly across all materials.
     */
    private static BufferedImage synthesizeArmorPartTemplate(String vanillaPath) throws IOException {
        BufferedImage src = readTemplateTexture(
                ResourceLocation.fromNamespaceAndPath("minecraft", vanillaPath));
        int W = src.getWidth(), H = src.getHeight();
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) { out.setRGB(x, y, 0); continue; }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>>  8) & 0xFF;
                int b = (argb)        & 0xFF;
                int luma = (299 * r + 587 * g + 114 * b + 500) / 1000;
                out.setRGB(x, y, (a << 24) | (luma << 16) | (luma << 8) | luma);
            }
        }
        return out;
    }

    /**
     * Synthesizes the armor-plates template from a grayscale-desaturated iron ingot icon — the
     * plates part conceptually sits between the core and trim, so the ingot silhouette is the
     * natural placeholder for "an extra layer of metal applied to armor".
     */
    private static BufferedImage synthesizeArmorPlatesTemplate() throws IOException {
        return synthesizeArmorPartTemplate("item/iron_ingot");
    }

    /**
     * Synthesizes the armor-trim template from a grayscale-desaturated iron nugget icon — the
     * trim part is the smallest contribution to armor, mirrored by the nugget's small silhouette.
     */
    private static BufferedImage synthesizeArmorTrimTemplate() throws IOException {
        return synthesizeArmorPartTemplate("item/iron_nugget");
    }

    /**
     * Like {@link #synthesizeArmorPartTemplate} but rescales so the brightest opaque pixel lands
     * at 230 — needed for naturally dark vanilla sources (flint, brick) where a plain desaturate
     * would multiply every material tint toward black.
     */
    private static BufferedImage synthesizeNormalizedTemplate(String vanillaPath) throws IOException {
        BufferedImage out = synthesizeArmorPartTemplate(vanillaPath);
        int W = out.getWidth(), H = out.getHeight();
        int peak = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int argb = out.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) continue;
                peak = Math.max(peak, argb & 0xFF);
            }
        }
        if (peak == 0) return out;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int argb = out.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) continue;
                int luma = Math.min(255, (argb & 0xFF) * 230 / peak);
                out.setRGB(x, y, (a << 24) | (luma << 16) | (luma << 8) | luma);
            }
        }
        return out;
    }

    private static InputStream emitPng(BufferedImage img) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static BufferedImage synthesizeSimpleItemTexture(SimpleItem si) throws IOException {
        BufferedImage src = readTemplateTexture(si.source());
        int W = src.getWidth(), H = src.getHeight();
        int tintR = (si.tintArgb() >>> 16) & 0xFF;
        int tintG = (si.tintArgb() >>>  8) & 0xFF;
        int tintB = (si.tintArgb())        & 0xFF;
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) { out.setRGB(x, y, 0); continue; }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>>  8) & 0xFF;
                int b = (argb)        & 0xFF;
                int rOut = (r * tintR + 127) / 255;
                int gOut = (g * tintG + 127) / 255;
                int bOut = (b * tintB + 127) / 255;
                out.setRGB(x, y, (a << 24) | (rOut << 16) | (gOut << 8) | bOut);
            }
        }
        return out;
    }

    private static BufferedImage synthesizeRedSlimeTexture() throws IOException {
        BufferedImage src = readTemplateTexture(
                ResourceLocation.fromNamespaceAndPath("minecraft", "block/slime_block"));
        int W = src.getWidth(), H = src.getHeight();
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) { out.setRGB(x, y, 0); continue; }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8)  & 0xFF;
                int b = (argb)        & 0xFF;
                int luma = (299 * r + 587 * g + 114 * b + 500) / 1000;
                int rOut = Math.min(255, luma);
                int gOut = luma / 4;
                int bOut = luma / 4;
                out.setRGB(x, y, (a << 24) | (rOut << 16) | (gOut << 8) | bOut);
            }
        }
        return out;
    }

    private static BufferedImage rotateAroundCenter(BufferedImage src, double angleDegrees) {
        int W = src.getWidth(), H = src.getHeight();
        BufferedImage dst = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.awt.geom.AffineTransform tx = java.awt.geom.AffineTransform.getRotateInstance(
                Math.toRadians(angleDegrees), W / 2.0, H / 2.0);
        java.awt.Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setTransform(tx);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static void clearLowerHalf(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        int mid = H / 2;
        for (int y = mid; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, 0);
            }
        }
    }

    private static BufferedImage centeredUpperHalf(BufferedImage src) {
        int W = src.getWidth(), H = src.getHeight();
        int halfH = H / 2;
        int offsetY = (H - halfH) / 2;
        BufferedImage dst = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < halfH; y++) {
            for (int x = 0; x < W; x++) {
                dst.setRGB(x, y + offsetY, src.getRGB(x, y));
            }
        }
        return dst;
    }

    private static BufferedImage synthesizeToolSlotTextureFrame(String toolPath, int slotIndex,
                                                                 @Nullable String frameSuffix) throws IOException {
        ResourceLocation sourceId = switch (toolPath) {
            case "sword", "broadsword", "rapier"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_sword");
            case "paxel", "mining_hammer"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_pickaxe");
            case "crossbow"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/crossbow_standby");
            case "kama"    -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_hoe");
            case "cleaver" -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_axe");
            case "lumberaxe"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_axe");
            case "excavator"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_shovel");
            case "shuriken"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/nether_star");
            case "trident"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/trident");
            case "battlesign"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/oak_sign");
            case "pickaxe" -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_pickaxe");
            case "axe"     -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_axe");
            case "shovel"  -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_shovel");
            case "hoe"     -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_hoe");
            case "spear"   -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_spear");
            case "bow"     -> {
                String path = "item/bow" + (frameSuffix == null ? "" : "_" + frameSuffix);
                yield ResourceLocation.fromNamespaceAndPath("minecraft", path);
            }
            case "arrow"   -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/arrow");
            case "helmet", "chestplate", "leggings", "boots"
                           -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/iron_" + toolPath);
            default        -> null;
        };
        if (sourceId == null) {
            throw new IOException("No source-texture mapping for tool: " + toolPath);
        }
        BufferedImage source = readTemplateTexture(sourceId);
        int W = source.getWidth(), H = source.getHeight();
        PixelKind[][] kind = classifyPixels(source, W, H);

        boolean[][] mask = computeSlotMask(toolPath, slotIndex, kind, W, H);

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
        if (r > g && g > b && chroma > 24) return PixelKind.HANDLE;
        if (chroma < 40) return PixelKind.METAL;
        return (r > b + 8) ? PixelKind.HANDLE : PixelKind.METAL;
    }

    private static int desaturate(int argb, PixelKind kind) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8)  & 0xFF;
        int b = (argb)        & 0xFF;
        int y = (299 * r + 587 * g + 114 * b + 500) / 1000;
        if (kind == PixelKind.HANDLE) {
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
            // Placeholder layer split for the multi-part tools: primary metal mass on slot 0,
            // accent discs for the extra heads/plates, handle halves for the rods. Crude but
            // tintable; real per-slot art can replace the source silhouettes later.
            case "paxel" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1, 2, 3 -> headCenteredDisc(kind, W, H, 4 - slotIndex);
                    case 4 -> handleHalf(kind, W, H, true);
                    case 5 -> handleHalf(kind, W, H, false);
                    case 6 -> binder;
                    default -> new boolean[W][H];
                };
            }
            case "kama" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.HANDLE);
                    case 2 -> binder;
                    default -> new boolean[W][H];
                };
            }
            // Cleaver (blade/plate/handle/handle/binder), lumberaxe and excavator
            // (head/head/plate/handle/binder): primary mass + accent discs + handles.
            case "cleaver", "lumberaxe", "excavator" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> headCenteredDisc(kind, W, H, 4);
                    case 2 -> "cleaver".equals(toolPath)
                            ? handleHalf(kind, W, H, true)
                            : headCenteredDisc(kind, W, H, 3);
                    case 3 -> handleHalf(kind, W, H, "cleaver".equals(toolPath) ? false : true);
                    case 4 -> binder;
                    default -> new boolean[W][H];
                };
            }
            case "shuriken" -> {
                // Four blades = four quadrants of the star silhouette.
                int cx = W / 2, cy = H / 2;
                java.util.function.BiPredicate<Integer, Integer> opaque =
                        (x, y) -> kind[x][y] != PixelKind.TRANSPARENT;
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> opaque.test(x, y) && x <  cx && y <  cy);
                    case 1 -> maskWhere(W, H, (x, y) -> opaque.test(x, y) && x >= cx && y <  cy);
                    case 2 -> maskWhere(W, H, (x, y) -> opaque.test(x, y) && x <  cx && y >= cy);
                    case 3 -> maskWhere(W, H, (x, y) -> opaque.test(x, y) && x >= cx && y >= cy);
                    default -> new boolean[W][H];
                };
            }
            case "trident" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> headCenteredDisc(kind, W, H, 4);
                    case 2 -> headCenteredDisc(kind, W, H, 3);
                    case 3 -> handleHalf(kind, W, H, true);
                    case 4 -> handleHalf(kind, W, H, false);
                    case 5 -> binder;
                    default -> new boolean[W][H];
                };
            }
            case "battlesign" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.HANDLE);
                    case 2 -> binder;
                    default -> new boolean[W][H];
                };
            }
            case "crossbow" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.HANDLE);
                    case 2 -> binder;
                    case 3 -> opaqueOutline(kind, W, H);
                    default -> new boolean[W][H];
                };
            }
            case "mining_hammer" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> headCenteredDisc(kind, W, H, 4);
                    case 2 -> headCenteredDisc(kind, W, H, 3);
                    case 3 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.HANDLE);
                    case 4 -> binder;
                    default -> new boolean[W][H];
                };
            }
            case "broadsword", "rapier", "sword" -> {
                int span = (W - 1) + (H - 1);
                java.util.function.BiPredicate<Integer, Integer> inBladeHalf =
                        (x, y) -> (x + (H - 1 - y)) * 100 >= 30 * span;
                boolean[][] guardBand = metalBoundaryWithHandle(kind, W, H, inBladeHalf);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL
                                                          && inBladeHalf.test(x, y)
                                                          && !guardBand[x][y]);
                    case 1 -> guardBand;
                    case 2 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.HANDLE);
                    case 3 -> unionMasks(W, H,
                            maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL
                                                       && !inBladeHalf.test(x, y)),
                            centeredDisc(guardBand, W, H, 1));
                    default -> new boolean[W][H];
                };
            }
            case "pickaxe", "axe", "shovel", "hoe", "spear" -> {
                boolean[][] binder = headCenteredDisc(kind, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL && !binder[x][y]);
                    case 1 -> handleHalf(kind, W, H, true);
                    case 2 -> handleHalf(kind, W, H, false);
                    case 3 -> binder;
                    default -> new boolean[W][H];
                };
            }
            case "bow" -> {
                return switch (slotIndex) {
                    case 0 -> handleHalf(kind, W, H, true);
                    case 1 -> handleHalf(kind, W, H, false);
                    case 2 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL);
                    default -> new boolean[W][H];
                };
            }
            case "arrow" -> {
                int span = (W - 1) + (H - 1);
                java.util.function.BiPredicate<Integer, Integer> opaque =
                        (x, y) -> kind[x][y] != PixelKind.TRANSPARENT;
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> opaque.test(x, y)
                                                          && (x + (H - 1 - y)) * 100 >= 70 * span);
                    case 1 -> maskWhere(W, H, (x, y) -> opaque.test(x, y)
                                                          && (x + (H - 1 - y)) * 100 >= 25 * span
                                                          && (x + (H - 1 - y)) * 100 < 70 * span);
                    case 2 -> maskWhere(W, H, (x, y) -> opaque.test(x, y)
                                                          && (x + (H - 1 - y)) * 100 < 25 * span);
                    default -> new boolean[W][H];
                };
            }
            // Armor slots: 0=core (the body), 1=plates (outer rim), 2=trim (center accent) —
            // mirrors how the piece is assembled, so each part's material tint reads clearly.
            case "helmet", "chestplate", "leggings", "boots" -> {
                boolean[][] opaque = maskWhere(W, H, (x, y) -> kind[x][y] != PixelKind.TRANSPARENT);
                boolean[][] rim = opaqueOutline(kind, W, H);
                boolean[][] trim = centeredDisc(opaque, W, H, 2);
                return switch (slotIndex) {
                    case 0 -> maskWhere(W, H, (x, y) -> opaque[x][y] && !rim[x][y] && !trim[x][y]);
                    case 1 -> rim;
                    case 2 -> maskWhere(W, H, (x, y) -> trim[x][y] && opaque[x][y]);
                    default -> new boolean[W][H];
                };
            }
        }
        return new boolean[W][H];
    }

    /** Opaque pixels touching transparency or the image border — the piece's outline. */
    private static boolean[][] opaqueOutline(PixelKind[][] kind, int W, int H) {
        boolean[][] m = new boolean[W][H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (kind[x][y] == PixelKind.TRANSPARENT) continue;
                boolean edge = x == 0 || y == 0 || x == W - 1 || y == H - 1
                        || kind[x - 1][y] == PixelKind.TRANSPARENT
                        || kind[x + 1][y] == PixelKind.TRANSPARENT
                        || kind[x][y - 1] == PixelKind.TRANSPARENT
                        || kind[x][y + 1] == PixelKind.TRANSPARENT;
                if (edge) m[x][y] = true;
            }
        }
        return m;
    }

    private static boolean[][] unionMasks(int W, int H, boolean[][] a, boolean[][] b) {
        boolean[][] m = new boolean[W][H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) m[x][y] = a[x][y] || b[x][y];
        return m;
    }

    @FunctionalInterface
    private interface XY { boolean test(int x, int y); }

    private static boolean[][] maskWhere(int W, int H, XY p) {
        boolean[][] m = new boolean[W][H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) m[x][y] = p.test(x, y);
        return m;
    }

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

    private static boolean[][] headCenteredDisc(PixelKind[][] kind, int W, int H, int radius) {
        int upperY = Math.max(1, H / 3);
        long sx = 0, sy = 0, n = 0;
        for (int y = 0; y < upperY; y++) for (int x = 0; x < W; x++) {
            if (kind[x][y] == PixelKind.METAL) { sx += x; sy += y; n++; }
        }
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
        ResourceLocation tex = pt.textureTemplate();
        return """
                {
                  "parent": "item/generated",
                  "textures": {
                    "layer0": "%s"
                  }
                }
                """.formatted(tex);
    }

    private static String buildToolModelJson(ToolType tt) {
        return buildToolModelJsonFrame(tt, null);
    }

    /**
     * Builds a layered {@code item/handheld}-family model for a tool, one texture layer per
     * composition slot. Layer index doubles as the tint index the client's item color handler
     * receives, which is how each slot gets its own material color.
     *
     * <p>The base bow model additionally carries vanilla-style {@code overrides} with
     * {@code pulling}/{@code pull} predicates so the draw animation swaps to the generated
     * {@code bow_pulling_N} frame models.
     */
    private static String buildToolModelJsonFrame(ToolType tt, @Nullable String frameSuffix) {
        StringBuilder sb = new StringBuilder(512);
        boolean isBow = "bow".equals(tt.id().getPath());
        sb.append("{\n");
        sb.append("  \"parent\": \"").append(isBow ? "item/bow" : "item/handheld").append("\",\n");
        sb.append("  \"textures\": {\n");
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            String partPath = slots.get(i).partType().id().getPath();
            sb.append("    \"layer").append(i).append("\": \"")
              .append(Smithery.MODID).append(":item/tool/")
              .append(tt.id().getPath()).append("/")
              .append(i).append("_").append(partPath);
            if (frameSuffix != null) sb.append("_").append(frameSuffix);
            sb.append("\"");
            if (i < slots.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        if (isBow && frameSuffix == null) {
            sb.append(",\n");
            sb.append("  \"overrides\": [\n");
            sb.append("    { \"predicate\": { \"pulling\": 1 }, \"model\": \"")
              .append(Smithery.MODID).append(":item/bow_pulling_0\" },\n");
            sb.append("    { \"predicate\": { \"pulling\": 1, \"pull\": 0.65 }, \"model\": \"")
              .append(Smithery.MODID).append(":item/bow_pulling_1\" },\n");
            sb.append("    { \"predicate\": { \"pulling\": 1, \"pull\": 0.9 }, \"model\": \"")
              .append(Smithery.MODID).append(":item/bow_pulling_2\" }\n");
            sb.append("  ]");
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private static String buildMoltenBlockstateJson(String blockName) {
        return """
                {
                  "variants": {
                    "": { "model": "%s:block/%s" }
                  }
                }
                """.formatted(Smithery.MODID, blockName);
    }

    private static String buildMoltenBlockModelJson() {
        return """
                {
                  "textures": {
                    "particle": "%s:block/molten_still"
                  }
                }
                """.formatted(Smithery.MODID);
    }

    private static IoSupplier<InputStream> jsonStream(String content) {
        return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
