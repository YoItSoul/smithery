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

Smithery is a modular, modder-friendly tool crafting mod centered on a multiblock **Forge**. Inspired by the spirit of Tinkers' Construct but rebuilt from the ground up for NeoForge 26.1.2 with a clean Java API, runtime asset generation, and full datapack override support.

- **Mixed-material tools.** Every tool is assembled from interchangeable parts — blade, guard, handle, binder, head. Pair an iron blade with a gold handle, a copper binder, and the stats, modifiers, and visuals stack accordingly.
- **Per-material, per-tool-type modifiers.** Iron sword swings sharper, iron pickaxe magnetizes drops, copper poisons, gold gilds XP. Materials carry different abilities depending on which tool they're used in.
- **Material synergies.** Specific cross-material combinations unlock bonus modifiers — Galvanic (Iron + Copper), Gilded (Iron + Gold), Verdant Veil (Copper + Gold) — at no slot cost.
- **Multiblock Forge.** Built from vanilla deepslate variants plus three Smithery blocks. Validates open-top or closed-top structures, tolerates partial builds, and allows walls without corner blocks.
- **Designed for modders.** Register new materials, parts, tool types, modifiers, synergies, and alloys via a builder-style Java API. Models, item definitions, and tinted textures are generated at runtime from the live registry — no boilerplate JSON per material.

---

## Current status

Smithery is in active development. The roadmap is documented in [`SMITHERY_DESIGN.md`](SMITHERY_DESIGN.md).

| Phase | Status          |
|---|-----------------|
| Materials, parts, tools, modifiers, synergies | ✅ implemented   |
| Crafting (shapeless tool assembly) | ✅ implemented   |
| Multiblock Forge validator (open / closed / partial / no-corner) | ✅ implemented   |
| Debug leak visualization | ✅ implemented   |
| Heat / fluid / alloy simulation | 🚧 in progress  |
| Forge controller GUI | 🚧 experimental |
| RF heat coils (Tier 1 + Tier 2) | ⏳ planned       |
| Casting basin + cast molds | ⏳ planned       |
| Sharpening stones (repair) | ⏳ planned       |
| Anvil-applied modifier items | ⏳ planned       |
| Datapack JSON override loaders | ⏳ planned       |
| In-game Field Guide book | 🚧 in progress  |

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
