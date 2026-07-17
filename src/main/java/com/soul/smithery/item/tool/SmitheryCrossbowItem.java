package com.soul.smithery.item.tool;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.api.tool.DurabilityRole;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.entity.SmitheryArrow;
import com.soul.smithery.item.PartItem;
import com.soul.smithery.item.SmitheryTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Smithery crossbow item. Extends vanilla {@link CrossbowItem} so the charge / stored-projectile
 * / fire pipeline runs unchanged; the projectile predicate is narrowed to arrows so smithery
 * arrows are the ammunition path.
 *
 * <p>1.20.1's crossbow pipeline offers no shoot-time hook, so the weapon's damage scalar is
 * stamped onto the charged projectiles' NBT when charging completes; {@link SmitheryArrow}
 * consumes the stamp on spawn and folds it into its composed base damage.
 */
public class SmitheryCrossbowItem extends CrossbowItem {

    /** Root NBT key vanilla stores a charged crossbow's loaded projectiles under. */
    private static final String KEY_CHARGED_PROJECTILES = "ChargedProjectiles";

    private final ResourceLocation toolTypeId;

    /**
     * Constructs the crossbow item bound to the given smithery ToolType id.
     */
    public SmitheryCrossbowItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties);
        this.toolTypeId = toolTypeId;
    }

    /** Returns the bound ToolType id (e.g. {@code smithery:crossbow}). */
    public ResourceLocation toolTypeId() { return toolTypeId; }
    /** Resolves the live {@link ToolType} for this crossbow item, or null if unregistered. */
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

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return getAllSupportedProjectiles();
    }

    /**
     * {@inheritDoc}
     *
     * <p>After vanilla finishes loading the projectiles, stamps this weapon's damage scalar
     * onto each stored projectile so the spawned {@link SmitheryArrow} can apply it.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        super.releaseUsing(stack, level, entity, timeLeft);
        if (isCharged(stack)) {
            stampWeaponScalarOnChargedProjectiles(stack);
        }
    }

    /**
     * Writes {@link SmitheryArrow#KEY_WEAPON_SCALAR} into every charged projectile's item NBT.
     * The stamp is consumed (and removed) when the arrow entity spawns, so recovered arrows
     * stack cleanly with fresh ones.
     */
    private static void stampWeaponScalarOnChargedProjectiles(ItemStack crossbow) {
        ToolComposition comp = SmitheryToolData.getComposition(crossbow);
        if (comp == null || !comp.isValid()) return;
        ToolStats stats = ToolStats.compute(comp);
        float scalar = Math.max(0.5f, stats.attackDamage / 2.0f);

        CompoundTag root = crossbow.getTag();
        if (root == null || !root.contains(KEY_CHARGED_PROJECTILES)) return;
        ListTag projectiles = root.getList(KEY_CHARGED_PROJECTILES, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < projectiles.size(); i++) {
            CompoundTag itemTag = projectiles.getCompound(i);
            CompoundTag stackTag = itemTag.getCompound("tag");
            stackTag.putFloat(SmitheryArrow.KEY_WEAPON_SCALAR, scalar);
            itemTag.put("tag", stackTag);
        }
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
