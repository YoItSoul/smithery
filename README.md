<div align="center">

# Smithery

**Modular tool crafting for NeoForge.** Build tools from interchangeable material parts in a multiblock forge.

[![Minecraft][mc-badge]][mc-link]
[![NeoForge][nf-badge]][nf-link]
[![Java][java-badge]][java-link]
[![License][lic-badge]](LICENSE)
[![Ko-Fi][kofi-badge]][kofi-link]

</div>

---

## What is Smithery?

Smithery is a modular, modder-friendly tool crafting mod centered on a multiblock **Forge**. Inspired by the spirit of Tinkers' Construct but rebuilt from the ground up for NeoForge 26.1.2 with a clean Java API, runtime asset generation, and growing datapack override support (alloys and modifiers today; more to come).

- **Mixed-material tools.** Every tool is assembled from interchangeable parts — blade, guard, handle, binder, head. Pair an iron blade with a gold handle, a copper binder, and the stats, modifiers, and visuals stack accordingly.
- **Per-material, per-tool-type modifiers.** Iron sword swings sharper, iron pickaxe magnetizes drops, copper poisons, gold gilds XP. Materials carry different abilities depending on which tool they're used in.
- **Material synergies.** Specific cross-material combinations unlock bonus modifiers — Galvanic (Iron + Copper), Gilded (Iron + Gold), Verdant Veil (Copper + Gold) — at no slot cost.
- **Multiblock Forge.** Built from Furnace Bricks plus the controller, fuel ports, drains, and item ports. Validates open-top or closed-top structures, tolerates partial builds, and allows walls without corner blocks. Melts items dropped inside (or fed through item ports), auto-alloys, scalds mobs into fluids, and drains into a fluid-pipe network.
- **Sand casting & Part Press.** Shape molten metal on the Casting Table — impress a part into casting sand, pour, let it cool, brush it clean. Non-meltable materials (wood, flint, slime, coral) are cut on the redstone-driven Part Press instead.
- **Designed for modders.** Register new materials, parts, tool types, modifiers, synergies, and alloys via a builder-style Java API. Models, item definitions, and tinted textures are generated at runtime from the live registry — no boilerplate JSON per material.

---

## Current status

Smithery is in active development. The roadmap is documented in [`SMITHERY_DESIGN.md`](SMITHERY_DESIGN.md).

| Phase | Status          |
|---|-----------------|
| Materials (28 built-in), parts, tools, modifiers, synergies | ✅ implemented   |
| Crafting (shapeless tool assembly at the crafting table) | ✅ implemented   |
| Multiblock Forge validator (open / closed / partial / no-corner) | ✅ implemented   |
| Heat / fuel / melting simulation (multi-fuel, temperature-gated melt rates) | ✅ implemented   |
| Molten fluids (auto-registered per material, tinted, with buckets) | ✅ implemented   |
| Alloying (data-driven recipes, in-forge auto-processing) | ✅ implemented   |
| Forge controller GUI (slots, tank, temp, fuel, alloy toggle) | ✅ implemented   |
| Forge drain (redstone pump → fluid pipe network) + fluid pipes | ✅ implemented   |
| Sand casting (casting table: impress → pour → cool → brush) | ✅ implemented   |
| Part Press (in-world part cutting for non-meltable materials) | ✅ implemented   |
| Anvil-applied modifiers (partial progress, per-level costs) | ✅ implemented   |
| Mob scalding → fluid drops in the Forge | ✅ implemented   |
| JEI integration (melting, casting, press, assembly, modifiers) | ✅ implemented   |
| Armor stats & parts (Core / Plates / Trim) | 🚧 in progress — no assembly recipe yet |
| Datapack JSON override loaders | 🚧 partial — alloys & modifiers reload from data; materials/melting are code-registered |
| In-game Field Guide book | 🚧 minimal single-entry draft |
| Tool repair (sharpening stones) | ⏳ planned       |
| RF heat coils (Tier 1 + Tier 2) | ⏳ planned       |
| World gen (ores) | ⏳ not planned — materials melt from vanilla resources |

---

## Building from source

You'll need **JDK 21+**.

```sh
./gradlew build         # compile + jar the mod
./gradlew runClient     # launch a dev client with Smithery loaded
./gradlew runServer     # launch a dev server
```

Built jars land in `build/libs/`.

---

## Contributing

Contributions, ideas, and bug reports are welcome. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the workflow — the short version is "fork, branch, PR." The license is intentionally fork- and PR-friendly; see below.

---

## License

Smithery is distributed under the **Smithery Source-Available License** (see [`LICENSE`](LICENSE)). At a glance:

- ✅ View the source freely
- ✅ Fork on GitHub (or anywhere else) for contribution, experimentation, or learning
- ✅ Submit pull requests
- ❌ Redistribute compiled builds to end users
- ❌ Bundle into modpacks or launchers
- ❌ Release a competing fork as a standalone product

---

## Support development

If you'd like to support continued work on Smithery:

<div align="center">

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)][kofi-link]

</div>

---

## Acknowledgements

- Built on the [NeoForge](https://neoforged.net/) mod loader.
- Project scaffold derived from the [NeoForge MDK](https://github.com/NeoForged/MDK) template.
- Texture style inspired by vanilla Minecraft.

[mc-badge]: https://img.shields.io/badge/Minecraft-26.1.2-62B47A?logo=minecraft&logoColor=white
[mc-link]: https://www.minecraft.net/
[nf-badge]: https://img.shields.io/badge/NeoForge-26.1.2.61--beta-DC8A33
[nf-link]: https://neoforged.net/
[java-badge]: https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white
[java-link]: https://adoptium.net/
[lic-badge]: https://img.shields.io/badge/License-Source--Available-3B5998
[kofi-badge]: https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?logo=ko-fi&logoColor=white
[kofi-link]: https://ko-fi.com/yoitsoul
