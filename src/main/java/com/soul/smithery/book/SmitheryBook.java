package com.soul.smithery.book;

/**
 * Constants identifying the Smithery field guide, a Modonomicon-backed in-game book.
 *
 * <p>Currently obtainable only through {@code /give} for the modonomicon book item with
 * a matching {@code book_id}; a craftable book item may follow.
 */
public final class SmitheryBook {
    /** Resource path of the book inside the smithery namespace. */
    public static final String BOOK_ID = "smithery_book";

    private SmitheryBook() {}
}
