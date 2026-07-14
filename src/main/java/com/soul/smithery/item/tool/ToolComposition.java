package com.soul.smithery.item.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.tool.ToolType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Persisted composition of a Smithery tool: the {@link ToolType} id plus the material
 * id occupying each slot in the ToolType's declared slot order. Materials are looked
 * up live so datapack overrides take effect on existing tools. Invariant violations
 * (slot count mismatch, unknown material id) leave the composition invalid rather than
 * throwing.
 *
 * @param toolTypeId       id of the bound ToolType
 * @param slotMaterials    material ids per slot, in the ToolType's declared order
 * @param embossedMaterial optional donor material whose traits are grafted onto the tool
 *                         (stats untouched); replaceable at the anvil with a new donor part
 */
public record ToolComposition(Identifier toolTypeId, List<Identifier> slotMaterials,
                              Optional<Identifier> embossedMaterial) {

    /**
     * Canonical constructor that defensively copies {@code slotMaterials} to an
     * immutable list.
     */
    public ToolComposition {
        slotMaterials = List.copyOf(slotMaterials);
    }

    /** Convenience constructor for compositions without an embossment. */
    public ToolComposition(Identifier toolTypeId, List<Identifier> slotMaterials) {
        this(toolTypeId, slotMaterials, Optional.empty());
    }

    /** Returns a copy of this composition with the embossed material replaced. */
    public ToolComposition withEmbossment(Identifier donorMaterial) {
        return new ToolComposition(toolTypeId, slotMaterials, Optional.of(donorMaterial));
    }

    /** Codec for persistence into ItemStack data components. */
    public static final Codec<ToolComposition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("tool_type").forGetter(ToolComposition::toolTypeId),
            Identifier.CODEC.listOf().fieldOf("slot_materials").forGetter(ToolComposition::slotMaterials),
            Identifier.CODEC.optionalFieldOf("embossed").forGetter(ToolComposition::embossedMaterial)
    ).apply(i, ToolComposition::new));

    /** Stream codec for network sync. */
    public static final StreamCodec<ByteBuf, ToolComposition> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ToolComposition::toolTypeId,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), ToolComposition::slotMaterials,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), ToolComposition::embossedMaterial,
            ToolComposition::new
    );

    /** Resolves the live {@link ToolType}, or null if the id is unregistered. */
    public ToolType toolType() {
        return SmitheryAPI.TOOL_TYPES.get(toolTypeId);
    }

    /**
     * Returns the materials in slot order; entries may be null if a referenced material
     * was removed from the registry.
     */
    public List<Material> materials() {
        List<Material> out = new ArrayList<>(slotMaterials.size());
        for (Identifier id : slotMaterials) out.add(SmitheryAPI.MATERIALS.get(id));
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns the distinct material ids across all slots in insertion order; used for
     * synergy matching.
     */
    public List<Identifier> distinctMaterials() {
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : slotMaterials) {
            if (id != null && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /**
     * True iff every referenced material resolves to a registered {@link Material} and
     * the slot count matches the bound ToolType's slot list.
     */
    public boolean isValid() {
        ToolType tt = toolType();
        if (tt == null || tt.slots().size() != slotMaterials.size()) return false;
        for (Identifier id : slotMaterials) {
            if (id == null || !SmitheryAPI.MATERIALS.contains(id)) return false;
        }
        return true;
    }
}
