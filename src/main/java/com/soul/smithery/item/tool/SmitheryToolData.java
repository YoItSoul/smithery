package com.soul.smithery.item.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.ModifierEffect;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    private static final String KEY_MAX_DURABILITY    = Smithery.MODID + ":max_durability";
    private static final String KEY_EXTRA_ATTRIBUTES  = Smithery.MODID + ":extra_attributes";

    private static final Codec<List<ModifierEffect>> APPLIED_MODIFIERS_CODEC =
            ModifierEffect.CODEC.listOf();
    private static final Codec<Map<ResourceLocation, Integer>> MODIFIER_PROGRESS_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);

    private SmitheryToolData() {}

    /**
     * Single-entry decode memo for {@link #getComposition}, one per thread.
     *
     * <p>Keyed on the <em>identity of the composition sub-tag</em>, not the root tag:
     * {@link #write} replaces the sub-tag while mutating the root in place, so root identity
     * survives a re-compose but the sub-tag's does not — which is exactly the invalidation
     * signal wanted. {@code ItemStack.copy} deep-copies the tag, so a copy misses and re-decodes.
     *
     * <p>Per-thread and single-entry deliberately. {@code getDestroySpeed} and the item colour
     * handler run on both the render and server threads, and a shared identity map keyed on
     * {@code Tag} would both race and leak the tags it held. {@link ToolComposition} is a record
     * whose list is copied in its canonical constructor, so handing the same instance to every
     * caller is safe.
     */
    private record CompositionMemo(Tag source, @Nullable ToolComposition value) {}

    private static final ThreadLocal<CompositionMemo> COMPOSITION_MEMO = new ThreadLocal<>();

    /**
     * The stack's {@link ToolComposition}, or null when absent or unreadable.
     *
     * <p>Decoded through a memo because this sits on genuinely hot paths: the item colour handler
     * is invoked once per tinted quad while rendering, and the modifier dispatch runs it for six
     * equipment slots per player per tick.
     */
    public static @Nullable ToolComposition getComposition(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) return null;
        Tag encoded = tag.get(KEY_COMPOSITION);
        if (encoded == null) return null;
        CompositionMemo memo = COMPOSITION_MEMO.get();
        if (memo != null && memo.source() == encoded) return memo.value();
        ToolComposition parsed = ToolComposition.CODEC
                .parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        COMPOSITION_MEMO.set(new CompositionMemo(encoded, parsed));
        return parsed;
    }

    /** True when the stack carries a decodable {@link ToolComposition}. */
    public static boolean hasComposition(ItemStack stack) {
        return getComposition(stack) != null;
    }

    /**
     * True when the composition tag is merely <em>present</em>, without decoding it.
     *
     * <p>For callers that only need to know whether a stack is Smithery gear at all — the
     * per-tick modifier dispatch, which immediately goes on to read the composition properly
     * when this passes.
     */
    public static boolean hasCompositionTag(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.contains(KEY_COMPOSITION);
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

    /**
     * The composed max durability written at compose time, or {@code fallback} when absent.
     * Every smithery item family's {@code getMaxDamage(ItemStack)} override serves this value.
     */
    public static int getMaxDurability(ItemStack stack, int fallback) {
        var tag = stack.getTag();
        if (tag != null && tag.contains(KEY_MAX_DURABILITY)) {
            return tag.getInt(KEY_MAX_DURABILITY);
        }
        return fallback;
    }

    /** Writes the composed max durability served by the items' {@code getMaxDamage} overrides. */
    public static void setMaxDurability(ItemStack stack, int maxDurability) {
        stack.getOrCreateTag().putInt(KEY_MAX_DURABILITY, maxDurability);
    }

    /** The stack's partial modifier-application progress; empty when absent or unreadable. */
    public static Map<ResourceLocation, Integer> getModifierProgress(ItemStack stack) {
        return read(stack, KEY_MODIFIER_PROGRESS, MODIFIER_PROGRESS_CODEC, Map.of());
    }

    /** Writes the stack's partial modifier-application progress. */
    public static void setModifierProgress(ItemStack stack, Map<ResourceLocation, Integer> progress) {
        write(stack, KEY_MODIFIER_PROGRESS, MODIFIER_PROGRESS_CODEC, progress);
    }

    /**
     * One modifier-granted attribute bonus riding the stack, applied on top of the composed
     * base attributes by the item's {@code getAttributeModifiers} override. Named entries are
     * unique per stack — recomposition clears and re-adds them, so compose hooks stay
     * idempotent.
     *
     * @param name        unique entry name; also seeds the attribute modifier's stable UUID
     * @param attributeId registry id of the attribute to modify
     * @param amount      modifier amount
     * @param operation   modifier operation
     * @param slot        equipment slot the bonus applies in
     */
    public record ExtraAttribute(String name, ResourceLocation attributeId, double amount,
                                 AttributeModifier.Operation operation, EquipmentSlot slot) {

        /**
         * Operation and slot codecs that <em>fail</em> on bad input rather than throwing.
         *
         * <p>{@code Operation.fromValue} and {@code EquipmentSlot.byName} both throw
         * {@code IllegalArgumentException}, and DFU's {@code xmap} does not wrap exceptions — so
         * with {@code xmap} the throw escaped {@code codec.parse} instead of becoming a failed
         * {@code DataResult}, breaking this class's contract that malformed NBT reads as absent.
         * That surfaced out of {@code getAttributeModifiers}, which vanilla calls while building
         * a tooltip, so hovering a hand-edited stack hard-crashed the client.
         *
         * <p>Note {@code EXTRA_ATTRIBUTES_CODEC} is a {@code listOf()}: one bad entry fails the
         * whole list, which then reads as absent. All-or-nothing, not per-entry recovery.
         */
        private static final Codec<AttributeModifier.Operation> OPERATION_CODEC =
                Codec.INT.comapFlatMap(
                        v -> (v >= 0 && v < AttributeModifier.Operation.values().length)
                                ? DataResult.success(AttributeModifier.Operation.fromValue(v))
                                : DataResult.error(() -> "Unknown attribute operation: " + v),
                        AttributeModifier.Operation::toValue);

        private static final Codec<EquipmentSlot> SLOT_CODEC =
                Codec.STRING.comapFlatMap(
                        name -> {
                            for (EquipmentSlot slot : EquipmentSlot.values()) {
                                if (slot.getName().equals(name)) return DataResult.success(slot);
                            }
                            return DataResult.error(() -> "Unknown equipment slot: " + name);
                        },
                        EquipmentSlot::getName);

        /** Codec for the extra-attribute NBT list. */
        public static final Codec<ExtraAttribute> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(ExtraAttribute::name),
                ResourceLocation.CODEC.fieldOf("attribute").forGetter(ExtraAttribute::attributeId),
                Codec.DOUBLE.fieldOf("amount").forGetter(ExtraAttribute::amount),
                OPERATION_CODEC.fieldOf("operation").forGetter(ExtraAttribute::operation),
                SLOT_CODEC.fieldOf("slot").forGetter(ExtraAttribute::slot)
        ).apply(i, ExtraAttribute::new));
    }

    private static final Codec<List<ExtraAttribute>> EXTRA_ATTRIBUTES_CODEC =
            ExtraAttribute.CODEC.listOf();

    /** The stack's modifier-granted attribute bonuses; empty when absent or unreadable. */
    public static List<ExtraAttribute> getExtraAttributes(ItemStack stack) {
        return read(stack, KEY_EXTRA_ATTRIBUTES, EXTRA_ATTRIBUTES_CODEC, List.of());
    }

    /** Adds (or replaces, by name) one modifier-granted attribute bonus on the stack. */
    public static void putExtraAttribute(ItemStack stack, ExtraAttribute attribute) {
        List<ExtraAttribute> current = new ArrayList<>(getExtraAttributes(stack));
        current.removeIf(e -> e.name().equals(attribute.name()));
        current.add(attribute);
        write(stack, KEY_EXTRA_ATTRIBUTES, EXTRA_ATTRIBUTES_CODEC, current);
    }

    /** Clears all modifier-granted attribute bonuses; called before compose hooks re-add them. */
    public static void clearExtraAttributes(ItemStack stack) {
        stack.removeTagKey(KEY_EXTRA_ATTRIBUTES);
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
