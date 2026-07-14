package com.soul.smithery;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common-side configuration spec for Smithery.
 *
 * <p>Holds the {@link ForgeConfigSpec} the mod registers against
 * {@code ModConfig.Type.COMMON}. Currently empty by design; runtime-tunable knobs
 * (forge tick rate, melt-rate caps, etc.) land here as they are promoted from constants.
 */
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
