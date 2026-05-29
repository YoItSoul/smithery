package com.soul.smithery.api.forge;

import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of fluids the forge accepts as fuel, paired with each fuel's target temperature.
 *
 * <p>The forge controller scans every connected fuel port each tick, looks up the port's stored
 * fluid here, and uses the hottest profile across all ports that have fuel. A forge with both a
 * lava port and a molten-blaze port heats to molten blaze's temperature (the hotter of the two).
 *
 * <p>Registration must occur after fluids have been registered (i.e. in mod init AFTER deferred
 * registers have fired, or in {@code FMLCommonSetupEvent}).
 */
public final class ForgeFuels {
    private ForgeFuels() {}

    private static final Map<Fluid, Profile> REGISTRY = new LinkedHashMap<>();

    /**
     * Heat profile for a fuel fluid.
     *
     * @param targetTemperatureC temperature (in degrees Celsius) the forge climbs to asymptotically
     *                           when this fluid is the highest-temp fuel available
     */
    public record Profile(float targetTemperatureC) {}

    /** Registers (or replaces) the heat profile for the given fuel fluid. */
    public static void register(Fluid fluid, Profile profile) {
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(profile, "profile");
        REGISTRY.put(fluid, profile);
    }

    /** Looks up the heat profile for a fluid, or {@code null} if it isn't a fuel. */
    public static @Nullable Profile get(Fluid fluid) {
        return fluid == null ? null : REGISTRY.get(fluid);
    }

    /** True if this fluid is a registered forge fuel. */
    public static boolean isFuel(Fluid fluid) {
        return get(fluid) != null;
    }

    /** Unmodifiable view of every registered fuel-to-profile mapping. */
    public static Map<Fluid, Profile> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
