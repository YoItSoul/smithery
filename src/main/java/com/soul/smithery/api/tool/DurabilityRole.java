package com.soul.smithery.api.tool;

/**
 * Whether a part contributes additively to the total durability sum,
 * or applies a multiplicative scalar to the sum.
 *
 * Formula: durability = (Σ ADDITIVE parts) × Π MULTIPLIER parts × Π modifier multipliers
 */
public enum DurabilityRole {
    ADDITIVE,
    MULTIPLIER
}
