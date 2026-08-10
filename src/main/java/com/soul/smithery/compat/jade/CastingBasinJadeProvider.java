package com.soul.smithery.compat.jade;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingBasinBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade tooltip for the Casting Basin: how far along the pour or the cooling is.
 *
 * <p>No cast line here — a basin has one shape, so what it is casting is decided entirely by the
 * metal poured in, which the fluid line already names.
 */
public final class CastingBasinJadeProvider implements IBlockComponentProvider {

    /** Single shared instance; the provider holds no state. */
    public static final CastingBasinJadeProvider INSTANCE = new CastingBasinJadeProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "casting_basin");

    private CastingBasinJadeProvider() {}

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (!(blockEntity instanceof CastingBasinBlockEntity be)) return;

        switch (be.state()) {
            case COOLING -> CastingJadeLines.appendCooling(tooltip, be.coolingFraction());
            case READY -> CastingJadeLines.appendReady(tooltip, be.peekBlockItem());
            // An empty basin says everything it needs to with its own name, and a filling one is
            // already fully described by Jade's fluid bar.
            case EMPTY, FILLING -> { }
        }
    }
}
