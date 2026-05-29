package com.soul.smithery.registry;

import com.soul.smithery.Smithery;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

/**
 * Installs the runtime {@link SmitheryGeneratedPack} as a top-priority always-on hidden
 * built-in client resource pack.
 *
 * <p>The pack is reopened on every resource reload, so its served JSON reflects the live
 * Smithery registry state — adding a material via datapack at runtime gets its part and
 * tool models generated on the next reload with no game restart needed.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryPackProvider {
    private SmitheryPackProvider() {}

    /**
     * Adds the Smithery generated pack to the client resource pack repository.
     *
     * @param event the {@link AddPackFindersEvent} delivered on the mod event bus
     */
    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        PackLocationInfo location = new PackLocationInfo(
                SmitheryGeneratedPack.PACK_ID,
                Component.literal("Smithery Generated"),
                PackSource.BUILT_IN,
                Optional.empty()
        );

        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) { return new SmitheryGeneratedPack(); }

            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata metadata) { return new SmitheryGeneratedPack(); }
        };

        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    location,
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false)
            );
            if (pack != null) consumer.accept(pack);
        });
    }
}
