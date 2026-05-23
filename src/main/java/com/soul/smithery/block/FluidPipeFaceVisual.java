package com.soul.smithery.block;

import net.minecraft.util.StringRepresentable;

/**
 * Rendered geometry for one face of a fluid pipe. Stored on the BlockState as an
 * EnumProperty per direction; the multipart blockstate routes each (face, visual)
 * pair to a separate model file.
 *
 * Three states cover everything in the new "pipes-are-just-channels" model:
 *   - {@link #NONE}: nothing rendered. Multipart has no apply entry, so only the
 *     centre cube is drawn. Used for faces with no connectable neighbour.
 *   - {@link #ARM_OPEN}: arm extending to the block edge with no cap. Used for
 *     connections to another pipe (continuous tube) and the down face above a
 *     casting table (free-drip into the smart sink).
 *   - {@link #ARM_TOOTHER}: arm + wider toother flange. Used for connections to
 *     a foreign fluid container (forge drain, fuel port, tank, etc) — visually
 *     reads as "interface to a non-pipe block".
 *
 * Directionality and one-way valves have been removed from the pipe — those
 * concerns are owned by the source endpoint (the forge drain pumps; sinks self-
 * gate). Future power-user blocks (Spout, Valve, Filter) can layer on top.
 */
public enum FluidPipeFaceVisual implements StringRepresentable {
    NONE("none"),
    ARM_OPEN("arm_open"),
    ARM_TOOTHER("arm_toother");

    private final String name;

    FluidPipeFaceVisual(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
