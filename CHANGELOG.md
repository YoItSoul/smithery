# Changelog

All notable changes to Smithery are recorded here. Versions follow [semver](https://semver.org/).

## [Unreleased]

### Added

- **Ponder scenes**, as an optional integration with KubeJS and PonderJS. Three animated
  walkthroughs — cutting a part on the Part Press, the conditions a Forge has to meet
  before its controller lamp goes green, and pouring a cast from the Drain through a pipe
  into a Casting Table. The scenes ship inside the jar as client scripts, so a pack that
  already has both mods gets them with nothing to install; without them the file is never
  read.

## [1.18.0] — 2026-08-15

Forge glazing, an electric heat source, and the forge as a village building.

### Added

- **Furnace Glass**, in plain and all sixteen dyed colours. Glazes the forge shell without
  breaking the multiblock. The cast-iron frame is drawn only around the outside of a pane
  rather than around every block in it, so a wall of it reads as one sheet; every colour
  counts as a connection for every other, so a pane can mix dyes without being cut into
  pieces.
- **Furnace Brick Slab**, from crafting or the stonecutter.
- **Forge RF Coil** — heats the forge electrically to a configurable target temperature
  instead of by burning fuel. Tuned through `forge.rfCoilMaxTemperatureC`,
  `rfCoilCoefficient`, `rfCoilThermalMassDivisor` and `rfCoilBufferSeconds`.
- **The forge as a village building.** A smithy generates in villages, appended to the
  vanilla house pools and to those registered by village-overhaul mods rather than
  redefining either, so nothing another mod contributed is lost. Controlled by
  `worldgen.villageForge`, `villageForgeWeight`, `villageForgeChance`,
  `villageForgeMinPieces` and `villageForgeModdedVillages`.
- Forge controller status readout: a built forge with no fuel now says so, with a hint
  pointing at the fuel port, instead of reading as broken.

### Fixed

- **Village forges generated disconnected.** Furnace glass framed every block as a single
  cube, and fluid pipes and fuel ports drew no arms. Jigsaw placement writes template states
  with `UPDATE_KNOWN_SHAPE` and worldgen runs through `ProtoChunk`, so neither `updateShape`
  nor `onPlace` ever fires on a generated building — whatever the structure's palette holds
  is what stands. The structure now carries the neighbour-derived states itself. The blocks
  healed themselves as soon as anything updated a neighbour, which is what made this look
  intermittent.
- **Village forges placed at the wrong rotation.** Furnace glass and fluid pipes now rotate
  and mirror their connection properties, so their geometry points the right way at all four
  jigsaw rotations rather than only the unrotated one.
- **Several forges per village.** A jigsaw pool has no notion of "at most one of these": the
  placer walks every connector on every street piece, shuffles the whole house pool for each
  and takes the first candidate that fits, so a village draws far more times than it has
  buildings. Weight could move the average but never bound the count. A forge is now capped
  at exactly one per village, and the lot that would have taken a second gets an ordinary
  house rather than an empty plot.
- Village forge rarity is now a dial in its own right. `villageForgeChance` (20% by default)
  is rolled once per village from its own origin, so it neither depends on the order chunks
  are visited nor consumes generator randomness — no vanilla village layout shifts for
  having the mod installed. `villageForgeMinPieces` can instead hold the forge to villages
  that grow past a given size.

### Changed

- Retextured the casting basin, casting table, fluid pipe, forge controller, forge drain,
  forge item port and part press.
- Smithery now ships one mixin, on the jigsaw placer, for the per-village forge cap. It
  touches nothing else and no other structure.
