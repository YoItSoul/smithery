package com.soul.smithery.book;

import com.klikli_dev.modonomicon.api.datagen.CategoryProviderBase;
import com.klikli_dev.modonomicon.api.datagen.LeafletEntryProvider;
import com.klikli_dev.modonomicon.api.datagen.book.page.BookTextPageModel;

/**
 * Datagen provider for the single welcome entry of the leaflet book.
 *
 * <p>Acts as a placeholder text page that verifies the datagen and load pipeline end-to-end;
 * real material/forge/casting content lands once the rest of the build sequence is stable.
 */
public class SmitheryWelcomeEntryProvider extends LeafletEntryProvider {

    /**
     * Constructs the entry under the given parent category provider.
     *
     * @param parent parent category provider supplying datagen context
     */
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
