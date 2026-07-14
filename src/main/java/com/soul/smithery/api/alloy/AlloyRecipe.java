package com.soul.smithery.api.alloy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Auto-tick alloy recipe consumed by the forge's per-tick scanner.
 *
 * <p>When the forge contains every listed {@link Input} fluid at or above the required mB and the
 * temperature is at or above {@link #minTemperatureC}, the inputs are consumed and the
 * {@link Output} fluid is added to the forge's storage. Players don't trigger alloying directly;
 * they just dump fluids into the forge and the recipe fires when preconditions match.
 *
 * <p>JSON files live at {@code data/<namespace>/smithery/alloy/<name>.json}. {@code min_temperature_c}
 * is optional and defaults to 0 (no heat gate). Inputs and output mB are independent — recipes can
 * transform volume freely.
 *
 * @param inputs         required input fluids and their mB amounts
 * @param result         output fluid and its produced mB
 * @param minTemperatureC minimum forge temperature for the recipe to fire (0 = no gate)
 */
public record AlloyRecipe(List<Input> inputs, Output result, float minTemperatureC) {

    /** Compact constructor enforcing non-null fields and at least one input; defensively copies inputs. */
    public AlloyRecipe {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        if (inputs.isEmpty()) throw new IllegalArgumentException("alloy must have at least one input");
        inputs = List.copyOf(inputs);
    }

    /**
     * One required input fluid of an {@link AlloyRecipe}.
     *
     * @param material id of the input material's fluid
     * @param mb       milliBuckets of this fluid required per fire
     */
    public record Input(ResourceLocation material, int mb) {
        /** Codec for {@link Input}. */
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("material").forGetter(Input::material),
                Codec.INT.fieldOf("mb").forGetter(Input::mb)
        ).apply(i, Input::new));
    }

    /**
     * Output fluid of an {@link AlloyRecipe}.
     *
     * @param material id of the output material's fluid
     * @param mb       milliBuckets produced per fire
     */
    public record Output(ResourceLocation material, int mb) {
        /** Codec for {@link Output}. */
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("material").forGetter(Output::material),
                Codec.INT.fieldOf("mb").forGetter(Output::mb)
        ).apply(i, Output::new));
    }

    /** Codec for {@link AlloyRecipe}. */
    public static final Codec<AlloyRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Input.CODEC.listOf().fieldOf("inputs").forGetter(AlloyRecipe::inputs),
            Output.CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
            Codec.FLOAT.optionalFieldOf("min_temperature_c", 0.0f)
                    .forGetter(AlloyRecipe::minTemperatureC)
    ).apply(i, AlloyRecipe::new));

    /**
     * True if the given forge can fire this alloy right now: temperature high enough and every
     * input fluid present at or above the required mB.
     */
    public boolean canFire(float temperatureC, Map<ResourceLocation, Integer> fluidStorage) {
        if (temperatureC < minTemperatureC) return false;
        for (Input in : inputs) {
            if (fluidStorage.getOrDefault(in.material(), 0) < in.mb()) return false;
        }
        return true;
    }
}
