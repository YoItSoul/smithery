package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.SmitheryTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Smithery bow item. Extends vanilla {@link BowItem}; per-stack composition lives in the
 * {@code tool_composition} NBT written by {@link SmitheryToolItem#applyComposition}.
 *
 * <p>1.20.1's BowItem offers no projectile-preparation hook, so {@link #releaseUsing}
 * mirrors the vanilla shoot path with the smithery arrow damage scaling applied to the
 * spawned arrow.
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

    /** {@inheritDoc} Serves the composed durability; see {@link SmitheryToolData}. */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return SmitheryToolData.getMaxDurability(stack, super.getMaxDamage(stack));
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return s -> s.is(ItemTags.ARROWS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Mirrors the vanilla 1.20.1 bow shoot path, inserting
     * {@link #applySmitheryArrowDamage} on the spawned arrow — the only divergence.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        boolean infinite = player.getAbilities().instabuild
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;
        ItemStack projectile = player.getProjectile(stack);

        int charge = this.getUseDuration(stack) - timeLeft;
        charge = ForgeEventFactory.onArrowLoose(stack, level, player, charge, !projectile.isEmpty() || infinite);
        if (charge < 0) return;

        if (projectile.isEmpty() && !infinite) return;
        if (projectile.isEmpty()) {
            projectile = new ItemStack(Items.ARROW);
        }

        float power = getPowerForTime(charge);
        if (power < 0.1f) return;

        boolean creativeArrow = infinite && projectile.is(Items.ARROW);
        if (!level.isClientSide) {
            ArrowItem arrowItem = (ArrowItem) (projectile.getItem() instanceof ArrowItem a ? a : Items.ARROW);
            AbstractArrow arrow = arrowItem.createArrow(level, projectile, player);
            arrow = customArrow(arrow);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, power * 3.0f, 1.0f);
            if (power == 1.0f) arrow.setCritArrow(true);

            applySmitheryArrowDamage(arrow, projectile, stack);

            int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
            if (powerLevel > 0) arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5 + 0.5);
            int punchLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
            if (punchLevel > 0) arrow.setKnockback(punchLevel);
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0) {
                arrow.setSecondsOnFire(100);
            }

            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
            if (creativeArrow || player.getAbilities().instabuild
                    && (projectile.is(Items.SPECTRAL_ARROW) || projectile.is(Items.TIPPED_ARROW))) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            level.addFreshEntity(arrow);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0f, 1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        if (!creativeArrow && !player.getAbilities().instabuild) {
            projectile.shrink(1);
            if (projectile.isEmpty()) {
                player.getInventory().removeItem(projectile);
            }
        }
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    /** Shared with {@link SmitheryCrossbowItem}: scales arrow base damage by arrow + weapon stats. */
    static void applySmitheryArrowDamage(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
        ToolComposition arrowComp = SmitheryToolData.getComposition(projectile);
        if (arrowComp == null || !arrowComp.isValid()) return;
        ToolStats arrowStats = ToolStats.compute(arrowComp);

        float bowScalar = bowDamageScalar(weapon);

        double finalDamage = arrowStats.attackDamage * bowScalar;
        if (finalDamage > 0.0) {
            arrow.setBaseDamage(finalDamage);
        }
    }

    private static float bowDamageScalar(ItemStack weapon) {
        ToolComposition bowComp = SmitheryToolData.getComposition(weapon);
        if (bowComp == null || !bowComp.isValid()) return 1.0f;
        ToolStats bowStats = ToolStats.compute(bowComp);
        return Math.max(0.5f, bowStats.attackDamage / 2.0f);
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Consumer<Component> tooltip = lines::add;
        ToolComposition comp = SmitheryToolData.getComposition(stack);
        ToolType tt = toolType();
        if (comp == null || tt == null || !comp.isValid()) {
            tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.uncomposed")
                    .withStyle(ChatFormatting.RED));
            super.appendHoverText(stack, level, lines, flag);
            return;
        }

        List<ModifierEffect> applied = SmitheryToolData.getAppliedModifiers(stack);
        ToolStats stats = ToolStats.compute(comp, applied);
        SmitheryTooltips.Tier tier = SmitheryTooltips.currentTier();

        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".section.summary")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (tier == SmitheryTooltips.Tier.BASIC) {
            SmitheryTooltips.appendKeyHint(tooltip, tier);
            super.appendHoverText(stack, level, lines, flag);
            return;
        }

        tooltip.accept(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("×%.2f", Math.max(0.5f, stats.attackDamage / 2.0f)))));

        tooltip.accept(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".tool.parts")));
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot slot = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
            if (m == null) continue;
            PartType pt = slot.partType();
            tooltip.accept(SmitheryTooltips.bullet(Component.empty()
                    .append(Component.translatable(PartItem.materialTranslationKey(m.id())))
                    .append(Component.literal(" "))
                    .append(Component.translatable(PartItem.partTranslationKey(pt.id())))));
        }

        SmitheryTooltips.appendKeyHint(tooltip, tier);
        super.appendHoverText(stack, level, lines, flag);
    }

    private static ResourceLocation primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }

    /** Ranged/thrown gear containing a foil material shimmers with the enchantment glint. */
    @Override
    public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
        return super.isFoil(stack) || SmitheryToolItem.hasFoilMaterial(stack);
    }
}
