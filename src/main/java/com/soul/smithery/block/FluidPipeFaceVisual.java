package com.soul.smithery.block;

import net.minecraft.util.StringRepresentable;

/**
 * Rendered geometry for one face of a fluid pipe. Stored on the BlockState as an
 * EnumProperty per direction; the multipart blockstate routes each (face, visual)
 * pair to a separate model file.
 *
 * Five visual states:
 *   - {@link #NONE}: nothing rendered for this face. The multipart has no "apply"
 *     entry for this value, so the face contributes only the core cube (rendered
 *     unconditionally). Used for lone faces (air / inert walls) and DISCONNECTED
 *     faces toward non-connectable neighbours.
 *   - {@link #CAP_STUB}: tiny "topipe" cap stub at the face edge, no arm. Used to
 *     signal "I could connect to a real neighbour here, but the player has me set
 *     to DISCONNECTED."
 *   - {@link #ARM_TOPIPE}: arm + small topipe cap. Semantically "spigot / this face
 *     pushes fluid out" or neutral-bidirectional. Rendered for OUT-mode pipe joins,
 *     CONNECTED↔CONNECTED pipe joins, CONNECTED-side of a join where the neighbour is
 *     IN (we're being pulled from), and pipe→container OUT push.
 *   - {@link #ARM_TOOTHER}: arm + wider toother flange. Semantically "flange / this
 *     face receives fluid". Rendered for IN-mode pipe joins, CONNECTED-side of a join
 *     where the neighbour is OUT (we're being pushed into), and pipe→container IN/CONNECTED.
 *     Pairing rule: reading a join end-to-end as (spigot → flange) tells you the flow
 *     direction at that join.
 *   - {@link #ARM_OPEN}: arm only, no cap. Free-drip override above a casting table.
 *
 * The behavioural face mode (Connected/Disconnected/In/Out) lives separately on
 * the BlockEntity — multiple behaviour modes can collapse to the same visual.
 */
public enum FluidPipeFaceVisual implements StringRepresentable {
    NONE("none"),
    CAP_STUB("cap_stub"),
    ARM_TOPIPE("arm_topipe"),
    ARM_TOOTHER("arm_toother"),
    ARM_OPEN("arm_open");

    private final String name;

    FluidPipeFaceVisual(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
