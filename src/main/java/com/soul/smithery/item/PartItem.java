package com.soul.smithery.item;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.ToolType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * A tool part: a slab of one Material shaped as one PartType.
 *
 * Auto-registered for every (Material × PartType) combination. The model is dynamically
 * served by SmitheryGeneratedPack (one model JSON per combo, pointing at the PartType's
 * shared grayscale template texture). Color tinting happens at render time via
 * RegisterColorHandlersEvent.Item, using the Material's partColor field.
 */
public class PartItem extends Item {
    private final Identifier materialId;
    private final Identifier partTypeId;

    public PartItem(Properties properties, Identifier materialId, Identifier partTypeId) {
        super(properties);
        this.materialId = materialId;
        this.partTypeId = partTypeId;
    }

    public Identifier materialId() { return materialId; }
    public Identifier partTypeId() { return partTypeId; }

    public Material material() { return SmitheryAPI.MATERIALS.get(materialId); }
    public PartType partType() { return SmitheryAPI.PART_TYPES.get(partTypeId); }

    /** Returns the part color used for client-side ItemColor tinting (layer 0). */
    public int tintColor() {
        Material m = material();
        return m != null ? m.stats().partColor() : 0xFFFFFFFF;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
                "item." + Smithery.MODID + ".part_combo",
                Component.translatable(materialTranslationKey(materialId)),
                Component.translatable(partTranslationKey(partTypeId))
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Material mat = material();
        PartType pt = partType();
        if (mat != null && pt != null) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".part.harvest_level",
                    mat.stats().harvestLevel()).withStyle(ChatFormatting.GRAY));
            int slots = mat.stats().modifierSlotsFor(pt);
            if (slots > 0) {
                tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".part.modifier_slots",
                        slots).withStyle(ChatFormatting.GRAY));
            }

            // For each tool type that uses this part, list the modifiers the material
            // contributes to that tool type. Tool types with no contribution are omitted.
            for (ToolType tt : SmitheryAPI.toolTypesUsingPart(pt)) {
                List<ModifierEffect> effects = mat.stats().modifiersFor(tt);
                if (effects.isEmpty()) continue;

                MutableComponent line = Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.in_tool",
                        Component.translatable(toolTypeTranslationKey(tt.id()))
                ).withStyle(ChatFormatting.DARK_GRAY);
                tooltip.accept(line);

                for (ModifierEffect effect : effects) {
                    tooltip.accept(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable(modifierTranslationKey(effect.modifierId()))
                                    .withStyle(ChatFormatting.AQUA)));
                }
            }
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    public static String materialTranslationKey(Identifier materialId) {
        return Smithery.MODID + ".material." + materialId.getNamespace() + "." + materialId.getPath();
    }

    public static String partTranslationKey(Identifier partTypeId) {
        return Smithery.MODID + ".part." + partTypeId.getNamespace() + "." + partTypeId.getPath();
    }

    public static String toolTypeTranslationKey(Identifier toolTypeId) {
        return Smithery.MODID + ".tool." + toolTypeId.getNamespace() + "." + toolTypeId.getPath();
    }

    public static String modifierTranslationKey(Identifier modifierId) {
        return Smithery.MODID + ".modifier." + modifierId.getNamespace() + "." + modifierId.getPath();
    }
}
