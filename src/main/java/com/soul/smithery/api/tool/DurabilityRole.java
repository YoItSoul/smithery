package com.soul.smithery.api.tool;

/**
 * How a part slot contributes to total tool durability.
 *
 * <p>Formula: {@code durability = (sum ADDITIVE parts) * (product MULTIPLIER parts) * (product modifier multipliers)}.
 */
public enum DurabilityRole {
    /** Adds the part's durability to the running sum. */
    ADDITIVE,
    /** Multiplies the running sum by the part's scalar. */
    MULTIPLIER
}
