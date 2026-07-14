package com.soul.smithery.item.tool;

import com.soul.smithery.entity.SmitheryShuriken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Stackable thrown weapon assembled from four shuriken blades. Right-click throws one; the
 * projectile deals the composition's attack damage and is recoverable from block hits. Extends
 * {@link SmitheryArrowItem}'s pattern of stackable composition-carrying ammo via
 * {@link SmitheryToolItem}'s stackable compose path.
 */
public class SmitheryShurikenItem extends SmitheryToolItem {

    private static final float THROW_POWER = 1.6f;
    private static final float THROW_INACCURACY = 0.5f;

    /**
     * Constructs the shuriken item bound to the given smithery ToolType id.
     */
    public SmitheryShurikenItem(Properties properties, ResourceLocation toolTypeId) {
        super(properties, toolTypeId);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.8f);
        if (level instanceof ServerLevel serverLevel) {
            SmitheryShuriken shuriken = new SmitheryShuriken(serverLevel, player, stack.copyWithCount(1));
            shuriken.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f,
                    THROW_POWER, THROW_INACCURACY);
            serverLevel.addFreshEntity(shuriken);
        }
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
