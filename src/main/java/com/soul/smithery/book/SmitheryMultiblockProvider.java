package com.soul.smithery.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.klikli_dev.modonomicon.api.datagen.MultiblockProvider;
import com.soul.smithery.Smithery;
import net.minecraft.data.PackOutput;

/**
 * Datagen for the field guide's multiblock previews. The small demo forge renders as a
 * rotating 3D model on the book's multiblock page and can be ghost-projected into the world
 * with the page's anchor button — the single best "how do I build this" teaching tool.
 */
public class SmitheryMultiblockProvider extends MultiblockProvider {

    public SmitheryMultiblockProvider(PackOutput packOutput) {
        super(packOutput, Smithery.MODID);
    }

    @Override
    public void buildMultiblocks() {
        add(modLoc("forge_small"), denseForge());
    }

    /**
     * Smallest legal forge: 3x3x3 shell with a single interior air block, open top, one
     * controller, one fuel port, one drain. Layers are listed top to bottom.
     *
     * <p>Built-in mapping chars (from DenseMultiblock's parser): {@code ' '} = air,
     * {@code '_'} = any block, {@code '0'} = the center anchor (air). Explicit mapping
     * values must be matcher objects with a {@code type} — plain strings fail to parse.
     */
    private JsonObject denseForge() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "modonomicon:dense");

        JsonArray pattern = new JsonArray();
        pattern.add(layer("###", "# #", "###"));   // rim, open top
        pattern.add(layer("#F#", "#0#", "#C#"));   // fuel port north, controller south, interior center
        pattern.add(layer("###", "#D#", "###"));   // floor with drain center
        json.add("pattern", pattern);

        JsonObject mapping = new JsonObject();
        mapping.add("#", blockMatcher("smithery:furnace_bricks"));
        mapping.add("F", blockMatcher("smithery:forge_fuel_port"));
        mapping.add("C", blockMatcher("smithery:forge_controller"));
        mapping.add("D", blockMatcher("smithery:forge_drain"));
        json.add("mapping", mapping);

        return json;
    }

    private static JsonObject blockMatcher(String blockId) {
        JsonObject matcher = new JsonObject();
        matcher.addProperty("type", "modonomicon:block");
        matcher.addProperty("block", blockId);
        return matcher;
    }

    private static JsonArray layer(String... rows) {
        JsonArray layer = new JsonArray();
        for (String row : rows) layer.add(row);
        return layer;
    }
}
