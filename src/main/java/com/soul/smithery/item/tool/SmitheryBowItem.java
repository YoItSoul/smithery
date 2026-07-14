package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.entity.SmitheryArrow;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.registry.SmitheryDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Smithery bow item. Extends vanilla {@link BowItem} so the full draw/charge/shoot
 * pipeline runs unchanged; only projectile prep (smithery arrow damage scaling) and
 * the tooltip are customised. Per-stack composition lives in the TOOL_COMPOSITION
 * component written by {@link SmitheryToolItem#applyComposition}.
 */
public class SmitheryBowItem extends BowItem {

    private final ResourceLocation toolTypeId;

    /**
     * Constructs the bow item bound to the given smithery ToolType id.
     */
    public SmitheryBowItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:bow}). */
    public ResourceLocation toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this bow item, or null if unregistered. */
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return s -> s.is(ItemTags.ARROWS);
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon,
                                          ItemStack projectile, boolean isCrit) {
        Projectile out = super.createProjectile(level, shooter, weapon, projectile, isCrit);
        if (out instanceof AbstractArrow arrow) {
            applySmitheryArrowDamage(arrow, projectile, weapon);
        }
        return out;
    }

    /** Shared with {@link SmitheryCrossbowItem}: scales arrow base damage by arrow + weapon stats. */
    static void applySmitheryArrowDamage(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
        ToolComposition arrowComp = projectile.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (arrowComp == null || !arrowComp.isValid()) return;
        ToolStats arrowStats = ToolStats.compute(arrowComp);

        float bowScalar = bowDamageScalar(weapon);

        double finalDamage = arrowStats.attackDamage * bowScalar;
        if (finalDamage > 0.0) {
            arrow.setBaseDamage(finalDamage);
        }
    }

    private static float bowDamageScalar(ItemStack weapon) {
        ToolComposition bowComp = weapon.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (bowComp == null || !bowComp.isValid()) return 1.0f;
        ToolStats bowStats = ToolStats.compute(bowComp);
        return Math.max(0.5f, bowStats.attackDamage / 2.0f);
    }

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
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
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        ToolType tt = toolType();
        if (comp == null || tt == null || !comp.isValid()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.uncomposed")
                    .withStyle(ChatFormatting.RED));
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        java.util.List<com.soul.smithery.api.modifier.ModifierEffect> applied =
                stack.getOrDefault(SmitheryDataComponents.APPLIED_MODIFIERS.get(),
                        java.util.List.<com.soul.smithery.api.modifier.ModifierEffect>of());
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
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(com.soul.smithery.item.SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("×%.2f", Math.max(0.5f, stats.attackDamage / 2.0f)))));

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

    private static ResourceLocation primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }
}
