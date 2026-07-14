package com.soul.smithery.item.tool;

import com.mojang.serialization.Codec;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierEffect;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * NBT accessors for Smithery's per-stack tool state.
 *
 * <p>Three pieces of state persist on a composed stack, each under its own namespaced key in
 * the stack tag and (de)serialized with the owning type's {@link Codec}:
 *
 * <ul>
 *   <li>{@link #getComposition composition} — the {@link ToolComposition} driving stats,
 *       rendering and behaviour;</li>
 *   <li>{@link #getAppliedModifiers applied modifiers} — post-craft
 *       {@link ModifierEffect}s, separate from at-craft material grants so the free-slot
 *       math stays simple;</li>
 *   <li>{@link #getModifierProgress modifier progress} — partial item counts toward the
 *       next level of an in-progress modifier, keyed by source-item id.</li>
 * </ul>
 *
 * <p>Stack NBT is synchronized to clients and persisted with the stack by vanilla, so these
 * accessors are the whole storage story. Malformed NBT decodes as absent rather than
 * throwing, mirroring how a removed datapack material leaves a composition invalid instead
 * of crashing.
 */
public final class SmitheryToolData {

    private static final String KEY_COMPOSITION       = Smithery.MODID + ":tool_composition";
    private static final String KEY_APPLIED_MODIFIERS = Smithery.MODID + ":applied_modifiers";
    private static final String KEY_MODIFIER_PROGRESS = Smithery.MODID + ":modifier_progress";

    private static final Codec<List<ModifierEffect>> APPLIED_MODIFIERS_CODEC =
            ModifierEffect.CODEC.listOf();
    private static final Codec<Map<ResourceLocation, Integer>> MODIFIER_PROGRESS_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);

    private SmitheryToolData() {}

    /** The stack's {@link ToolComposition}, or null when absent or unreadable. */
    public static @Nullable ToolComposition getComposition(ItemStack stack) {
        return read(stack, KEY_COMPOSITION, ToolComposition.CODEC, null);
    }

    /** True when the stack carries a decodable {@link ToolComposition}. */
    public static boolean hasComposition(ItemStack stack) {
        return getComposition(stack) != null;
    }

    /** Writes the stack's {@link ToolComposition}. */
    public static void setComposition(ItemStack stack, ToolComposition composition) {
        write(stack, KEY_COMPOSITION, ToolComposition.CODEC, composition);
    }

    /** The stack's post-craft modifier list; empty when absent or unreadable. */
    public static List<ModifierEffect> getAppliedModifiers(ItemStack stack) {
        return read(stack, KEY_APPLIED_MODIFIERS, APPLIED_MODIFIERS_CODEC, List.of());
    }

    /** Writes the stack's post-craft modifier list. */
    public static void setAppliedModifiers(ItemStack stack, List<ModifierEffect> modifiers) {
        write(stack, KEY_APPLIED_MODIFIERS, APPLIED_MODIFIERS_CODEC, modifiers);
    }

    /** The stack's partial modifier-application progress; empty when absent or unreadable. */
    public static Map<ResourceLocation, Integer> getModifierProgress(ItemStack stack) {
        return read(stack, KEY_MODIFIER_PROGRESS, MODIFIER_PROGRESS_CODEC, Map.of());
    }

    /** Writes the stack's partial modifier-application progress. */
    public static void setModifierProgress(ItemStack stack, Map<ResourceLocation, Integer> progress) {
        write(stack, KEY_MODIFIER_PROGRESS, MODIFIER_PROGRESS_CODEC, progress);
    }

    private static <T> T read(ItemStack stack, String key, Codec<T> codec, T fallback) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(key)) return fallback;
        return codec.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(fallback);
    }

    private static <T> void write(ItemStack stack, String key, Codec<T> codec, T value) {
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        if (encoded == null) {
            Smithery.LOGGER.error("Failed to encode {} for {}", key, stack);
            return;
        }
        stack.getOrCreateTag().put(key, encoded);
    }
}
