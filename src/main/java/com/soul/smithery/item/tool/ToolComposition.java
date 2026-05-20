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

/**
 * Persisted composition of a Smithery tool: which ToolType it is and what material occupies
 * each of its slots, in the ToolType's declared slot order. Materials are referenced by id;
 * stats are looked up live so datapack overrides take effect on existing tools.
 *
 * Slot count must equal ToolType.slots().size(). Stacks that fail this invariant are treated
 * as broken (fall through to neutral defaults) — never thrown from, so a bad datapack can't
 * crash a player's inventory.
 */
public record ToolComposition(Identifier toolTypeId, List<Identifier> slotMaterials) {

    public ToolComposition {
        slotMaterials = List.copyOf(slotMaterials);
    }

    public static final Codec<ToolComposition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("tool_type").forGetter(ToolComposition::toolTypeId),
            Identifier.CODEC.listOf().fieldOf("slot_materials").forGetter(ToolComposition::slotMaterials)
    ).apply(i, ToolComposition::new));

    public static final StreamCodec<ByteBuf, ToolComposition> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ToolComposition::toolTypeId,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), ToolComposition::slotMaterials,
            ToolComposition::new
    );

    public ToolType toolType() {
        return SmitheryAPI.TOOL_TYPES.get(toolTypeId);
    }

    /** Materials in slot order; entries may be null if a referenced material was removed. */
    public List<Material> materials() {
        List<Material> out = new ArrayList<>(slotMaterials.size());
        for (Identifier id : slotMaterials) out.add(SmitheryAPI.MATERIALS.get(id));
        return Collections.unmodifiableList(out);
    }

    /** Distinct materials present across all slots (insertion order). Used for synergy matching. */
    public List<Identifier> distinctMaterials() {
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : slotMaterials) {
            if (id != null && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** True if every referenced slot material resolves to a registered Material. */
    public boolean isValid() {
        ToolType tt = toolType();
        if (tt == null || tt.slots().size() != slotMaterials.size()) return false;
        for (Identifier id : slotMaterials) {
            if (id == null || !SmitheryAPI.MATERIALS.contains(id)) return false;
        }
        return true;
    }
}
