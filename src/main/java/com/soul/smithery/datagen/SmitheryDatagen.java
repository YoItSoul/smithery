package com.soul.smithery.datagen;

import com.klikli_dev.modonomicon.api.datagen.BookProvider;
import com.klikli_dev.modonomicon.api.datagen.BookSubProvider;
import com.soul.smithery.Smithery;
import com.soul.smithery.book.SmitheryBookSubProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Datagen entry point that wires up the Smithery field guide's book provider.
 *
 * <p>Runs during {@code runData}. The book sub-provider collects translation keys + English
 * defaults into an in-memory map as it builds; those strings are currently mirrored into
 * {@code en_us.json} by hand pending a real language provider.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryDatagen {

    /**
     * Adds the modonomicon book provider to the data generator.
     *
     * @param event NeoForge's client-side gather-data event
     */
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Client event) {
        Map<String, String> bookLang = new HashMap<>();
        BiConsumer<String, String> langCollector = bookLang::put;

        List<BookSubProvider> subProviders = List.of(
                new SmitheryBookSubProvider(langCollector)
        );

        BookProvider bookProvider = new BookProvider(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                Smithery.MODID,
                subProviders);
        event.getGenerator().addProvider(true, bookProvider);
    }

    private SmitheryDatagen() {}
}
