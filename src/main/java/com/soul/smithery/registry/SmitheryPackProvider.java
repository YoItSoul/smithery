package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Installs the runtime {@link SmitheryGeneratedPack} as a top-priority always-on built-in
 * client resource pack.
 *
 * <p>The pack is reopened on every resource reload, so its served JSON reflects the live
 * Smithery registry state — adding a material via datapack at runtime gets its part and
 * tool models generated on the next reload with no game restart needed.
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmitheryPackProvider {
    private SmitheryPackProvider() {}

    /**
     * Adds the Smithery generated pack to the client resource pack repository.
     *
     * @param event the {@link AddPackFindersEvent} delivered on the mod event bus
     */
    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        // Registered for BOTH pack types: client resources (models/textures) and server data
        // (the dynamic minecraft:lava fluid tag that gives molten metals lava semantics).
        PackType type = event.getPackType();
        String id = type == PackType.CLIENT_RESOURCES
                ? SmitheryGeneratedPack.PACK_ID
                : SmitheryGeneratedPack.PACK_ID + "_data";

        Pack.ResourcesSupplier supplier = packId -> new SmitheryGeneratedPack();

        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    id,
                    Component.literal("Smithery Generated"),
                    true,
                    supplier,
                    type,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );
            if (pack != null) consumer.accept(pack);
        });
    }
}
