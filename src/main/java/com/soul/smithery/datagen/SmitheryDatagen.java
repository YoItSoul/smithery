package com.soul.smithery.datagen;

import com.google.gson.JsonObject;
import com.klikli_dev.modonomicon.api.datagen.BookProvider;
import com.klikli_dev.modonomicon.api.datagen.BookSubProvider;
import com.soul.smithery.Smithery;
import com.soul.smithery.book.SmitheryFieldGuideSubProvider;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Datagen entry point that wires up the Smithery field guide's book provider plus a lang
 * provider that dumps every collected book string into a generated {@code en_us.json}.
 * Minecraft merges lang files across packs, so the generated file coexists with the
 * hand-maintained one in {@code src/main/resources}.
 *
 * <p>Provider order matters: the data generator runs providers sequentially, so the lang
 * provider (registered second) sees the fully-populated string map.
 */
@EventBusSubscriber(modid = Smithery.MODID)
public final class SmitheryDatagen {

    /**
     * Adds the modonomicon book provider and the book-lang provider to the data generator.
     *
     * @param event NeoForge's client-side gather-data event
     */
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Client event) {
        Map<String, String> bookLang = new TreeMap<>();
        BiConsumer<String, String> langCollector = bookLang::put;

        List<BookSubProvider> subProviders = List.of(
                new SmitheryFieldGuideSubProvider(langCollector)
        );

        BookProvider bookProvider = new BookProvider(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                Smithery.MODID,
                subProviders);
        event.getGenerator().addProvider(true, bookProvider);

        event.getGenerator().addProvider(true, new com.soul.smithery.book.SmitheryMultiblockProvider(
                event.getGenerator().getPackOutput()));

        // Separate namespace dir: lang files merge across ALL namespaces at load, and using
        // smithery/lang here would collide with the hand-maintained file at processResources.
        Path langPath = event.getGenerator().getPackOutput().getOutputFolder()
                .resolve("assets/" + Smithery.MODID + "_book/lang/en_us.json");
        event.getGenerator().addProvider(true, new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                JsonObject json = new JsonObject();
                bookLang.forEach(json::addProperty);
                return DataProvider.saveStable(cache, json, langPath);
            }

            @Override
            public String getName() {
                return "Smithery Book Language";
            }
        });
    }

    private SmitheryDatagen() {}
}
