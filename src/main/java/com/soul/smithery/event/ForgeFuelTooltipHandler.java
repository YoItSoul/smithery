package com.soul.smithery.event;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.forge.ForgeFuels;
import com.soul.smithery.item.SmitheryTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tells a player that a bucket is forge fuel, and how hot it burns.
 *
 * <p>Which fluids the fuel port accepts is a runtime registry ({@link ForgeFuels}) that addons add
 * to, so there is nothing on a molten-blaze bucket itself to suggest it belongs in a fuel port — and
 * nothing anywhere to say it reaches 3500°C while lava stalls at 1650°C. Since the forge runs at the
 * temperature of its hottest fuel, and a melting recipe simply refuses to run below its own
 * temperature, that number is the difference between a working setup and an inert one. It should be
 * legible from the bucket.
 *
 * <p>The predicate deliberately mirrors {@code ForgeFuelPortBlock#use} — {@link BucketItem} with a
 * non-empty registered fuel fluid — so the tooltip promises exactly what the port will accept, and
 * automatically covers fuels registered by addons.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT)
public final class ForgeFuelTooltipHandler {

    private ForgeFuelTooltipHandler() {}

    /**
     * Appends the fuel header and target-temperature line to a fuel bucket's tooltip.
     *
     * @param event item-tooltip event whose stack is inspected
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof BucketItem bucket)) return;

        Fluid fluid = bucket.getFluid();
        if (fluid == null || fluid == Fluids.EMPTY) return;

        ForgeFuels.Profile profile = ForgeFuels.get(fluid);
        if (profile == null) return;

        event.getToolTip().add(SmitheryTooltips.sectionHeader(
                Component.translatable("tooltip." + Smithery.MODID + ".fuel.header")));
        event.getToolTip().add(SmitheryTooltips.statLine(
                Component.translatable("tooltip." + Smithery.MODID + ".fuel.temperature",
                        String.format("%.0f", profile.targetTemperatureC()))));
    }
}
