package com.soul.smithery.book;

/**
 * Constants for the Smithery field guide (a Modonomicon-backed in-game book).
 *
 * Players obtain it via {@code /give @s modonomicon:modonomicon_book{book_id:"smithery:smithery_book"}}
 * for now; a dedicated craftable book item can come later.
 */
public final class SmitheryBook {
    /** Resource path of the book inside the smithery namespace. */
    public static final String BOOK_ID = "smithery_book";

    private SmitheryBook() {}
}
