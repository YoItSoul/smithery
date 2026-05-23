package com.soul.smithery.block;

/**
 * Behavioural mode for one face of a fluid pipe. Player-configurable via right-click
 * with a stick. Cycle order: CONNECTED → DISCONNECTED → IN → OUT → CONNECTED.
 *
 * Stored on the FluidPipeBlockEntity, packed two bits per face into a short (6 × 2 = 12 bits).
 */
public enum FluidPipeFaceMode {
    /** Bidirectional dynamics-driven flow. Down preferred over horizontal; up forbidden. */
    CONNECTED,
    /** No flow through this face. */
    DISCONNECTED,
    /** One-way input: fluid may only enter the pipe via this face. */
    IN,
    /** One-way output: fluid may only exit the pipe via this face (bypasses gravity rule). */
    OUT;

    private static final FluidPipeFaceMode[] VALUES = values();

    public FluidPipeFaceMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static FluidPipeFaceMode byOrdinal(int o) {
        return VALUES[Math.floorMod(o, VALUES.length)];
    }

    /** Player-facing label for chat feedback. */
    public String displayName() {
        return switch (this) {
            case CONNECTED    -> "Connected";
            case DISCONNECTED -> "Disconnected";
            case IN           -> "One-way IN";
            case OUT          -> "One-way OUT";
        };
    }

    public boolean allowsOutbound() { return this == CONNECTED || this == OUT; }
    public boolean allowsInbound()  { return this == CONNECTED || this == IN;  }
}
