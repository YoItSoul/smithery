package com.soul.smithery.item;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.tool.SmitheryToolItem;
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
        if (mat == null || pt == null) {
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".part.harvest_level",
                mat.stats().harvestLevel()).withStyle(ChatFormatting.GRAY));
        int slots = mat.stats().modifierSlotsFor(pt);
        if (slots > 0) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".part.modifier_slots",
                    slots).withStyle(ChatFormatting.GRAY));
        }

        com.mojang.blaze3d.platform.Window win =
                net.minecraft.client.Minecraft.getInstance().getWindow();
        boolean shiftDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                        win, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                        win, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);

        boolean anyDescriptionAvailable = false;

        // For each tool type that uses this part, show the stat contributions + modifiers.
        for (ToolType tt : SmitheryAPI.toolTypesUsingPart(pt)) {
            // Locate this part's slot in the tool and the first additive slot (for "primary" check).
            ToolType.Slot ourSlot = null;
            ToolType.Slot firstAdditive = null;
            for (ToolType.Slot s : tt.slots()) {
                if (firstAdditive == null && s.role() == DurabilityRole.ADDITIVE) firstAdditive = s;
                if (ourSlot == null && s.partType().equals(pt)) ourSlot = s;
            }
            if (ourSlot == null) continue;
            boolean isPrimaryAdditive = firstAdditive != null && firstAdditive.partType().equals(pt);

            tooltip.accept(Component.translatable(
                    "tooltip." + Smithery.MODID + ".part.in_tool",
                    Component.translatable(toolTypeTranslationKey(tt.id()))
            ).withStyle(ChatFormatting.DARK_GRAY));

            // Durability contribution (additive parts) or multiplier (binders).
            if (ourSlot.role() == DurabilityRole.ADDITIVE) {
                int contribution = Math.round(mat.stats().durabilityPerIngot() * pt.durabilityScalar());
                tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.durability_add", contribution)));
            } else {
                tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.durability_mul",
                        String.format("%.2f", mat.stats().binderMultiplier()))));
            }
            // Attack damage + mining speed flow only through the primary additive slot.
            if (isPrimaryAdditive) {
                tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.attack_damage",
                        String.format("%.1f", mat.stats().attackDamage()))));
                tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                        "tooltip." + Smithery.MODID + ".part.mining_speed",
                        String.format("%.1f", mat.stats().miningSpeed()))));
            }

            // Modifiers granted to this tool type, with shift-for-descriptions like the tool tooltip.
            for (ModifierEffect effect : mat.stats().modifiersFor(tt)) {
                int level = effect.paramInt("level", 1);
                MutableComponent line = Component.literal(" • ")
                        .append(Component.translatable(modifierTranslationKey(effect.modifierId()))
                                .withStyle(ChatFormatting.AQUA));
                if (level > 1) {
                    line.append(Component.literal(" " + SmitheryToolItem.toRoman(level))
                            .withStyle(ChatFormatting.AQUA));
                }
                tooltip.accept(line.withStyle(ChatFormatting.DARK_GRAY));

                String descKey = modifierDescriptionKey(effect.modifierId());
                if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
                    if (shiftDown) {
                        tooltip.accept(Component.literal("     ")
                                .append(Component.translatable(descKey))
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    } else {
                        anyDescriptionAvailable = true;
                    }
                }
            }
        }

        if (anyDescriptionAvailable) {
            tooltip.accept(Component.translatable(
                    "tooltip." + Smithery.MODID + ".tool.shift_for_descriptions")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
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

    /** Description text shown in the tool tooltip when the player holds Shift. */
    public static String modifierDescriptionKey(Identifier modifierId) {
        return modifierTranslationKey(modifierId) + ".description";
    }
}
