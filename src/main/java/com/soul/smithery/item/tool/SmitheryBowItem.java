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
import net.minecraft.resources.Identifier;
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
 * Smithery bow. Extends vanilla {@link BowItem} so all of vanilla's draw / charge / shoot
 * pipeline runs unchanged — we only customise the projectile prep (smithery arrow damage
 * comes from its composition, not from any vanilla baseline) and the tooltip.
 *
 * <h3>Why extend BowItem (and not SmitheryToolItem)</h3>
 * BowItem extends ProjectileWeaponItem which provides the {@code shoot} / {@code draw} /
 * {@code useAmmo} pipeline tied into player.getProjectile / inventory ammo lookup.
 * Re-implementing that on top of SmitheryToolItem would duplicate a non-trivial amount of
 * vanilla logic; subclassing BowItem keeps us automatically forward-compatible when vanilla
 * tweaks the bow flow.
 *
 * <h3>Composition wiring</h3>
 * Per-stack composition lives in the TOOL_COMPOSITION component (written by
 * {@link SmitheryToolItem#applyComposition}). Durability comes from the bow limb +
 * bowstring materials; the bowstring material's binderMultiplier scales it.
 *
 * <h3>Ammo</h3>
 * Accepts both smithery arrows (via {@link SmitheryArrowItem}) and vanilla arrows — both
 * are reachable through {@code ItemTags.ARROWS}, which vanilla BowItem's default predicate
 * already uses. Smithery arrows carry their own composition; vanilla arrows fire with
 * baseline damage.
 */
public class SmitheryBowItem extends BowItem {

    private final Identifier toolTypeId;

    public SmitheryBowItem(Properties properties, Identifier toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    public Identifier toolTypeId() { return toolTypeId; }
    public ToolType toolType() { return SmitheryAPI.TOOL_TYPES.get(toolTypeId); }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        // Accept anything tagged minecraft:arrows. Vanilla arrows + smithery arrows (the
        // SmitheryArrowItem registration is tag-injected into #minecraft:arrows via
        // data/minecraft/tags/item/arrows.json) both pass.
        return s -> s.is(ItemTags.ARROWS);
    }

    /**
     * Override so the projectile spawned for a smithery arrow carries the right per-arrow base
     * damage (from the arrow's TOOL_COMPOSITION) and also picks up any bow-side damage bonus
     * scaling from the bow's own composition. Vanilla flow:
     *   ArrowItem.createArrow(...) → AbstractArrow with baseDamage = 2.0 (vanilla default)
     *   then EnchantmentHelper.modifyDamage scales it.
     * Our flow stamps the arrow's baseDamage from composition stats before returning.
     */
    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon,
                                          ItemStack projectile, boolean isCrit) {
        Projectile out = super.createProjectile(level, shooter, weapon, projectile, isCrit);
        if (out instanceof AbstractArrow arrow) {
            applySmitheryArrowDamage(arrow, projectile, weapon);
        }
        return out;
    }

    /**
     * Stamps the arrow's baseDamage from the arrow's composition (arrow_head material's
     * attackDamage drives the baseline). A separate bow-side scalar layers on top: a wooden
     * bow shoots less hard than a netherite bow even with the same arrow. Both contributions
     * come from {@link ToolStats}.
     *
     * If the projectile is a vanilla arrow (no smithery composition), we leave its baseDamage
     * alone — vanilla's 2.0 default applies, plus any bow-side enchantment scaling.
     */
    private static void applySmitheryArrowDamage(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
        ToolComposition arrowComp = projectile.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (arrowComp == null || !arrowComp.isValid()) return;
        ToolStats arrowStats = ToolStats.compute(arrowComp);

        // Bow-side multiplier: average of limb attackDamage / 2.0 (vanilla baseline). A bow
        // with mediocre limbs reduces damage vs a strong-material bow. Capped to ≥ 0.5 so a
        // wood bow still does meaningful damage with iron arrows.
        float bowScalar = bowDamageScalar(weapon);

        double finalDamage = arrowStats.attackDamage * bowScalar;
        if (finalDamage > 0.0) {
            arrow.setBaseDamage(finalDamage);
        }
    }

    /**
     * Bow-side damage scalar. Reads the bow's TOOL_COMPOSITION and returns a multiplier that
     * scales arrow base damage. Calibrated so an iron-limb bow (attackDamage=2.0) reads as
     * 1.0 — a neutral scalar — and other materials scale up/down from there.
     */
    private static float bowDamageScalar(ItemStack weapon) {
        ToolComposition bowComp = weapon.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        if (bowComp == null || !bowComp.isValid()) return 1.0f;
        ToolStats bowStats = ToolStats.compute(bowComp);
        // Iron's attackDamage = 2.0 → 1.0× multiplier. Half-iron → 0.5×, double-iron → 2.0×.
        return Math.max(0.5f, bowStats.attackDamage / 2.0f);
    }

    // ---- Display name & tooltip — mirror SmitheryToolItem's shape ----

    @Override
    public Component getName(ItemStack stack) {
        ToolComposition comp = stack.get(SmitheryDataComponents.TOOL_COMPOSITION.get());
        ToolType tt = toolType();
        if (comp == null || !comp.isValid() || tt == null) {
            return Component.translatable(PartItem.toolTypeTranslationKey(toolTypeId));
        }
        Identifier primaryMat = primaryAdditiveMaterial(tt, comp);
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

        // Parts breakdown
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.parts")
                .withStyle(ChatFormatting.GRAY));
        List<ToolType.Slot> slots = tt.slots();
        for (int i = 0; i < slots.size(); i++) {
            ToolType.Slot slot = slots.get(i);
            Material m = SmitheryAPI.MATERIALS.get(comp.slotMaterials().get(i));
            if (m == null) continue;
            PartType pt = slot.partType();
            tooltip.accept(Component.literal(" • ")
                    .append(Component.translatable(PartItem.materialTranslationKey(m.id())))
                    .append(Component.literal(" "))
                    .append(Component.translatable(PartItem.partTranslationKey(pt.id())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // Stats — bow shows durability and a "bow damage scalar" derived from limb attackDamage.
        // The arrow's own damage stat is shown on the arrow's tooltip; multiplying the two gives
        // the actual shot damage at full charge.
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.durability", stats.maxDurability)));
        tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("×%.2f", Math.max(0.5f, stats.attackDamage / 2.0f)))));

        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    private static Identifier primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }
}
