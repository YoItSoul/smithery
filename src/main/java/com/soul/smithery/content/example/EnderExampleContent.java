package com.soul.smithery.content.example;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.cast.CastResults;
import com.soul.smithery.api.cast.CastTemplates;
import com.soul.smithery.api.material.MaterialStats;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.part.PartType;
import com.soul.smithery.content.SmitheryModifiers;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.content.SmitheryToolTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * <h2>End-to-end modder example: ender as a fully-specified material</h2>
 *
 * This class is a <em>live, working reference</em> for modders who want to add their own
 * materials and cast types to smithery. It exercises every single builder method on
 * {@link MaterialStats} and {@link PartType} so a modder can read one file and see the
 * complete surface area of values they're allowed to touch — nothing is left implicit.
 *
 * <h3>What ender adds</h3>
 * <ul>
 *   <li><b>Material smithery:ender</b> — every {@link MaterialStats.Builder} setter is invoked
 *       below, with comments explaining what each one controls. Flagged {@code castOnly} so
 *       no auto-generated PartItems are produced (ender is shipped as a cast-only material).
 *       Auto-bootstraps a {@code smithery:molten_ender} fluid (block + bucket) via the
 *       standard {@code SmitheryFluids} pipeline because {@code meltingTemp > 0}.</li>
 *   <li><b>PartType smithery:pearl</b> — every {@link PartType.Builder} setter is invoked.
 *       Synthetic cast target — no PartItem is generated. Uses {@code minecraft:item/ender_pearl}
 *       as its impression-template texture, so the runtime sand voxelizer carves a pearl
 *       silhouette.</li>
 *   <li><b>CastResults entry</b> — pouring molten ender into a pearl cast yields a vanilla
 *       ender pearl. Other materials × pearl produce nothing (cast self-discards on retrieve).</li>
 *   <li><b>CastTemplates entry</b> — a vanilla ender pearl is now a valid impression template
 *       on a sand-prepared casting table.</li>
 *   <li><b>Melting recipe</b> — vanilla ender pearls melt into 64 mB of molten ender. Tuned
 *       break-even so 1 pearl → 1 pearl; alloy/efficiency mods can change the ratio.</li>
 * </ul>
 *
 * <h3>Player workflow</h3>
 * <ol>
 *   <li>Toss vanilla ender pearls into the Forge → smelted into molten ender.</li>
 *   <li>Click molten-ender in the controller GUI to make it the active output.</li>
 *   <li>Place casting sand on a casting table, right-click with a vanilla ender pearl
 *       to impress the pearl shape.</li>
 *   <li>Activate the drain → pipes light up only along the path to that table → molten
 *       ender pours in → cooling fades from teal-green to white.</li>
 *   <li>Right-click READY table → vanilla ender pearl drops into your inventory.</li>
 * </ol>
 *
 * <h3>How to adapt this for your own material</h3>
 * Copy this class into your mod, rename it, change {@code ENDER_MATERIAL_ID} / {@code PEARL_PART_TYPE_ID}
 * to your namespace, retune the stats, and call {@code YourExampleContent.register()} from
 * your mod constructor between smithery's {@code SmitheryMeltingRecipes.register()} call and
 * {@code SmitheryFluids.bootstrap()}.
 *
 * <h3>Timing</h3>
 * This must be invoked AFTER {@code SmitheryPartTypes.register()}, {@code SmitheryToolTypes.register()},
 * and {@code SmitheryModifiers.register()} (so the static handles we reference below are non-null)
 * and BEFORE {@code SmitheryFluids.bootstrap()} (so the fluid auto-registration sees the material).
 */
public final class EnderExampleContent {
    private EnderExampleContent() {}

