package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryToolData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/**
 * Smithery trident: three spear heads on a two-handle haft. Extends vanilla
 * {@link TridentItem} so the charge/throw/return pipeline (and the vanilla thrown-trident
 * rendering) runs unchanged. Melee stats ride the composition's ATTRIBUTE_MODIFIERS like every
 * other smithery tool; Loyalty/Riptide arrive later as apply_enchantment modifiers if wanted.
 */
public class SmitheryTridentItem extends TridentItem {

    private final ResourceLocation toolTypeId;

    /**
     * Constructs the trident item bound to the given smithery ToolType id.
     */
    public SmitheryTridentItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:trident}). */
    public ResourceLocation toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this trident item, or null if unregistered. */
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        ToolType tt = toolType();
        if (comp == null || !comp.isValid() || tt == null) {
            return Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId));
        }
        ResourceLocation primaryMat = primaryAdditiveMaterial(tt, comp);
        Component matName = primaryMat != null
                ? Component.translatable(PartItem.materialTranslationKey(primaryMat))
                : Component.literal("");
        return Component.translatable("item." + Smithery.MODID + ".part_combo",
                matName,
                Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId)));
    }

    private static ResourceLocation primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }
}
