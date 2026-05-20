package com.soul.smithery.api.modifier;

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
}