    public static final Identifier ENDER_MATERIAL_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "ender");
    public static final Identifier PEARL_PART_TYPE_ID =
            Identifier.fromNamespaceAndPath(Smithery.MODID, "pearl");

    /** Cached PartType handle so other code (renderer / capabilities) can reference it. */
    public static PartType PEARL;

    public static void register() {
        // ════════════════════════════════════════════════════════════════════════════════
        //  1. PartType — every PartType.Builder setter exercised
        // ════════════════════════════════════════════════════════════════════════════════
        // PartType describes a "category" of tool part — sword_blade, pick_head, ingot,
        // pearl, etc. Each (Material × PartType) pair normally auto-generates a PartItem;
        // synthetic part types skip that step and resolve through CastResults instead.
        PEARL = SmitheryAPI.registerPartType(PartType.builder(PEARL_PART_TYPE_ID)
                // ── durabilityScalar ────────────────────────────────────────────────────
                // Multiplier applied to material.durabilityPerIngot when this part contributes
                // additively to a tool's durability. Heads = 1.0, accessories = 0.4, binders
                // = 0.0 (they multiply via Material.binderMultiplier instead). For synthetic
                // casts (ingot/nugget/pearl) we use 0 since the result is not a tool part.
                .durabilityScalar(0.0f)
                // ── partColorTint ───────────────────────────────────────────────────────
                // If true, the ItemColor handler tints this part's grayscale template texture
                // with Material.partColor at render time. For synthetic casts using a vanilla
                // texture (like ender_pearl which has its own colours) we disable tinting.
                .partColorTint(false)
                // ── castMb ──────────────────────────────────────────────────────────────
                // How many millibuckets of molten material it takes to fill this cast.
                // 144 mB = 1 ingot, 16 mB = 1 nugget. Pearl is small + cheap at 64 mB
                // (roughly 4 nuggets' worth — a "pearl"-sized portion).
                .castMb(64)
                // ── textureTemplate ─────────────────────────────────────────────────────
                // Identifier of the texture used by the sand voxelizer to carve the
                // impression silhouette. Reads /assets/<ns>/textures/<path>.png and turns
                // each non-transparent pixel into a sand voxel cavity. If omitted, defaults
                // to "<part_type_ns>:item/part/<part_type_path>".
                .textureTemplate(Identifier.fromNamespaceAndPath("minecraft", "item/ender_pearl"))
                // ── syntheticCast ───────────────────────────────────────────────────────
                // True = don't auto-generate a smithery PartItem. The cast outcome resolves
                // through CastResults (registered below) to whatever item the modder wants
                // — typically a vanilla item or another mod's item.
                .syntheticCast(true)
                .build());

        // ════════════════════════════════════════════════════════════════════════════════
        //  2. Material — every MaterialStats.Builder setter exercised
        // ════════════════════════════════════════════════════════════════════════════════
        // Even though `castOnly=true` means no auto-generated PartItems use these tool stats,
        // we fill every field so this class works as a complete reference. Downstream mods
        // that hand-roll PartItems against the ender material will read these stats; flipping
        // castOnly to false would immediately make ender a fully-functional tool material.
        SmitheryAPI.registerMaterial(ENDER_MATERIAL_ID, MaterialStats.builder()
                // ── harvestLevel ────────────────────────────────────────────────────────
                // Vanilla tier scale: 0 = bare hands, 1 = stone, 2 = iron, 3 = diamond,
                // 4 = netherite. Ender is mystical/dimensional — sits at diamond tier.
                .harvestLevel(3)
                // ── miningSpeed ─────────────────────────────────────────────────────────
                // Block-breaking speed multiplier for tool heads made of this material.
                // Iron = 6.5, diamond = 8.0, gold = 12.0. Ender is fast (teleportation-themed)
                // but not gold-fast.
                .miningSpeed(9.0f)
                // ── attackDamage ────────────────────────────────────────────────────────
                // Bonus attack damage added by a sword blade made of this material.
                // Iron = 2.0, diamond ≈ 3.0. Ender energy bites.
                .attackDamage(3.0f)
                // ── durabilityPerIngot ──────────────────────────────────────────────────
                // Base durability one ingot's worth of material contributes to a part with
                // durabilityScalar=1.0. Tools sum (durabilityPerIngot × scalar) across parts,
                // then multiply by the binder's binderMultiplier. Iron = 150, diamond ≈ 250.
                .durabilityPerIngot(220)
                // ── meltingTemp ─────────────────────────────────────────────────────────
                // °C the forge must reach to melt this material's source items. Drives
                // fluid auto-bootstrap (any material with meltingTemp > 0 gets a fluid).
                // Iron = 1538, gold = 1064. Ender is "low-temperature mystical" at 900°C.
                .meltingTemp(900f)
                // ── moltenColor ─────────────────────────────────────────────────────────
                // ARGB used to tint the generated fluid texture and pipe-interior render.
                // Bright teal-green matches the ender particle palette.
                .moltenColor(0xFF1FC891)
                // ── partColor ───────────────────────────────────────────────────────────
                // ARGB used by the ItemColor handler when partColorTint=true on a PartType.
                // For ender we set a lighter mint shade so any hand-rolled ender part would
                // be visually distinct from molten ender. If left at 0, MaterialStats
                // auto-derives a 70%-darker partColor from moltenColor.
                .partColor(0xFF8DDEC1)
                // ── binderMultiplier ────────────────────────────────────────────────────
                // When this material is used as the BINDER part of a tool, the tool's
                // total durability is multiplied by this value. Wood = 0.7 (poor binder),
                // iron = 1.0 (neutral), gold = 0.7 (soft). Ender's "warp-stitching" binds
                // tightly at 1.25.
                .binderMultiplier(1.25f)
                // ── castOnly ────────────────────────────────────────────────────────────
                // False → ender behaves as a full tool material: auto-generates a PartItem
                // for every PartType (smithery:ender_sword_blade, smithery:ender_pick_head,
                // ...), gets remelt recipes back to molten ender, and shows up as a valid
                // material in tool compositions. Tools made of ender will render with the
                // partColor tint (mint/teal) on each slot. Flip back to true to make ender
                // a fluid-only material whose only meaningful output is via CastResults
                // (vanilla ender pearl).
                .castOnly(false)
                // ── modifierSlots ───────────────────────────────────────────────────────
                // Per-PartType modifier slot contribution. The total slots on a finished
                // tool is the sum of contributions across its parts. Ender is a mystical
                // material, so it gives 2 slots on every standard part — generous for
                // post-craft modifier attachment. We register slots for every existing
                // part type so any combination is fully described.
                .modifierSlots(SmitheryPartTypes.SWORD_BLADE, 2)
                .modifierSlots(SmitheryPartTypes.GUARD, 2)
                .modifierSlots(SmitheryPartTypes.HANDLE, 2)
                .modifierSlots(SmitheryPartTypes.BINDER, 2)
                .modifierSlots(SmitheryPartTypes.PICK_HEAD, 2)
                // Synthetic part types don't produce tools, so slots are inert — but
                // documenting them keeps the surface area visible.
                .modifierSlots(SmitheryPartTypes.INGOT, 0)
                .modifierSlots(SmitheryPartTypes.NUGGET, 0)
                .modifierSlots(PEARL, 0)
                // ── addModifier (all three overloads demonstrated) ──────────────────────
                // (a) Full form — ToolType + ModifierEffect with parameters.
                //     VERDANT on the sword: 25% chance to apply Poison II for 80 ticks on hit.
                //     Mystical poison-on-hit is on-theme for ender.
                .addModifier(SmitheryToolTypes.SWORD,
                        ModifierEffect.of(SmitheryModifiers.VERDANT,
                                Map.of("chance", 0.25f, "duration_ticks", 80, "amplifier", 1)))
                // (b) Shorthand — ToolType + modifier Identifier, no params (uses modifier defaults).
                //     LUCKY_STRIKE on the sword: passive XP multiplier on kills (default 1.0).
                .addModifier(SmitheryToolTypes.SWORD, SmitheryModifiers.LUCKY_STRIKE)
                // (c) Shorthand with params — ToolType + Identifier + Map. Same effect as (a)
                //     but skips the ModifierEffect.of(...) wrapping. MAGNETIZED on the pickaxe:
                //     pulls drops within 8 blocks toward the player — "teleport drops home"
                //     fits ender's theme perfectly.
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.MAGNETIZED,
                        Map.of("radius", 8.0f))
                // GILDED on the pickaxe: passive XP multiplier on ore mining (default 1.0).
                .addModifier(SmitheryToolTypes.PICKAXE, SmitheryModifiers.GILDED)
                .build());

        // ════════════════════════════════════════════════════════════════════════════════
        //  3. Cast result mapping — (Material × PartType) → result Item
        // ════════════════════════════════════════════════════════════════════════════════
        // Pour molten ender into a pearl-shaped cast → vanilla ender pearl drops out.
        // The Supplier<Item> is invoked lazily on cast retrieval, so it's safe to use
        // Items.* references even though the registry might not be fully built at register
        // time (Items class statics are populated by Mojang's bootstrap before any mod's
        // FMLCommonSetupEvent fires).
        CastResults.register(ENDER_MATERIAL_ID, PEARL_PART_TYPE_ID, () -> Items.ENDER_PEARL);

        // ════════════════════════════════════════════════════════════════════════════════
        //  4. Cast template mapping — held Item → PartType to impress
        // ════════════════════════════════════════════════════════════════════════════════
        // When a player right-clicks a sand-prepared casting table with a vanilla ender
        // pearl, this mapping tells the table "impress the pearl shape into the sand".
        CastTemplates.register(Items.ENDER_PEARL, PEARL_PART_TYPE_ID);

        // ════════════════════════════════════════════════════════════════════════════════
        //  5. Melting recipe — input Item → (Material, mB)
        // ════════════════════════════════════════════════════════════════════════════════
        // 1 vanilla ender pearl → 64 mB molten ender. The pearl cast is also 64 mB, so the
        // material conserves perfectly: melt a pearl, cast a pearl. Alloy or efficiency mods
        // can adjust this ratio (e.g. 1 pearl → 96 mB for "ender refinement").
        SmitheryAPI.registerMeltingRecipe("minecraft:ender_pearl", ENDER_MATERIAL_ID.toString(), 64);
    }
}
