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
- **Multiblock Forge.** Built entirely from custom Smithery blocks — Furnace Bricks form the structural shell, with Controller, Fuel Port, Drain, and Item Port blocks filling functional roles. Validates open-top or closed-top structures (closed-top applies a 1.2× heat bonus), tolerates partial builds, and allows Tinkers-style walls without corner blocks.
- **Heat & fluid simulation.** The Forge heats up from lava fuel, melts items dropped into the interior at temperature-gated rates, and processes alloy recipes automatically. The controller GUI shows live temperature (°C/°F toggle), per-material fluid tanks, and burn time estimates.
- **Casting system.** Pour molten material from the Forge through a fluid pipe network into casting tables. Impress a shape into sand, fill the mold, wait for cooling, then brush away the sand to retrieve your part.
- **Molten fluid system.** Every meltable material gets its own auto-generated fluid, fluid block, and tinted bucket — no JSON required. Fluids are transported via a built-in pipe network with wavefront routing.
- **JEI integration.** Melting, casting, part press, and tool assembly recipes all appear in JEI with full ingredient and output previews.
- **Designed for modders.** Register new materials, parts, tool types, modifiers, synergies, and alloys via a builder-style Java API. Models, item definitions, and tinted textures are generated at runtime from the live registry — no boilerplate JSON per material.

---

## Current status

Smithery is in active development. The roadmap is documented in [`SMITHERY_DESIGN.md`](SMITHERY_DESIGN.md).

| Phase | Status |
|---|---|
| Materials, parts, tools, modifiers, synergies | ✅ implemented |
| Crafting (shapeless tool assembly) | ✅ implemented |
| Multiblock Forge validator (open / closed / partial / no-corner) | ✅ implemented |
| Debug leak visualization | ✅ implemented |
| Heat & fluid simulation | ✅ implemented |
| Alloy processing | ✅ implemented |
| Molten fluid system (auto-generated per material) | ✅ implemented |
| Forge controller GUI (tanks, temperature, alloy toggle) | ✅ implemented |
| Fluid pipe network + Forge drain | ✅ implemented |
| Casting table (sand mold, impress, fill, cool, retrieve) | ✅ implemented |
| JEI recipe integration | ✅ implemented |
| Item melting feedback (particles + sound) | ⏳ planned |
| RF heat coils (Tier 1 + Tier 2) | ⏳ planned |
| Sharpening stones (repair) | ⏳ planned |
| Anvil-applied modifier items | ⏳ planned |
| Datapack JSON override loaders | 🚧 in progress |
| In-game Field Guide (Modonomicon) | 🚧 in progress |

---

## Building from source

You'll need **JDK 25** (shipped by Mojang with Minecraft 26.1.2).

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
- In-game documentation powered by [Modonomicon](https://github.com/klikli-dev/modonomicon).
- Animated block models via [GeckoLib](https://geckolib.com/).
- Recipe viewer integration via [JEI](https://www.curseforge.com/minecraft/mc-mods/jei).
- Texture style inspired by vanilla Minecraft.

[mc-badge]: https://img.shields.io/badge/Minecraft-26.1.2-62B47A?logo=minecraft&logoColor=white
[mc-link]: https://www.minecraft.net/
[nf-badge]: https://img.shields.io/badge/NeoForge-26.1.2.61--beta-DC8A33
[nf-link]: https://neoforged.net/
[java-badge]: https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white
[java-link]: https://adoptium.net/
[lic-badge]: https://img.shields.io/badge/License-Source--Available-3B5998
[kofi-badge]: https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?logo=ko-fi&logoColor=white
[kofi-link]: https://ko-fi.com/yoitsoul
