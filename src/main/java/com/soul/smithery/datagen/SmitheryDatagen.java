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
 * Datagen entry point for the Smithery field guide.
 *
 * Registered on the mod bus so it fires during ./gradlew runData. The book
 * sub-provider collects translation keys + English defaults into an in-memory
 * map as it builds; we don't (yet) plumb those into a vanilla language
 * provider, so for now the strings need to be mirrored into
 * assets/smithery/lang/en_us.json by hand. That gap is acceptable for a
 * test entry — once we're happy with the book working in-game we'll wire
 * an actual LanguageProvider in to round-trip the strings.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryDatagen {

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
