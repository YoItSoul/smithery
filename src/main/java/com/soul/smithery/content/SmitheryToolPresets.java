package com.soul.smithery.content;

import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.tool.ToolComposition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory helpers for {@link ToolComposition} instances used by creative tabs and tests.
 */
public final class SmitheryToolPresets {

    /**
     * Builds a composition with every slot of the given tool type filled by the same material.
     *
     * @param tt         the tool type to compose
     * @param materialId material identifier applied to every slot
     * @return a uniform-material composition for {@code tt}
     */
    public static ToolComposition uniform(ToolType tt, ResourceLocation materialId) {
        List<ResourceLocation> mats = new ArrayList<>(tt.slots().size());
        for (int i = 0; i < tt.slots().size(); i++) mats.add(materialId);
        return new ToolComposition(tt.id(), mats);
    }

    /**
     * Convenience wrapper that calls {@link #uniform(ToolType, ResourceLocation)} with iron.
     *
     * @param tt the tool type to compose in iron
     * @return an all-iron composition for {@code tt}
     */
    public static ToolComposition iron(ToolType tt) {
        return uniform(tt, SmitheryMaterials.IRON);
    }

    private SmitheryToolPresets() {}
}
