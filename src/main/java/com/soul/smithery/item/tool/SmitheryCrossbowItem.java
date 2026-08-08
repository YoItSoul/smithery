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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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

    /** TC 1.12 {@code CrossBow.projectileDamageModifier()}: crossbows hit for 130% of the ammo. */
    public static final float CROSSBOW_DAMAGE_MODIFIER = 1.3f;
    /** TC 1.12 {@code CrossBow.getDrawTime()}: a drawSpeed-1.0 crossbow reloads in 45 ticks. */
    public static final int BASE_DRAW_TIME = 45;
    /** TC 1.12 {@code CrossBow.baseProjectileSpeed()} — bolts leave far faster than arrows. */
    public static final float BASE_PROJECTILE_SPEED = 7.0f;
    /** Vanilla's crossbow spread, kept so bolts stay the precise option. */
    public static final float CROSSBOW_INACCURACY = 1.0f;

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
     * Fires the loaded bolts at the limbs' range instead of vanilla's fixed 3.15.
     *
     * <p>Vanilla's {@code use} hard-codes the shot velocity, so the charged branch is replayed here
     * with {@code BASE_PROJECTILE_SPEED × range} — TC 1.12's {@code CrossBow.baseProjectileSpeed()}
     * was 7.0 against a shortbow's 3.0, which is what made crossbows the flat-trajectory option.
     * Charge speed is handled separately in {@link #releaseUsing}.</p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isCharged(stack)) return super.use(level, player, hand);

        ToolStats stats = SmitheryBowItem.statsOf(stack);
        float range = stats != null ? stats.range : 1.0f;
        performShooting(level, player, hand, stack,
                BASE_PROJECTILE_SPEED * range, CROSSBOW_INACCURACY);
        setCharged(stack, false);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Ticks this crossbow needs to charge: vanilla's duration (25, less 5 per Quick Charge level)
     * divided by the limbs' draw speed, so material and enchantment stack the way TC 1.12's
     * {@code CrossBow.getDrawTime()} and Quick Charge each did on their own.
     *
     * <p>Vanilla's {@link #getChargeDuration} is static and can't be overridden, so the charge
     * check is reimplemented in {@link #releaseUsing} against this value instead.</p>
     */
    public static int chargeDurationFor(ItemStack stack) {
        ToolStats stats = SmitheryBowItem.statsOf(stack);
        float drawSpeed = stats != null ? stats.drawSpeed : 1.0f;
        return Math.max(1, Math.round(getChargeDuration(stack) / Math.max(0.001f, drawSpeed)));
    }

    /** Holding time this crossbow allows, tracking its own charge duration like vanilla's +3. */
    @Override
    public int getUseDuration(ItemStack stack) {
        return chargeDurationFor(stack) + 3;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Replaces vanilla's charge check, which divides by the static {@link #getChargeDuration},
     * with one against {@link #chargeDurationFor} so a fast-limbed crossbow really does load
     * sooner. Projectile loading mirrors vanilla's private {@code tryLoadProjectiles}: Multishot
     * loads three, creative players keep their ammo, and an empty-handed creative player falls
     * back to a plain arrow. Once loaded, each stored projectile takes this weapon's damage
     * stamp so the spawned {@link SmitheryArrow} can apply it.</p>
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        if (used < chargeDurationFor(stack) || isCharged(stack)) return;
        if (!loadProjectiles(entity, stack)) return;

        setCharged(stack, true);
        SoundSource source = entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.CROSSBOW_LOADING_END, source,
                1.0f, 1.0f / (level.getRandom().nextFloat() * 0.5f + 1.0f) + 0.2f);
        stampWeaponScalarOnChargedProjectiles(stack);
    }

    /**
     * Loads this crossbow's projectiles, mirroring vanilla's private {@code tryLoadProjectiles}.
     * Returns false when the shooter has no usable ammo, leaving the crossbow uncharged.
     */
    private boolean loadProjectiles(LivingEntity shooter, ItemStack crossbow) {
        int multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, crossbow);
        int count = multishot == 0 ? 1 : 3;
        boolean creative = shooter instanceof Player p && p.getAbilities().instabuild;

        ItemStack ammo = shooter.getProjectile(crossbow);
        if (ammo.isEmpty() && creative) {
            ammo = new ItemStack(Items.ARROW);
        }
        if (ammo.isEmpty()) return false;

        ItemStack template = ammo.copy();
        for (int i = 0; i < count; i++) {
            // Only the first shot of a Multishot volley consumes ammo, as in vanilla.
            ItemStack loaded = (i > 0 || creative) ? template.copy() : ammo.split(1);
            if (loaded.isEmpty()) return false;
            if (i == 0 && !creative && ammo.isEmpty() && shooter instanceof Player p) {
                p.getInventory().removeItem(ammo);
            }
            addChargedProjectile(crossbow, loaded.copy());
        }
        return true;
    }

    /** Appends one projectile to the crossbow's {@code ChargedProjectiles} list, as vanilla does. */
    private static void addChargedProjectile(ItemStack crossbow, ItemStack projectile) {
        CompoundTag tag = crossbow.getOrCreateTag();
        ListTag list = tag.contains(KEY_CHARGED_PROJECTILES, CompoundTag.TAG_LIST)
                ? tag.getList(KEY_CHARGED_PROJECTILES, CompoundTag.TAG_COMPOUND)
                : new ListTag();
        list.add(projectile.save(new CompoundTag()));
        tag.put(KEY_CHARGED_PROJECTILES, list);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Vanilla's loading sounds are keyed to its own charge duration, so they're replayed here
     * against {@link #chargeDurationFor} — otherwise a fast crossbow finishes loading before its
     * "loading middle" click plays.</p>
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        int charge = getChargeDuration(stack);
        int scaled = chargeDurationFor(stack);
        // Hand vanilla a remaining-time rescaled onto its own timeline: same sound beats, our pace.
        int elapsed = getUseDuration(stack) - remaining;
        float progress = Math.min(1.0f, (float) elapsed / scaled);
        super.onUseTick(level, entity, stack, (int) (charge + 3 - progress * charge));
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
        // TC 1.12 CrossBow: 1.3x the ammo's damage, plus the limb's flat bonus. Only the scalar
        // rides the projectile NBT, so the flat bonus is folded into it against the ammo's own
        // damage — the arrow entity has no second field to read.
        float scalar = CROSSBOW_DAMAGE_MODIFIER;
        float flatBonus = stats.bonusDamage + stats.passiveBonusDamage;
        if (flatBonus > 0f) {
            float ammoDamage = 0f;
            CompoundTag rootTag = crossbow.getTag();
            if (rootTag != null && rootTag.contains(KEY_CHARGED_PROJECTILES)) {
                ListTag charged = rootTag.getList(KEY_CHARGED_PROJECTILES, CompoundTag.TAG_COMPOUND);
                if (!charged.isEmpty()) {
                    ToolStats ammo = SmitheryBowItem.statsOf(ItemStack.of(charged.getCompound(0)));
                    if (ammo != null) ammoDamage = ammo.attackDamage;
                }
            }
            if (ammoDamage > 0f) scalar += flatBonus / ammoDamage;
        }

        CompoundTag root = crossbow.getTag();
        if (root == null || !root.contains(KEY_CHARGED_PROJECTILES)) return;
        ListTag projectiles = root.getList(KEY_CHARGED_PROJECTILES, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < projectiles.size(); i++) {
            CompoundTag itemTag = projectiles.getCompound(i);
            CompoundTag stackTag = itemTag.getCompound("tag");
            stackTag.putFloat(SmitheryArrow.KEY_WEAPON_SCALAR, scalar);
            if (SmitheryArrow.grantsWaterDragImmunity(crossbow)) {
                stackTag.putBoolean(SmitheryArrow.KEY_IGNORES_WATER_DRAG, true);
            }
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
                "tooltip." + Smithery.MODID + ".tool.draw_speed",
                String.format("%.2f", stats.drawSpeed))));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.range",
                String.format("×%.2f", stats.range))));
        tooltip.accept(SmitheryTooltips.statLine(Component.translatable(
                "tooltip." + Smithery.MODID + ".tool.bonus_damage",
                String.format("%.1f", stats.bonusDamage))));

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
