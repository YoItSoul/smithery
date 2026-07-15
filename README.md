<div align="center">

# Smithery

**Modular tool crafting for NeoForge & Forge.** Build tools from interchangeable material parts in a multiblock forge.

[![Minecraft][mc-badge]][mc-link]
[![NeoForge][nf-badge]][nf-link]
[![Forge][forge-badge]][forge-link]
[![Java][java-badge]][java-link]
[![License][lic-badge]](LICENSE)
[![Ko-Fi][kofi-badge]][kofi-link]

</div>

---

## What is Smithery?

Smithery is a modular, modder-friendly tool crafting mod. Inspired by the spirit of
Tinkers' Construct but rebuilt from the ground up for modern Minecraft, it replaces
recipe-grid tools with a smithing craft:

- **Build a multiblock Forge** in any shape — it melts whatever you throw in, alloys
  metals automatically, and stores the molten result.
- **Cast or press parts** — pour molten metal into sand casts, or cut non-meltable
  materials (wood, flint, slime…) on the Part Press.
- **Assemble mixed-material tools and armor** at the crafting table, where every part
  contributes its own stats, abilities, and look.
- **Grow your gear** through material abilities, cross-material synergies, and
  anvil-applied modifiers.

Everything is driven by a clean Java API with runtime asset generation, so modders can add
materials, parts, tool types, and modifiers without writing a single JSON model.

Smithery ships for two loaders from this repository:

| Branch | Loader | Minecraft | Java |
|---|---|---|---|
| [`main`](../../tree/main) | NeoForge 26.1.2.61-beta | 26.1.2 | 21+ |
| [`forge-1.20.1`](../../tree/forge-1.20.1) | Forge 47.4.10 | 1.20.1 | 17+ |

Gameplay and balance are identical across both, with a handful of platform approximations
on 1.20.1 where the older APIs have no equivalent.

---

## Dependencies

| | NeoForge (26.1.2) | Forge (1.20.1) |
|---|---|---|
| **Required** | [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 5+ | [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.8+ |
| **Optional** — in-game field guide | [Modonomicon](https://www.curseforge.com/minecraft/mc-mods/modonomicon) | [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) |
| **Optional** — recipe browsing | [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) 15+ |

Without the optional mods Smithery runs normally — you just lose the field guide book or the
in-game recipe browser.

---

## Current status

Smithery is in active development — the core loop (forge, melting, alloying, casting,
pressing, tool assembly, modifiers, JEI) is playable today, with armor assembly, the
in-game field guide, and broader datapack support still in progress.

---

## Building from source

Check out the branch for the loader you want — **JDK 21+** for `main` (NeoForge), **JDK 17+** for `forge-1.20.1`.

```sh
./gradlew build         # compile + jar the mod
./gradlew runClient     # launch a dev client with Smithery loaded
./gradlew runServer     # launch a dev server
```

Finished jars are named `smithery-<loader>-<mcversion>-<modversion>.jar` and collected into
`dist/`, which always holds the latest build per loader (raw outputs stay in `build/libs/`).

---

## Contributing

Contributions, ideas, and bug reports are welcome — the workflow is the usual "fork, branch, PR." The license is intentionally fork- and PR-friendly; see below.

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

- Built on the [NeoForge](https://neoforged.net/) and [Forge](https://minecraftforge.net/) mod loaders.
- Project scaffold derived from the [NeoForge MDK](https://github.com/NeoForged/MDK) template.
- In-game field guide powered by [Modonomicon](https://github.com/klikli-dev/modonomicon) (NeoForge) and [Patchouli](https://github.com/VazkiiMods/Patchouli) (Forge 1.20.1).
- Texture style inspired by vanilla Minecraft.

[mc-badge]: https://img.shields.io/badge/Minecraft-26.1.2_%7C_1.20.1-62B47A?logo=minecraft&logoColor=white
[mc-link]: https://www.minecraft.net/
[nf-badge]: https://img.shields.io/badge/NeoForge-26.1.2.61--beta-DC8A33
[nf-link]: https://neoforged.net/
[forge-badge]: https://img.shields.io/badge/Forge-1.20.1--47.4.10-1E2D42
[forge-link]: https://minecraftforge.net/
[java-badge]: https://img.shields.io/badge/Java-21+_%7C_17+-ED8B00?logo=openjdk&logoColor=white
[java-link]: https://adoptium.net/
[lic-badge]: https://img.shields.io/badge/License-Source--Available-3B5998
[kofi-badge]: https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?logo=ko-fi&logoColor=white
[kofi-link]: https://ko-fi.com/yoitsoul
