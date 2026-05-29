package com.soul.smithery.block;

import net.minecraft.util.StringRepresentable;

/**
 * Rendered geometry for one face of a {@link FluidPipeBlock}. Stored as an EnumProperty
 * per direction; the multipart blockstate routes each (face, visual) pair to its own
 * model file.
 */
public enum FluidPipeFaceVisual implements StringRepresentable {
    /** No geometry on this face; only the centre cube is drawn. */
    NONE("none"),
    /** Arm extending to the block edge without a cap, used for pipe-to-pipe runs and
     *  the down face above a casting table. */
    ARM_OPEN("arm_open"),
    /** Arm plus a wider toother flange, used when connecting to a foreign fluid container. */
    ARM_TOOTHER("arm_toother");

    private final String name;

    FluidPipeFaceVisual(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
