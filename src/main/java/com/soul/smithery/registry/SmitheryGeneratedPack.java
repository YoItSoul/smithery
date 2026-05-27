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

    /**
     * Prefix for synthesized PartType template textures. Static part PNGs live under the same
     * directory in {@code src/main/resources}; when a request comes in for one of the names
     * listed in {@link #SYNTHESIZED_PART_TEMPLATES} we generate it on the fly from a vanilla
     * (or other smithery) source instead. Static PNGs in the resources folder take precedence
     * because vanilla resource loading checks earlier packs first.
     */
    private static final String PART_TEMPLATE_TEX_PREFIX = "textures/item/part/";

    /** Path prefix for synthesized block textures (currently just red_slime_block). */
    private static final String BLOCK_TEX_PREFIX = "textures/block/";
    /** Block id whose textures + model + blockstate + item def are runtime-synthesized. */
    private static final String RED_SLIME_BLOCK = "red_slime_block";

    /** Path prefix for synthesized "simple item" textures (the bowstring-class crafting items). */
    private static final String SIMPLE_ITEM_TEX_PREFIX = "textures/item/";

    /**
     * Synthesized "simple items" — flat smithery items (not parts, not tools, not blocks) that
     * need a model + item-def + texture but don't fit either the part-material grid or the
     * layered-tool pipeline. Each entry pairs a vanilla source texture with a tint color;
     * synthesis multiplies the source pixel by the tint to produce a recolored variant.
     */
    private record SimpleItem(Identifier source, int tintArgb) {}

    private static final java.util.Map<String, SimpleItem> SIMPLE_ITEMS;
    static {
        Identifier vanillaString    = Identifier.fromNamespaceAndPath("minecraft", "item/string");
        Identifier vanillaSlimeBall = Identifier.fromNamespaceAndPath("minecraft", "item/slime_ball");
        // Tints are full-alpha so the multiply by source preserves the source's own alpha.
        // Kelp string tiers progress in saturation: tier 1 is muted (early weave), tier 3
        // approaches the finished kelp_string green.
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

        // Synthesized PartType template textures (bow_limb / bowstring / arrow_shaft). Each
        // derives from another texture via a small transformation (rotate, crop, copy). Used
        // both for the part item icons and the impressed-sand voxelizer.
        if (path.endsWith(PNG_SUFFIX) && path.startsWith(PART_TEMPLATE_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            String partName = path.substring(PART_TEMPLATE_TEX_PREFIX.length(),
                    path.length() - PNG_SUFFIX.length());
            return resolveSynthesizedPartTemplate(partName);
        }

        // Red slime block texture — vanilla slime_block.png recolored toward red while
        // preserving the alpha channel and per-pixel detail (luminance-driven tint).
        if (path.endsWith(PNG_SUFFIX) && path.startsWith(BLOCK_TEX_PREFIX)
                && Smithery.MODID.equals(namespace)) {
            String blockName = path.substring(BLOCK_TEX_PREFIX.length(),
                    path.length() - PNG_SUFFIX.length());
            if (RED_SLIME_BLOCK.equals(blockName)) {
                return () -> emitPng(synthesizeRedSlimeTexture());
            }
        }

        // Simple-item textures — synthesized from a vanilla source + multiplicative tint. The
        // namespace check + lookup against SIMPLE_ITEMS naturally excludes part textures (which
        // also live under textures/item/ but resolve through different paths above).
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

                // Bow gets three extra layered model JSONs for the draw animation: bow_pulling_0/1/2.
                // The item def's range_dispatch references these by name. Each is layered with
                // the same number of slots as the base bow.
                boolean isBow = "bow".equals(path);
                int frameCount = isBow ? 3 : 0;
                for (int frame = 0; frame < frameCount; frame++) {
                    String pullName = "bow_pulling_" + frame;
                    final String capturedPull = pullName;
                    emitIfMatches(namespace, MODELS_PREFIX + pullName + JSON_SUFFIX, prefix,
                            () -> resolveModelJson(namespace, capturedPull), output);
                }

                // Per-slot layer textures (synthesized from vanilla iron tool sources).
                // Must be advertised here — the item-sprite atlas builder discovers candidate
                // PNGs via listResources, not via direct getResource calls. Without this,
                // the atlas has no entry for our synthesized textures and tools render
                // with the missing-texture placeholder per layer.
                //
                // For the bow, also advertise the per-frame variants (slot × pulling_0/1/2).
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

        // Synthesized PartType template textures (advertise so the atlas + voxelizer find them).
        // Static PNGs in src/main/resources for the other parts continue to win because
        // resource loading walks packs in order — these only fire when no static file exists.
        if (Smithery.MODID.equals(namespace)) {
            String[] synthParts = { "bow_limb", "bowstring", "arrow_shaft", "fletching" };
            for (String partName : synthParts) {
                String pngPath = PART_TEMPLATE_TEX_PREFIX + partName + PNG_SUFFIX;
                final String captured = partName;
                emitIfMatches(namespace, pngPath, prefix,
                        () -> resolveSynthesizedPartTemplate(captured), output);
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

        // Red slime block: blockstate + block model + item model + item def + texture.
        if (Smithery.MODID.equals(namespace)) {
            String name = RED_SLIME_BLOCK;
            emitIfMatches(namespace, BLOCKSTATES_PREFIX  + name + JSON_SUFFIX, prefix,
                    () -> resolveBlockstateJson(namespace, name), output);
            emitIfMatches(namespace, BLOCK_MODELS_PREFIX + name + JSON_SUFFIX, prefix,
                    () -> resolveBlockModelJson(namespace, name), output);
            emitIfMatches(namespace, MODELS_PREFIX       + name + JSON_SUFFIX, prefix,
                    () -> resolveModelJson(namespace, name), output);
            emitIfMatches(namespace, ITEMS_PREFIX        + name + JSON_SUFFIX, prefix,
                    () -> resolveItemDefJson(namespace, name), output);
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

        // Smithery simple items: each entry advertises model + item-def + texture so the
        // sprite atlas + model loader pick them up.
        if (Smithery.MODID.equals(namespace)) {
            for (var entry : SIMPLE_ITEMS.entrySet()) {
                String name = entry.getKey();
                final SimpleItem captured = entry.getValue();
                emitIfMatches(namespace, MODELS_PREFIX       + name + JSON_SUFFIX, prefix,
                        () -> resolveModelJson(namespace, name), output);
                emitIfMatches(namespace, ITEMS_PREFIX        + name + JSON_SUFFIX, prefix,
                        () -> resolveItemDefJson(namespace, name), output);
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
        output.accept(Identifier.fromNamespaceAndPath(namespace, fullPath), content);
    }

    /** Advertises one synthesized tool-layer PNG path to the item-sprite atlas. */
    private void emitToolLayerTexture(String namespace, String texName, String requestedPrefix,
                                       ResourceOutput output) {
        String pngPath = TOOL_LAYER_TEX_PREFIX + texName + PNG_SUFFIX;
        final String captured = pngPath;
        emitIfMatches(namespace, pngPath, requestedPrefix,
                () -> resolveToolSlotTexture(captured), output);
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
                // Bow draw-state model variants: "bow_pulling_0" / "bow_pulling_1" / "bow_pulling_2".
                // Each is a layered model identical to "bow" except texture paths reference the
                // corresponding pulling-frame slot textures.
                if ("bow".equals(tt.id().getPath())) {
                    for (int frame = 0; frame < 3; frame++) {
                        String pullName = "bow_pulling_" + frame;
                        if (pullName.equals(itemName)) {
                            return jsonStream(buildToolModelJsonFrame(tt, pullName.substring(4))); // "pulling_N"
                        }
                    }
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
            // Red slime block: item model parents the block model so the inventory icon
            // shows the same translucent geometry as the placed block.
            if (RED_SLIME_BLOCK.equals(itemName)) {
                return jsonStream(("""
                        {
                          "parent": "%s:block/%s"
                        }
                        """).formatted(Smithery.MODID, itemName));
            }
            // Smithery simple items — flat item/generated model layered on the synthesized
            // texture. layer0 references textures/item/<name>.png; that PNG is produced by
            // the texture-synthesis branch in getResource above.
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
            // Red slime block item def — references the item model that parents the block model.
            if (RED_SLIME_BLOCK.equals(itemName)) {
                return jsonStream(("""
                        {
                          "model": {
                            "type": "minecraft:model",
                            "model": "%s:item/%s"
                          }
                        }
                        """).formatted(Smithery.MODID, itemName));
            }
            // Smithery simple items — item def points at the generated item model. No tints
            // (the synthesis baked the tint into the texture directly).
            if (SIMPLE_ITEMS.containsKey(itemName)) {
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
            // Parents vanilla's slime_block model (inner+outer cube geometry, translucent
            // rendering) and overrides only the texture lookups so we inherit every behaviour
            // automatically. Pointing both `particle` and `texture` keys at the synthesized
            // red PNG is what vanilla does too.
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
    //   Pickaxe-family (pickaxe / axe / shovel / hoe / spear, each from its vanilla iron tool):
    //     slot 0 <head>      : METAL pixels except the binder disc
    //     slot 1 handle (main): HANDLE pixels in the upper half of the shaft
    //     slot 2 handle (fore): HANDLE pixels in the lower half of the shaft
    //     slot 3 binder      : small disc at the head's centroid
    //
    // Output pixels are desaturated to luminance + alpha so each layer's slot tint
    // (smithery:tool_slot_material) multiplies cleanly into the slot's partColor.

    private enum PixelKind { TRANSPARENT, METAL, HANDLE }

    /**
     * Texture path format: {@code textures/item/tool/<tool>/<slotIndex>_<partTypePath>[_<frame>].png}
     *
     * The optional {@code _<frame>} tail (currently only {@code _pulling_0|1|2} for bows) picks
     * which source frame to synthesize from. Static tools omit it and use the single source PNG.
     */
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
        // Detect optional frame suffix on the form "<slot>_<partpath>_<frame>". The first
        // underscore after the slot index introduces the part path; any further underscore-
        // separated tail starting with "pulling_" is treated as the frame suffix.
        String partAndFrame = slotPart.substring(underscore + 1);
        String frameSuffix = null;
        int pullingIdx = partAndFrame.lastIndexOf("_pulling_");
        if (pullingIdx > 0) {
            // partAndFrame e.g. "bow_limb_pulling_2" → frameSuffix "pulling_2"
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

    private static BufferedImage synthesizeToolSlotTexture(String toolPath, int slotIndex) throws IOException {
        return synthesizeToolSlotTextureFrame(toolPath, slotIndex, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  Synthesized PartType template textures (bow_limb / bowstring / arrow_shaft)
    // ═══════════════════════════════════════════════════════════════════════════════════════
    //
    // Returns an IoSupplier producing the PNG bytes for one of three derived textures, or null
    // for any other partName (in which case the resource loader keeps walking other packs to
    // find a static file). Each entry pairs the part path with a source texture + transform.

    private @Nullable IoSupplier<InputStream> resolveSynthesizedPartTemplate(String partName) {
        return switch (partName) {
            case "bow_limb"    -> () -> emitPng(synthesizeBowLimbTemplate());
            case "bowstring"   -> () -> emitPng(synthesizeBowstringTemplate());
            case "arrow_shaft" -> () -> emitPng(synthesizeArrowShaftTemplate());
            case "fletching"   -> () -> emitPng(synthesizeFletchingTemplate());
            default            -> null;
        };
    }

    /** Vanilla string copied verbatim as the bowstring template. */
    private static BufferedImage synthesizeBowstringTemplate() throws IOException {
        return readTemplateTexture(Identifier.fromNamespaceAndPath("minecraft", "item/string"));
    }

    /**
     * Bow limb: pick_head rotated -45° (45° counter-clockwise) around its center, then
     * upper-half cropped (lower half cleared to fully transparent). The shallower diagonal
     * curve reads as one half of a bow's arc.
     */
    private static BufferedImage synthesizeBowLimbTemplate() throws IOException {
        BufferedImage src = readTemplateTexture(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "item/part/pick_head"));
        BufferedImage rotated = rotateAroundCenter(src, -45.0);
        clearLowerHalf(rotated);
        return rotated;
    }

    /**
     * Arrow shaft: handle cropped to its upper half and re-centered vertically. Without the
     * recenter step the stick sits flush against the top of the 16×16 frame which reads as
     * "off-center" — centering shifts it down by H/4 so it sits in the middle of the icon.
     */
    private static BufferedImage synthesizeArrowShaftTemplate() throws IOException {
        BufferedImage src = readTemplateTexture(
                Identifier.fromNamespaceAndPath(Smithery.MODID, "item/part/handle"));
        return centeredUpperHalf(src);
    }

    /**
     * Fletching: vanilla feather cropped to its upper half and re-centered vertically. Same
     * centering treatment as arrow_shaft so the half-feather doesn't hug the top of the icon.
     */
    private static BufferedImage synthesizeFletchingTemplate() throws IOException {
        BufferedImage src = readTemplateTexture(
                Identifier.fromNamespaceAndPath("minecraft", "item/feather"));
        return centeredUpperHalf(src);
    }

    /** Encode a BufferedImage as a PNG byte stream. */
    private static InputStream emitPng(BufferedImage img) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Simple-item texture synthesis: pixelwise multiply of the vanilla source by the tint
     * (treating each channel as a normalized 0..1 multiplier). Alpha is preserved from the
     * source so transparent regions stay transparent. Source's per-pixel brightness drives
     * the result intensity — white pixels become full tint, mid-grays become muted tint,
     * blacks stay black.
     */
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

    /**
     * Red slime block texture: vanilla slime_block recolored toward red. Each pixel is
     * collapsed to its luminance (preserving the per-pixel detail in the source), then
     * remapped into a red-dominant tint:
     *   R = Y, G = Y/4, B = Y/4
     * The alpha channel is preserved so the translucent outer shell still reads as
     * see-through. Result: a red slime block silhouette with the same fine detail and
     * transparency profile as vanilla.
     */
    private static BufferedImage synthesizeRedSlimeTexture() throws IOException {
        BufferedImage src = readTemplateTexture(
                Identifier.fromNamespaceAndPath("minecraft", "block/slime_block"));
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

    /** Rotates an ARGB image 90° clockwise. New width = old height, new height = old width. */
    private static BufferedImage rotate90Clockwise(BufferedImage src) {
        int W = src.getWidth(), H = src.getHeight();
        BufferedImage dst = new BufferedImage(H, W, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                // (x, y) → (H-1-y, x)
                dst.setRGB(H - 1 - y, x, src.getRGB(x, y));
            }
        }
        return dst;
    }

    /**
     * Rotates an ARGB image around its geometric center by an arbitrary angle (degrees,
     * positive = clockwise). Output is the same dimensions as the input; pixels that rotate
     * out of bounds are clipped. Uses nearest-neighbor interpolation to keep the pixelated
     * Minecraft look — bilinear/bicubic would smear the silhouette into a fuzzy ghost.
     */
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

    /** Clears the lower half of an image to fully transparent — pixels at y ≥ H/2 → 0. */
    private static void clearLowerHalf(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        int mid = H / 2;
        for (int y = mid; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, 0);
            }
        }
    }

    /**
     * Copies the upper half of {@code src} (rows {@code 0..H/2-1}) into a fresh ARGB canvas
     * of the same dimensions, offset down by {@code H/4} so the cropped content sits centered
     * vertically. Used by arrow_shaft and fletching — without this the cropped silhouette
     * hugs the top of the 16×16 frame and reads as off-center.
     */
    private static BufferedImage centeredUpperHalf(BufferedImage src) {
        int W = src.getWidth(), H = src.getHeight();
        int halfH = H / 2;
        int offsetY = (H - halfH) / 2;          // shift down by H/4 to center vertically
        BufferedImage dst = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < halfH; y++) {
            for (int x = 0; x < W; x++) {
                dst.setRGB(x, y + offsetY, src.getRGB(x, y));
            }
        }
        return dst;
    }

    /**
     * Source-texture mapping + per-frame variant for animated tools (bow). For static tools
     * (sword, pickaxe, axe, shovel, hoe, spear, arrow) {@code frameSuffix} is null and the
     * single source PNG is used. For the bow, callers pass {@code "pulling_0"},
     * {@code "pulling_1"}, or {@code "pulling_2"} to pick up the matching draw-frame source.
     */
    private static BufferedImage synthesizeToolSlotTextureFrame(String toolPath, int slotIndex,
                                                                 @Nullable String frameSuffix) throws IOException {
        // Source-texture mapping: every tool path maps to a vanilla equivalent whose silhouette
        // we slice into per-slot masks. Modders adding new tool paths can extend this switch
        // (or override the generated PNG with a static one in their own resource pack).
        Identifier sourceId = switch (toolPath) {
            case "sword"   -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_sword");
            case "pickaxe" -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_pickaxe");
            case "axe"     -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_axe");
            case "shovel"  -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_shovel");
            case "hoe"     -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_hoe");
            case "spear"   -> Identifier.fromNamespaceAndPath("minecraft", "item/iron_spear");
            case "bow"     -> {
                // Bow has four source frames: bow.png (rested) + bow_pulling_0/1/2.png (drawing).
                // frameSuffix picks the right one; null = rested.
                String path = "item/bow" + (frameSuffix == null ? "" : "_" + frameSuffix);
                yield Identifier.fromNamespaceAndPath("minecraft", path);
            }
            case "arrow"   -> Identifier.fromNamespaceAndPath("minecraft", "item/arrow");
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
            // Pickaxe-family tools (head + 2×handle + binder) all share the same slot layout:
            //   0 = head (METAL pixels minus the binder disc)
            //   1 = upper handle (HANDLE pixels above the handle centroid)
            //   2 = lower handle (HANDLE pixels at/below the handle centroid)
            //   3 = binder      (small METAL disc centered on the head)
            // Axe / shovel / hoe / spear / pickaxe all map cleanly onto this — the per-tool
            // source texture switch above determines silhouette; the mask is the same shape.
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
            // Bow: 3 slots — upper limb, lower limb, bowstring.
            //   Vanilla bow.png runs roughly top-left → bottom-right, with the bowstring as the
            //   thin straight line cutting across. We split the opaque pixels into:
            //     0 = upper limb (HANDLE pixels above the bow centroid Y)
            //     1 = lower limb (HANDLE pixels at/below the bow centroid Y)
            //     2 = bowstring  (METAL/non-HANDLE pixels — the string is grey/silver in the
            //                     source so it falls into the metal bucket of the classifier)
            //   Works across bow.png and bow_pulling_0/1/2.png because the centroid Y stays
            //   roughly the same as the bow flexes.
            case "bow" -> {
                return switch (slotIndex) {
                    case 0 -> handleHalf(kind, W, H, true);
                    case 1 -> handleHalf(kind, W, H, false);
                    case 2 -> maskWhere(W, H, (x, y) -> kind[x][y] == PixelKind.METAL);
                    default -> new boolean[W][H];
                };
            }
            // Arrow: 3 slots — head, shaft, fletching. Vanilla arrow.png runs from bottom-left
            // (fletching) to top-right (head tip). We split by anti-diagonal progress:
            //   progress(x,y) = (x + (H-1-y)) / (W-1 + H-1) ∈ [0,1]
            //     ≥ 0.7  → arrow_head (top-right ~30%)
            //     ≥ 0.25 → arrow_shaft (middle ~45%)
            //     else   → fletching (bottom-left ~25%)
            // Operates on opaque pixels only (TRANSPARENT excluded).
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
        return buildToolModelJsonFrame(tt, null);
    }

    /**
     * Builds a layered model JSON for the given tool type and optional pulling frame. When
     * {@code frameSuffix} is null the texture paths reference the base
     * {@code tool/<tool>/<slot>_<part>}; when non-null (e.g. {@code "pulling_0"}) they
     * reference {@code tool/<tool>/<slot>_<part>_<frameSuffix>}. Used for the bow's draw
     * animation: one model JSON per (bow × pull state), all sharing the same per-slot tint
     * indices so material colors stay consistent across frames.
     *
     * <p>Bow uses {@code item/bow} as its parent instead of {@code item/handheld} so vanilla's
     * cherry-picked bow display rotation kicks in.
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
        // Bows get the vanilla predicate-driven model swap so the draw animation plays. Three
        // pulling states wrap the base "rested" model via a using_item-conditional + a
        // use_duration range_dispatch — same structure as vanilla bow.json.
        if ("bow".equals(tt.id().getPath())) {
            return buildBowItemDefJson(tt);
        }
        return buildStaticToolItemDefJson(tt, tt.id().getPath());
    }

    private static String buildStaticToolItemDefJson(ToolType tt, String modelName) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        sb.append("  \"model\": {\n");
        appendLayeredModelBlock(sb, tt, modelName, "    ");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Bow item def: nested condition (using_item?) → range_dispatch on use_duration → one of
     * three pulling-frame models, with the rested model as the false branch. Each nested model
     * has its own tints array (one tool_slot_material per slot), so material tinting tracks the
     * draw frames correctly.
     */
    private static String buildBowItemDefJson(ToolType tt) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\n");
        sb.append("  \"model\": {\n");
        sb.append("    \"type\": \"minecraft:condition\",\n");
        sb.append("    \"property\": \"minecraft:using_item\",\n");
        sb.append("    \"on_false\": {\n");
        appendLayeredModelBlock(sb, tt, "bow", "      ");
        sb.append("    },\n");
        sb.append("    \"on_true\": {\n");
        sb.append("      \"type\": \"minecraft:range_dispatch\",\n");
        sb.append("      \"property\": \"minecraft:use_duration\",\n");
        sb.append("      \"scale\": 0.05,\n");
        sb.append("      \"fallback\": {\n");
        appendLayeredModelBlock(sb, tt, "bow_pulling_0", "        ");
        sb.append("      },\n");
        sb.append("      \"entries\": [\n");
        sb.append("        {\n");
        sb.append("          \"threshold\": 0.65,\n");
        sb.append("          \"model\": {\n");
        appendLayeredModelBlock(sb, tt, "bow_pulling_1", "            ");
        sb.append("          }\n");
        sb.append("        },\n");
        sb.append("        {\n");
        sb.append("          \"threshold\": 0.9,\n");
        sb.append("          \"model\": {\n");
        appendLayeredModelBlock(sb, tt, "bow_pulling_2", "            ");
        sb.append("          }\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Emits a {@code minecraft:model} block (type + model path + tints array) at the given
     * indent. Shared between the static-tool path and the bow pulling-state nested models.
     */
    private static void appendLayeredModelBlock(StringBuilder sb, ToolType tt, String modelName, String indent) {
        sb.append(indent).append("\"type\": \"minecraft:model\",\n");
        sb.append(indent).append("\"model\": \"").append(Smithery.MODID).append(":item/")
          .append(modelName).append("\",\n");
        sb.append(indent).append("\"tints\": [\n");
        int n = tt.slots().size();
        for (int i = 0; i < n; i++) {
            sb.append(indent).append("  { \"type\": \"smithery:tool_slot_material\", \"slot\": ").append(i).append(" }");
            if (i < n - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(indent).append("]\n");
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
