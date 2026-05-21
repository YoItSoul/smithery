package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import com.soul.smithery.gui.ForgeControllerMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public final class SmitheryMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Smithery.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ForgeControllerMenu>> FORGE_CONTROLLER =
            MENUS.register("forge_controller",
                    () -> IMenuTypeExtension.create(ForgeControllerMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    private SmitheryMenus() {}
}
