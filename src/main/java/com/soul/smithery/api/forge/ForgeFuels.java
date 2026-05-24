package com.soul.smithery.api.forge;

import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of fluids the forge accepts as fuel and the target temperature each one drives the
 * forge to. The forge controller scans every connected fuel port each tick, looks up each
 * port's stored fluid in this registry, and uses the highest temperature target across all
 * ports that have fuel. So a forge with both a lava-filled port and a molten-blaze-filled
 * port will heat to molten blaze's temperature (the hotter of the two).
 *
 * <h3>Modder usage</h3>
 * <pre>{@code
 *   ForgeFuels.register(myMod.getMyHotFluid(), new ForgeFuels.Profile(4000f));
 * }</pre>
 *
 * Must be called after fluids are registered (i.e. in mod init AFTER deferred registers have
 * fired, or in {@code FMLCommonSetupEvent}).
 */
public final class ForgeFuels {
    private ForgeFuels() {}

    private static final Map<Fluid, Profile> REGISTRY = new LinkedHashMap<>();

    /**
     * Heat profile for a fuel fluid. {@code targetTemperatureC} is the temperature the forge
     * climbs to (asymptotically) when this fluid is the highest-temp fuel available.
     */
    public record Profile(float targetTemperatureC) {}

    public static void register(Fluid fluid, Profile profile) {
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(profile, "profile");
        REGISTRY.put(fluid, profile);
    }

    public static @Nullable Profile get(Fluid fluid) {
        return fluid == null ? null : REGISTRY.get(fluid);
    }

    public static boolean isFuel(Fluid fluid) {
        return get(fluid) != null;
    }

    public static Map<Fluid, Profile> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
