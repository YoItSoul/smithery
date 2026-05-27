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
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Smithery arrow item. Each ItemStack carries a {@link ToolComposition} (arrow_head, shaft,
 * fletching materials). Stacks vanilla-style (up to 64) with same-composition stacking — two
 * arrows of identical material composition merge into the same slot.
 *
 * <h3>Why extend ArrowItem (and not SmitheryToolItem)</h3>
 * Vanilla {@link ArrowItem#createArrow} is the entry point both vanilla and smithery bows go
 * through to spawn the projectile. Extending ArrowItem keeps that wiring intact while letting
 * us return our {@link SmitheryArrow} (which carries composition + fires on-hit modifiers).
 * SmitheryToolItem couldn't be used as the base because Java doesn't allow extending both.
 *
 * <h3>Composition wiring</h3>
 * The crafted arrow's TOOL_COMPOSITION data component is written by the shared
 * {@link SmitheryToolItem#applyComposition} path — same as every other smithery tool. This
 * keeps the tooltip / stats / modifier resolution identical to swords, pickaxes, etc.
 */
public class SmitheryArrowItem extends ArrowItem {

    private final Identifier toolTypeId;

    public SmitheryArrowItem(Properties properties, Identifier toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    public Identifier toolTypeId() { return toolTypeId; }
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

        // Stats — arrows show damage (per arrow, before charge multiplier) + the stack count
        // as remaining shots. No mining-speed line: arrows don't mine.
        tooltip.accept(Component.translatable("tooltip." + Smithery.MODID + ".tool.stats")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(SmitheryToolItem.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.attack_damage",
                String.format("%.1f", stats.attackDamage))));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    /**
     * Apply the shared smithery composition logic to a freshly-crafted arrow stack: writes
     * TOOL_COMPOSITION, MAX_DAMAGE (used as a wear indicator on picked-up arrows), and the
     * attribute modifiers. Same path as every other smithery tool — see
     * {@link SmitheryToolItem#applyComposition}.
     *
     * Note: arrows are crafted via the shaped {@link ToolAssemblyRecipe} which already calls
     * {@code SmitheryToolItem.applyComposition} on the result. This static helper exists for
     * any future code path that constructs arrows without going through the recipe (commands,
     * loot tables, etc.).
     */
    public static ItemStack craftArrowStack(ItemStack stack, ToolComposition comp) {
        return SmitheryToolItem.applyComposition(stack, comp);
    }

    private static Identifier primaryAdditiveMaterial(ToolType tt, ToolComposition comp) {
        for (int i = 0; i < tt.slots().size(); i++) {
            if (tt.slots().get(i).role() == DurabilityRole.ADDITIVE) {
                return comp.slotMaterials().get(i);
            }
        }
        return null;
    }

    // Mark arrows as a single-stack damage-bearing item like vanilla via Properties at registration —
    // see SmitheryItems.ARROW. Stack size remains the vanilla 64 default; same-composition arrows
    // merge into a single stack courtesy of vanilla ItemStack.matches honoring TOOL_COMPOSITION.

    /** Mainly cosmetic — display the arrow's wear bar based on DAMAGE / MAX_DAMAGE. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        // Show the wear bar only when the arrow has actually taken damage (e.g. a picked-up arrow
        // that broke partially). Fresh stacks have damage==0 and don't show a bar.
        Integer dmg = stack.get(DataComponents.DAMAGE);
        return dmg != null && dmg > 0;
    }
}
