package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.entity.SmitheryArrow;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.tool.SmitheryToolData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Smithery arrow item. Each stack carries a {@link ToolComposition} (arrow_head, shaft,
 * fletching materials) on its TOOL_COMPOSITION data component and stacks identically
 * with same-composition arrows. Extends vanilla {@link ArrowItem} so both vanilla and
 * smithery bows route through the same {@code createArrow} entry point.
 */
public class SmitheryArrowItem extends ArrowItem {

    private final ResourceLocation toolTypeId;

    /**
     * Constructs the arrow item bound to the given smithery ToolType id.
     */
    public SmitheryArrowItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:arrow}). */
    public ResourceLocation toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this arrow item, or null if unregistered. */
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack itemStack, LivingEntity owner,
                                     @Nullable ItemStack firedFromWeapon) {
        return new SmitheryArrow(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        return SmitheryArrow.spawnFromDispenser(level, position.x(), position.y(), position.z(), itemStack);
    }

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

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        ToolType tt = toolType();
        if (comp == null || tt == null || !comp.isValid()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.uncomposed")
                    .withStyle(ChatFormatting.RED));
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                SmitheryToolData.getAppliedModifiers(stack);
        ToolStats stats = ToolStats.compute(comp, applied);
        com.soul.smithery.item.SmitheryTooltips.Tier tier = com.soul.smithery.item.SmitheryTooltips.currentTier();

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (tier == com.soul.smithery.item.SmitheryTooltips.Tier.BASIC) {
            com.soul.smithery.item.SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")));
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("%.1f", stats.attackDamage))));

        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.parts")));
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot slot = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
            if (m == null) continue;
            PartType pt = slot.partType();
            tooltip.accept(com.soul.smithery.item.SmitheryTooltips.bullet(Component.empty()
                    .append(Component.translatable(PartItem.materialTranslationKey(m.id())))
                    .append(Component.literal(" "))
                    .append(Component.translatable(PartItem.partTranslationKey(pt.id())))));
        }

        com.soul.smithery.item.SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    /**
     * Routes through {@link ToolCompositions#apply} so freshly-crafted arrow
     * stacks pick up TOOL_COMPOSITION, MAX_DAMAGE, and attribute modifiers via the same
     * path every other smithery tool uses. Exists for code paths that build arrows
     * outside the assembly recipe (commands, loot tables, etc.).
     */
    public static ItemStack craftArrowStack(ItemStack stack, ToolComposition comp) {
        return ToolCompositions.apply(stack, comp);
    }

    private static ResourceLocation primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        Integer dmg = stack.get(DataComponents.DAMAGE);
        return dmg != null && dmg > 0;
    }
}
