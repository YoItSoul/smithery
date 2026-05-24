package com.soul.smithery.api.alloy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * "When the forge contains all {@link Input} fluids at the specified mB amounts AND the
 * temperature is at or above {@link #minTemperatureC}, consume them and add the
 * {@link Output} fluid to the forge's storage."
 *
 * <h3>Auto-tick semantics</h3>
 * {@link com.soul.smithery.block.entity.ForgeControllerBlockEntity#serverTick} scans every
 * registered alloy on each server tick. If preconditions match, the alloy fires immediately
 * — players don't trigger alloying; they just dump fluids into the forge and the system
 * runs in the background.
 *
 * <h3>JSON schema</h3>
 * Files at {@code data/<namespace>/smithery/alloy/<name>.json}:
 * <pre>{@code
 *   {
 *     "inputs": [
 *       { "material": "smithery:gold",           "mb": 576 },
 *       { "material": "smithery:ancient_debris", "mb": 576 }
 *     ],
 *     "result": { "material": "smithery:netherite", "mb": 144 },
 *     "min_temperature_c": 2200
 *   }
 * }</pre>
 *
 * {@code min_temperature_c} is optional; defaults to 0 (no heat gate). Set it to the
 * output material's {@code meltingTemp} for natural tier-gating.
 *
 * <h3>Ratios</h3>
 * Inputs and output mB are independent — alloys can transform volume freely. The built-in
 * netherite recipe uses vanilla-matched ratios (576+576 → 144, mirroring 4 gold + 4
 * ancient debris → 1 netherite ingot in vanilla). Modders can choose 1:1:1 conservation
 * or any other ratio that suits their material economy.
 */
public record AlloyRecipe(List<Input> inputs, Output result, float minTemperatureC) {

    public AlloyRecipe {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        if (inputs.isEmpty()) throw new IllegalArgumentException("alloy must have at least one input");
        inputs = List.copyOf(inputs);
    }

    public record Input(Identifier material, int mb) {
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("material").forGetter(Input::material),
                Codec.INT.fieldOf("mb").forGetter(Input::mb)
        ).apply(i, Input::new));
    }

    public record Output(Identifier material, int mb) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("material").forGetter(Output::material),
                Codec.INT.fieldOf("mb").forGetter(Output::mb)
        ).apply(i, Output::new));
    }

    public static final Codec<AlloyRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Input.CODEC.listOf().fieldOf("inputs").forGetter(AlloyRecipe::inputs),
            Output.CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
            Codec.FLOAT.optionalFieldOf("min_temperature_c", 0.0f)
                    .forGetter(AlloyRecipe::minTemperatureC)
    ).apply(i, AlloyRecipe::new));

    /**
     * True if the given forge can fire this alloy right now: temperature high enough, every
     * input fluid present at ≥ the required mB.
     */
    public boolean canFire(float temperatureC, Map<Identifier, Integer> fluidStorage) {
        if (temperatureC < minTemperatureC) return false;
        for (Input in : inputs) {
            if (fluidStorage.getOrDefault(in.material(), 0) < in.mb()) return false;
        }
        return true;
    }
}
