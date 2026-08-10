package com.soul.smithery.compat.jade;

import com.soul.smithery.Smithery;
import com.soul.smithery.block.entity.CastingTableBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade tooltip for the Casting Table: which shape is impressed in the sand, and how far along the
 * pour or the cooling is.
 *
 * <p>The impressed shape is the line that matters most in practice — a row of tables all covered in
 * identical black sand gives no way to tell a pick head mould from a sword blade mould without
 * breaking one open.
 */
public final class CastingTableJadeProvider implements IBlockComponentProvider {

    /** Single shared instance; the provider holds no state. */
    public static final CastingTableJadeProvider INSTANCE = new CastingTableJadeProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Smithery.MODID, "casting_table");

    private CastingTableJadeProvider() {}

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (!(blockEntity instanceof CastingTableBlockEntity be)) return;

        ResourceLocation partTypeId = be.impressedPartTypeId();
        if (partTypeId != null) {
            CastingJadeLines.appendCast(tooltip, partTypeId);
        }

        switch (be.state()) {
            case SAND -> tooltip.add(CastingJadeLines.line("table.sand"));
            case IMPRESSED -> tooltip.add(CastingJadeLines.line("table.awaiting_metal"));
            case COOLING -> CastingJadeLines.appendCooling(tooltip, be.coolingFraction());
            case READY -> CastingJadeLines.appendReady(tooltip, be.peekPartItem());
            // An empty table says everything it needs to with its own name, and a filling one is
            // already fully described by Jade's fluid bar.
            case EMPTY, FILLING -> { }
        }
    }
}
