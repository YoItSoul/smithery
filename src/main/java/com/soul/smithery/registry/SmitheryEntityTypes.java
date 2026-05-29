package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.entity.SmitheryArrow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Entity-type registrations owned by Smithery.
 *
 * <p>Currently just the Smithery arrow; dimensions and client tracking values mirror
 * vanilla {@code EntityType.ARROW} so collision and rendering match vanilla arrows.
 */
public final class SmitheryEntityTypes {
    /** Deferred register for Smithery-namespaced entity types. */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Smithery.MODID);

    /** Smithery's modular arrow entity type; parallels vanilla arrow dimensions and tracking. */
    public static final Supplier<EntityType<SmitheryArrow>> ARROW =
            registerEntity("arrow", () -> EntityType.Builder
                    .<SmitheryArrow>of(SmitheryArrow::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.13f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(arrowKey()));

    private static ResourceKey<EntityType<?>> arrowKey() {
        return ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Smithery.MODID, "arrow"));
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
