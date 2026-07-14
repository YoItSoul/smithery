# Forge 1.20.1 Backport (`forge-1.20.1` branch)

This branch backports Smithery from NeoForge 26.1.2 to **MinecraftForge 47.4.10 / Minecraft 1.20.1**.

## Toolchain (done)

- ForgeGradle `[6.0,6.2)` replaces ModDevGradle (Gradle wrapper downgraded 9.2.1 → 8.8; configuration cache disabled — FG6 does not support it).
- Java toolchain 17 (what 1.20.1 ships to players).
- `META-INF/mods.toml` template replaces `neoforge.mods.toml`; `pack.mcmeta` added (`pack_format` 15, required again on 1.20.1).
- Dependencies pinned to the target modpack's 1.20.1 lines, wrapped in `fg.deobf`:
  - **Patchouli `1.20.1-85-FORGE` replaces Modonomicon** — the field guide is hand-authorable JSON under `patchouli_books/smithery_book/` (converted from the old generated book)
  - Geckolib `4.8.4` (`software.bernie.geckolib:geckolib-forge-1.20.1`)
  - JEI `15.20.0.134` (`jei-1.20.1-common-api` / `-forge-api` / `-forge`)

## Code port (done — `./gradlew build` green)

How each 26.1 system landed on 1.20.1:

1. **Bootstrap / registries** — no-arg `@Mod` constructor with `FMLJavaModLoadingContext`; plain `DeferredRegister` + `RegistryObject`.
2. **Per-stack state** — data components became codec-backed stack NBT (`SmitheryToolData`): composition, applied modifiers, modifier progress, composed max durability, and an extra-attribute channel for modifier-granted bonuses. Vanilla syncs stack NBT, so no custom component networking exists.
3. **Items** — stats served live through stack-sensitive overrides (`getAttributeModifiers`, `getDestroySpeed`, `isCorrectToolForDrops` via `TierSortingRegistry`, `getMaxDamage`). Armor extends `ArmorItem` with a zeroed placeholder material + `DyeableLeatherItem` worn tint.
4. **Soulbound** — a serializable Forge capability copied across `PlayerEvent.Clone`.
5. **Networking** — one `SimpleChannel` (`smithery:main`); the distance-check-before-`getBlockEntity` hardening is preserved.
6. **Block entities** — `IItemHandler`/`IFluidHandler` behind `getCapability` + `LazyOptional`; `CompoundTag` persistence.
7. **Recipes** — `ToolAssemblyRecipe` carries a recipe id and a hand-written `fromJson`/`fromNetwork` serializer.
8. **Client** — direct `BlockEntityRenderer.render` implementations; item tints via `RegisterColorHandlersEvent.Item` (tint index = composition slot); fluids via `FluidType.initializeClient`; Geckolib 4 `GeoBlockRenderer` for the part press.
9. **JEI 15** — `RecipeType` + slot-builder API; tool assembly cycles materials in lockstep through a focus link.
10. **Datagen** — removed; its only provider was the Modonomicon book, now static Patchouli JSON.

## Gameplay approximations (1.20.1 has no equivalent API)

Behavior-visible differences from `main`, each the closest 1.20.1 reconstruction:

- **Spear** — no vanilla spear mechanics; the thrust identity is +1.5 entity reach (`ForgeMod.ENTITY_REACH`) plus the composed attack-speed curve. Kinetic/piercing charge mechanics do not exist here.
- **Battlesign** — blocks via the shield pipeline (`SHIELD_BLOCK` tool action); `ShieldBlockEvent` scales blocked damage to 60%.
- **Block-drop modifiers** — no block-drops event on 1.20.1: XP-side hooks run at `BlockEvent.BreakEvent` (XP still writable); drop-side hooks run per spawned drop by correlating same-tick `ItemEntity` spawns near the break. Hooks see either an empty drop list with live XP or a one-drop list with inert XP.
- **Amphibious** — no oxygen attribute; reimplemented as level-scaled air replenishment while submerged.
- **Resin** — no resin item exists in 1.20.1; the material keeps its registration but has no press input (unobtainable without a datapack recipe).
- **Lunge** — plays the trident riptide sounds (no dedicated lunge sounds).
- **Mining wear** — tools take 2 durability per mined block, mirroring the pre-port TOOL component data.

Everything else keeps gameplay/balance identical to `main` — this branch changes platform code only. Fixes that aren't 1.20.1-specific should land on `main` first and be cherry-picked here.

## Remaining before release

- Runtime pass on a client: forge multiblock build/melt/drain, casting, part press animation, tool assembly + tints, armor rendering, controller GUI, JEI pages, Patchouli book.
- Sweep the deprecated `new ResourceLocation(...)` constructors to the statics Forge 47.4 backported (warnings only).
- Verify the runtime-generated pack's models/textures resolve on 1.20.1 (bow overrides, impressed sand voxel models).
