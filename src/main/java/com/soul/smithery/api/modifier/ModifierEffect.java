package com.soul.smithery.api.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An instance of a registered Modifier with a specific parameter set.
 *
 * The Modifier itself defines behavior (passive stat, active event hook, durability multiplier).
 * The ModifierEffect attaches one configured instance of that behavior to a (material, ToolType)
 * pair, a synergy, or a post-craft modifier item.
 */
public final class ModifierEffect {
    private final Identifier modifierId;
    private final Map<String, Object> params;

    public ModifierEffect(Identifier modifierId, Map<String, Object> params) {
        this.modifierId = Objects.requireNonNull(modifierId, "modifierId");
        this.params = params == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(params));
    }

    public Identifier modifierId() { return modifierId; }
    public Map<String, Object> params() { return params; }

    public Object param(String key) { return params.get(key); }
    public float paramFloat(String key, float def) {
        Object v = params.get(key);
        return v instanceof Number n ? n.floatValue() : def;
    }
    public int paramInt(String key, int def) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }
    public boolean paramBool(String key, boolean def) {
        Object v = params.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public static ModifierEffect of(Identifier id) { return new ModifierEffect(id, Map.of()); }
    public static ModifierEffect of(Identifier id, Map<String, Object> params) { return new ModifierEffect(id, params); }

    @Override public String toString() { return "ModifierEffect[" + modifierId + ", " + params + "]"; }

    // ---- Codecs for persistence (data components) + network sync ----
    //
    // Params are serialized as a String → Float map. This covers every currently-registered
    // modifier param (chance, radius, duration_ticks, damage, etc.). int-typed params survive
    // because the param accessor (paramInt) casts the stored Number via floatValue(). Booleans
    // are not supported in v1 — no modifier currently uses them; if one's added later, extend
    // the codec to a tagged variant rather than a raw float map.

    private static Map<String, Object> floatMapToObjectMap(Map<String, Float> floats) {
        Map<String, Object> out = new HashMap<>(floats.size());
        for (Map.Entry<String, Float> e : floats.entrySet()) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static Map<String, Float> objectMapToFloatMap(Map<String, Object> objs) {
        Map<String, Float> out = new HashMap<>(objs.size());
        for (Map.Entry<String, Object> e : objs.entrySet()) {
            if (e.getValue() instanceof Number n) out.put(e.getKey(), n.floatValue());
        }
        return out;
    }

    public static final Codec<ModifierEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ModifierEffect::modifierId),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
                    .optionalFieldOf("params", Map.of())
                    .forGetter(e -> objectMapToFloatMap(e.params()))
    ).apply(i, (id, params) -> new ModifierEffect(id, floatMapToObjectMap(params))));

    public static final StreamCodec<ByteBuf, ModifierEffect> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,                                  ModifierEffect::modifierId,
            ByteBufCodecs.map(java.util.HashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT),  e -> objectMapToFloatMap(e.params()),
            (id, params) -> new ModifierEffect(id, floatMapToObjectMap(params))
    );
}
