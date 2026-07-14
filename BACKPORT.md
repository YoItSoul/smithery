# Forge 1.20.1 Backport (`forge-1.20.1` branch)

This branch backports Smithery from NeoForge 26.1.2 to **MinecraftForge 47.4.10 / Minecraft 1.20.1**.

## Toolchain status (done)

- ForgeGradle `[6.0,6.2)` replaces ModDevGradle (Gradle wrapper downgraded 9.2.1 → 8.8; configuration cache disabled — FG6 does not support it).
- Java toolchain 17 (what 1.20.1 ships to players).
- `META-INF/mods.toml` template replaces `neoforge.mods.toml`; `pack.mcmeta` added (`pack_format` 15, required again on 1.20.1).
- Dependencies re-pinned to their 1.20.1 lines and wrapped in `fg.deobf`:
  - Modonomicon `1.79.3` (`modonomicon-1.20.1-forge`)
  - Geckolib `4.8.4` (`software.bernie.geckolib:geckolib-forge-1.20.1` — note the group change from `com.geckolib`)
  - JEI `15.20.0.134` (`jei-1.20.1-common-api` / `-forge-api` / `-forge`)

## Code port status (not started)

The Java sources are still the NeoForge 26.1 code and will not compile yet. Major migration areas, roughly in dependency order:

1. **Packages / bootstrap** — `net.neoforged.*` → `net.minecraftforge.*`; Forge has a split event bus (mod bus via `FMLJavaModLoadingContext`, game bus via `MinecraftForge.EVENT_BUS`) instead of NeoForge's unified bus.
2. **Language level** — source must compile on Java 17; any Java 18+ features (record patterns in switch, unnamed variables, etc.) need rewriting.
3. **Data components → NBT** — 1.20.1 has no data component system. Tool/armor composition storage (`ToolCompositions`, material parts, dye colors, soulbound flag) moves to item NBT with explicit (de)serialization.
4. **Attachments → capabilities** — NeoForge data attachments (e.g. the soulbound player attachment) become Forge capabilities; Forge 1.20.1 *does* have `PlayerEvent.Clone` for death persistence.
5. **Networking** — custom payloads/`StreamCodec` → Forge `SimpleChannel` with hand-written `FriendlyByteBuf` encode/decode (the forge packet handler hardening from `b0f8a94` needs re-verifying under SimpleChannel semantics).
6. **Capabilities on block entities** — forge multiblock item/fluid handlers switch to `getCapability`/`invalidateCaps` overrides instead of NeoForge's capability registration events.
7. **Recipes** — 1.20.1 recipe serializers have no codec support; `fromJson`/`fromNetwork` must be hand-written for forge melting / press recipes.
8. **Attributes & tiers** — `ItemAttributeModifiers`-style component data → `getAttributeModifiers` multimap; tool tiers/armor materials use the 1.20.1 `Tier`/`ArmorMaterial` interfaces.
9. **Client rendering** — no equipment-asset system in 1.20.1: dyed armor rendering goes through `HumanoidArmorLayer`/`IClientItemExtensions.getGenericArmorModel` + layered textures; fluid rendering uses `IClientFluidTypeExtensions`; check every `GuiGraphics` call against the 1.20.1 signatures (the fluid-atlas vs GUI blit split still applies).
10. **Third-party APIs** — Geckolib 5 → 4 (different package roots and animation controller API), Modonomicon 1.142 → 1.79 (book datagen API differences), JEI 29 → 15 (`IRecipeCategory` and ingredient API differences).
11. **Datagen** — 1.20.1 providers take `PackOutput` + lookup providers with different signatures; regenerate `src/generated/resources` on this branch rather than hand-editing.
12. **Gametests** — `forge.enabledGameTestNamespaces` + `@GameTestHolder`/`@PrefixGameTestTemplate` replace the NeoForge gametest registration.

Keep gameplay/balance identical to `main` — this branch changes platform code only. Fixes that aren't 1.20.1-specific should land on `main` first and be cherry-picked here.
