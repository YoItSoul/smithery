package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.entity.SmitheryArrow;
import com.soul.smithery.entity.SmitheryShuriken;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Entity-type registrations owned by Smithery.
 *
 * <p>Currently the Smithery arrow and thrown shuriken; dimensions and client tracking values
 * mirror vanilla {@code EntityType.ARROW} / {@code EntityType.SNOWBALL} so collision and
 * rendering match their vanilla counterparts.
 */
public final class SmitheryEntityTypes {
    /** Deferred register for Smithery-namespaced entity types. */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Smithery.MODID);

    /** Smithery's modular arrow entity type; parallels vanilla arrow dimensions and tracking. */
    public static final Supplier<EntityType<SmitheryArrow>> ARROW =
            registerEntity("arrow", () -> EntityType.Builder
                    .<SmitheryArrow>of(SmitheryArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(dataFixerKey("arrow")));

    /** Thrown shuriken entity type; snowball-sized, renders as its item. */
    public static final Supplier<EntityType<SmitheryShuriken>> SHURIKEN =
            registerEntity("shuriken", () -> EntityType.Builder
                    .<SmitheryShuriken>of(SmitheryShuriken::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(dataFixerKey("shuriken")));

    /** Namespaced id string handed to {@link EntityType.Builder#build(String)} for datafixer keys. */
    private static String dataFixerKey(String path) {
        return Smithery.MODID + ":" + path;
    }

    private static <T extends Entity> Supplier<EntityType<T>> registerEntity(
            String name, Supplier<EntityType<T>> factory) {
        return ENTITY_TYPES.register(name, factory);
    }

    /**
     * Binds the deferred register to the mod event bus.
     *
     * @param modEventBus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private SmitheryEntityTypes() {}
}
