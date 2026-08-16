package com.soul.smithery.block;

import net.minecraft.util.StringRepresentable;

/**
 * What the forge controller shows on its face. Purely cosmetic; the simulation reads
 * the block entity, never this. Stored as an EnumProperty so the blockstate can route
 * each value to its own model.
 *
 * <p>Three values rather than a simple lit/unlit boolean because the face has two
 * independent things to report, and a boolean can only carry one: whether the player
 * assembled the multiblock correctly, and whether it is currently burning fuel. A
 * freshly-built but unfuelled forge is the exact moment a player most needs to know
 * the structure is valid.
 */
public enum ForgeControllerStatus implements StringRepresentable {
    /** No valid structure. Dark window, dead indicators. */
    IDLE("idle"),
    /** Structure is valid but not burning. Indicators lit, window still cold. */
    FORMED("formed"),
    /** Valid and consuming fuel. Window full of ember. */
    BURNING("burning");

    private final String name;

    ForgeControllerStatus(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
