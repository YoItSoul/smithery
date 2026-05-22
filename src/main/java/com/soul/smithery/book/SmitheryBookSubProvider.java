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
 * Single-category, single-entry "leaflet" book for Smithery.
 * Will grow into a multi-category field guide as more systems land.
 */
public class SmitheryBookSubProvider extends LeafletSubProvider {

    public SmitheryBookSubProvider(BiConsumer<String, String> defaultLang) {
        // SingleBookSubProvider(bookId, modId, defaultLang, otherLangs).
        // bookId is the per-book folder name (data/<modId>/modonomicon/books/<bookId>/).
        // modId is the data namespace; both also feed translation keys
        // (book.<modId>.<bookId>.name, etc).
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
        // No macros for now.
    }

    @Override
    protected LeafletEntryProvider createEntryProvider(CategoryProvider categoryProvider) {
        return new SmitheryWelcomeEntryProvider(categoryProvider);
    }

    @Override
    protected BookModel additionalLeafletSetup(BookModel book) {
        // Park the auto-generated book item under our own creative tab so players
        // can grab it from the Smithery Forge tab instead of having to /give it
        // (or dig through the Modonomicon tab).
        return book.withCreativeTab(Identifier.fromNamespaceAndPath(Smithery.MODID, "forge_tab"));
    }
}
