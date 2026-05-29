package com.soul.smithery.book;

import com.klikli_dev.modonomicon.api.datagen.CategoryProvider;
import com.klikli_dev.modonomicon.api.datagen.LeafletEntryProvider;
import com.klikli_dev.modonomicon.api.datagen.LeafletSubProvider;
import com.klikli_dev.modonomicon.api.datagen.book.BookModel;
import com.soul.smithery.Smithery;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Datagen sub-provider for the single-category leaflet book.
 *
 * <p>Wires up the book's id, default-language strings, and its single welcome entry, and parks
 * the auto-generated book item under the Smithery blocks creative tab. Expands to a multi-category
 * field guide as more systems land.
 */
public class SmitheryBookSubProvider extends LeafletSubProvider {

    /**
     * Constructs the sub-provider with the modonomicon book id, mod namespace, and
     * a sink to receive default-language translation entries.
     *
     * @param defaultLang sink receiving (key, value) translation pairs for the default language
     */
    public SmitheryBookSubProvider(BiConsumer<String, String> defaultLang) {
        super(SmitheryBook.BOOK_ID, Smithery.MODID, defaultLang, Map.of());
    }

    @Override
    protected String bookName() {
        return "Smithery";
    }

    @Override
    protected String bookTooltip() {
        return "Smithery field guide";
    }

    @Override
    protected void registerDefaultMacros() {
    }

    @Override
    protected LeafletEntryProvider createEntryProvider(CategoryProvider categoryProvider) {
        return new SmitheryWelcomeEntryProvider(categoryProvider);
    }

    @Override
    protected BookModel additionalLeafletSetup(BookModel book) {
        return book.withCreativeTab(Identifier.fromNamespaceAndPath(Smithery.MODID, "blocks_tab"));
    }
}
