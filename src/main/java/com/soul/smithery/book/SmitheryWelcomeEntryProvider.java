package com.soul.smithery.book;

import com.klikli_dev.modonomicon.api.datagen.CategoryProviderBase;
import com.klikli_dev.modonomicon.api.datagen.LeafletEntryProvider;
import com.klikli_dev.modonomicon.api.datagen.book.page.BookTextPageModel;

/**
 * The single welcome entry in the leaflet book. Just a placeholder text page
 * — enough to verify the datagen + load pipeline works end-to-end. Real
 * content lands once we know the book displays correctly in-game.
 */
public class SmitheryWelcomeEntryProvider extends LeafletEntryProvider {

    public SmitheryWelcomeEntryProvider(CategoryProviderBase parent) {
        super(parent);
    }

    @Override
    protected void generatePages() {
        this.page(BookTextPageModel.create()
                .withTitle(this.context().pageTitle())
                .withText(this.context().pageText()));
        this.pageTitle("Welcome to Smithery");
        this.pageText(
                "This is a placeholder entry to verify the field guide loads. "
                + "Real content for materials, parts, the forge and the casting table "
                + "lands once the rest of the build sequence is wired up.");
    }

    @Override
    protected String entryName() {
        return "Welcome";
    }
}
