package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.gui.ForgeControllerMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/**
 * Menu (container) type registrations for Smithery's UI surfaces.
 *
 * <p>The Forge controller is currently the only GUI screen in the mod — every other
 * interaction is in-world.
 */
public final class SmitheryMenus {
    /** Deferred register for Smithery-namespaced menu types. */
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Smithery.MODID);

    /** Menu type for the Forge controller GUI screen. */
    public static final DeferredHolder<MenuType<?>, MenuType<ForgeControllerMenu>> FORGE_CONTROLLER =
            MENUS.register("forge_controller",
                    () -> IMenuTypeExtension.create(ForgeControllerMenu::new));

    /**
     * Binds the deferred register to the mod event bus.
     *
     * @param bus the mod-bus the deferred register attaches to
     */
    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    private SmitheryMenus() {}
}
