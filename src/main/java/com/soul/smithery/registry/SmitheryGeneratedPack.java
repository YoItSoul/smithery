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

import java.io.ByteArrayInputStream;
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

    private static final String MODELS_PREFIX = "models/item/";
    private static final String ITEMS_PREFIX  = "items/";
    private static final String JSON_SUFFIX   = ".json";

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

    private static IoSupplier<InputStream> jsonStream(String content) {
        return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
