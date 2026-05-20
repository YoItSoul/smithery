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
 * Hooks the runtime {@link SmitheryGeneratedPack} into the client resource pack list as a
 * top-priority, always-on, hidden built-in pack. Reopened on every resource reload so the
 * served JSON reflects the live registry state — e.g., a material added via datapack at
 * runtime gets its part/tool models generated on the next reload, no restart needed.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryPackProvider {
    private SmitheryPackProvider() {}

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
