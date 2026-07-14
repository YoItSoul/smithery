package com.soul.smithery.api.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One configured instance of a registered {@link Modifier} with a specific parameter set.
 *
 * <p>The {@link Modifier} defines behavior (passive stat, active event hook, durability
 * multiplier); a ModifierEffect attaches one configured instance of that behavior to a
 * (material, ToolType) pair, a synergy, or a post-craft modifier item. Params are stored as a
 * boxed map and read via the {@code paramX(name, default)} accessors.
 */
public final class ModifierEffect {
    private final ResourceLocation modifierId;
    private final Map<String, Object> params;

    /** Constructs an effect referencing {@code modifierId} with the given parameter map. */
    public ModifierEffect(ResourceLocation modifierId, Map<String, Object> params) {
        this.modifierId = Objects.requireNonNull(modifierId, "modifierId");
        this.params = params == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(params));
    }

    /** ResourceLocation of the {@link Modifier} this effect references. */
    public ResourceLocation modifierId() { return modifierId; }

    /** Unmodifiable view of this effect's parameter map. */
    public Map<String, Object> params() { return params; }

    /** Raw parameter lookup (returns {@code null} if missing). */
    public Object param(String key) { return params.get(key); }

    /** Reads a float-typed parameter, returning {@code def} when missing or non-numeric. */
    public float paramFloat(String key, float def) {
        Object v = params.get(key);
        return v instanceof Number n ? n.floatValue() : def;
    }

    /** Reads an int-typed parameter, returning {@code def} when missing or non-numeric. */
    public int paramInt(String key, int def) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    /** Reads a boolean-typed parameter, returning {@code def} when missing or non-boolean. */
    public boolean paramBool(String key, boolean def) {
        Object v = params.get(key);
        return v instanceof Boolean b ? b : def;
    }

    /** Convenience factory for a parameterless effect. */
    public static ModifierEffect of(ResourceLocation id) { return new ModifierEffect(id, Map.of()); }

    /** Convenience factory carrying parameters. */
    public static ModifierEffect of(ResourceLocation id, Map<String, Object> params) { return new ModifierEffect(id, params); }

    @Override public String toString() { return "ModifierEffect[" + modifierId + ", " + params + "]"; }

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

    /** Codec used for persistence (data components) and JSON serialization. Params encode as a string-to-float map. */
    public static final Codec<ModifierEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ModifierEffect::modifierId),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
                    .optionalFieldOf("params", Map.of())
                    .forGetter(e -> objectMapToFloatMap(e.params()))
    ).apply(i, (id, params) -> new ModifierEffect(id, floatMapToObjectMap(params))));

    /** Stream codec used for network sync. */
    public static final StreamCodec<ByteBuf, ModifierEffect> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,                                  ModifierEffect::modifierId,
            ByteBufCodecs.map(java.util.HashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT),  e -> objectMapToFloatMap(e.params()),
            (id, params) -> new ModifierEffect(id, floatMapToObjectMap(params))
    );
}
