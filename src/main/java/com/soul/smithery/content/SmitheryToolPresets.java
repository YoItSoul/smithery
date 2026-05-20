package com.soul.smithery.content;

import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience factories for ToolComposition. Used by the creative-tab tool tab so players can
 * spawn example tools before recipe support exists; modders can also use these to /give tools
 * during testing.
 */
public final class SmitheryToolPresets {

    /** All slots filled with the same material. */
    public static ToolComposition uniform(ToolType tt, Identifier materialId) {
        List<Identifier> mats = new ArrayList<>(tt.slots().size());
        for (int i = 0; i < tt.slots().size(); i++) mats.add(materialId);
        return new ToolComposition(tt.id(), mats);
    }

    /** Convenience: uniform iron tool. */
    public static ToolComposition iron(ToolType tt) {
        return uniform(tt, SmitheryMaterials.IRON);
    }

    private SmitheryToolPresets() {}
}
