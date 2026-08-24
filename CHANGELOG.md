# Changelog

All notable changes to Smithery are recorded here. Versions follow [semver](https://semver.org/).

## [Unreleased]

## [1.19.1] — 2026-08-16

Registered press inputs now outrank the tag catch-all.

### Fixed

- **A log with its own registered press material no longer cuts into generic Wood parts.**
  `PartPressBlockEntity.resolveMaterialFor` tested `minecraft:logs` before consulting
  `SmitheryAPI.PRESS_INPUTS`, so any addon material whose source item is also a log — Botania's
  livingwood and dreamwood, Aether's skyroot log — was unreachable: the press took the item and
  handed back Wood. An exact item registration now wins over a tag match, which is the order the
  two were always meant to have.
- **The part-press JEI category lists addon press inputs.** It rebuilt the built-in acceptance
  set by hand (logs, flint, slime, coral) and never consulted `PRESS_INPUTS`, so every
  addon-registered input was missing from JEI while the affected logs were advertised as Wood.
  Rows now follow the same precedence the block uses.
- **The part-source tooltip agrees with the press.** It carried its own copy of the tag-first
  order, so a livingwood log's tooltip promised Wood parts.
- **Part templates render their true material colour.** Nine of the 23 part templates under
  `textures/item/part/` carried pixels left over from an anti-aliasing editor export: 1-7
  semi-transparent edge pixels each, and off-grey RGB values — 60 of them on `binder`, which was
  cream rather than grey. The client's item colour handler multiplies these masks by the
  material's `partColor`, so a non-grey mask skewed the tint (every material's binder pulled
  toward yellow) and feathered pixels composited the inventory slot into the item edge. Vanilla's
  own item art has neither, across all 21 textures checked. `tools/clean_part_alpha.py` restores
  binary alpha and true grayscale; shape and tone are untouched, and `--check` fails the same way
  if a future export reintroduces it.

- **The drain stops pouring into a block entity that no longer exists.** The pour job captured
  each target's `IFluidHandler` once and reused it for the life of the job. A table cast runs 29+
  ticks and a basin far longer, so breaking or replacing the target part-way through left the
  drain filling an orphan while the forge really lost the metal. Targets are now addressed by
  position and face and re-resolved every tick.
- **Forge temperature no longer reads negative on the client above 3276.7 °C.** `DATA_TEMP` was a
  single container-data index, which is short-sized on the wire, while the value is tenths of a
  degree — so molten blaze at 3500 °C arrived as −3053.6 °C. Not cosmetic: the screen compares that
  value against each material's melting point, so every slot tooltip claimed the forge was too cool
  while the server was actively melting. It now occupies a wide index pair like the other
  out-of-range quantities.
- **The rendered fluid pool keeps up with the forge.** `addFluid`, `drainFluid` and the alloy loop
  marked the block entity changed but never sent a block update, and the client's pool renders
  straight from the synced storage — so a forge pumped empty through the drain kept drawing full
  indefinitely. Melting hid it by syncing on its own. All three now flag a per-tick sync.
- **Large forges stop re-broadcasting their entire contents on every item change.** The update tag
  carries the whole interior position array plus a compound per occupied slot, and every single
  item shrink, absorbed drop and hopper interaction sent it — kilobytes per tick on a big build.
  Those paths now coalesce into the same per-tick flush; structure changes and player inserts
  still sync immediately.
- **A tool is only credited for damage it actually dealt.** `onDealDamage` fell back to the
  attacker's main hand whenever the hit was not a recognised Smithery projectile, with no check
  that the main hand threw the blow — so a splash potion of Harming, the player's own TNT, or an
  offhand vanilla bow all ran the rapier's armour-scaling bonus (armour *increasing* potion damage)
  and every `onDealDamage` hook. The cleaver's head roll and the XP hook had the same hole. All
  three now require a direct melee source.
- **Composition NBT is decoded once per stack instead of once per query.** Nothing cached the
  decode, and the item colour handler runs per tinted quad while rendering, so a held tool cost
  order 10² codec parses per frame; the per-tick modifier dispatch also decoded the tag purely to
  ask whether it existed, then decoded it again. There is now a decode memo keyed on the identity
  of the composition tag — which the write path replaces, so a re-compose invalidates it — and a
  presence check that does not decode at all.
- **Generated storage forms work at all.** The `storageForms()` addon path produced a block with no
  recipes, no loot table and no way to mine it correctly. Two independent causes: the generated
  pack declared only the `minecraft` namespace for server data while answering under the material
  owner's namespace, so its recipes and loot tables were never visible to the data manager; and the
  blocks require a correct tool but, being minted at construction time, could never appear in the
  hand-authored `mineable/pickaxe` tag. The pack now declares every namespace it serves and
  contributes a merging tag file.
- **The Draconic AOE module breaks a plane, not a cube.** DE zeroes the face-axis extent of its
  mining area; Smithery iterated the full cube, so a radius-2 module broke 124 extra blocks against
  DE's 24, each a full server break with its own event, loot roll and neighbour updates.
- **The casting table shows the right texture for non-part results.** It built a texture path by
  guessing `textures/item/<item id>.png`, which does not exist for storage-form ingots (they share
  one template) or for foreign ingots that nest their textures in subfolders — both drew a
  missing-texture checkerboard on the sand. It now draws the item's real sprite from the atlas.
- **Modifier slot counts in the tooltip match what the anvil enforces.** The tooltip counted
  applied entries and ignored bonus slots, while the anvil charges a level-N modifier N slots — so
  a level-2 Excavating displayed "1 / 3" having really consumed 2 of 3.
- **Hovering a hand-edited item no longer crashes the client.** The extra-attribute codec mapped
  operation and slot with `xmap`, whose factories throw rather than returning a failed result, so
  malformed NBT threw out of the decode instead of reading as absent — out of
  `getAttributeModifiers`, which vanilla calls while building a tooltip.
- **The scythe can be assembled.** It had no assembly recipe while JEI advertised one from the
  registered tool types, so the displayed recipe produced nothing. Added, along with the sceptre's,
  which goes live as soon as an addon supplies an arcane-focus material.
- **JEI's runtime is released when it tears down.** The reference was held forever, and the
  datapack sync fires before a new session's runtime is published — so a second server join in one
  client session mutated the previous session's recipe manager.
- **The part-press stops drawing teeth from stale textures.** A failed silhouette read cached its
  all-zero fallback permanently, which draws a full slab of maximum teeth for the rest of the
  session, and nothing ever called the cache's own `invalidate()`. Failures are no longer cached
  and a resource-reload listener clears it.
- **Fluid animations run at the same speed regardless of frame rate.** The pool, phantom-decay and
  fuel-port lerps applied a fixed fraction per rendered frame, so they sped up with frame rate and
  again under a shader pack's extra shadow pass.
- **Interior forge slots are no longer item-rendered off-screen every frame.** They are positioned
  off-screen because the screen draws the visible rows itself, but stayed active, and vanilla
  renders every active slot without culling — 27 redundant item renders per frame on a filled
  5×5×5, 125 on a 7×7×7.
- Dropped three data directories named for 1.21 conventions (`loot_table/`, `tags/block/`,
  `tags/item/`) that 1.20.1 never reads. They were byte-identical duplicates of the live
  directories, so nothing changes at runtime, but an edit landing in the wrong copy had no effect
  and no visible cause. They will regrow on any merge from the 1.21 branch.

- **Bonus drops no longer multiply themselves.** `ToolModifierEventRouter.onDropSpawn` spawned
  its extra drops with `addFreshEntity`, which fires `EntityJoinLevelEvent` synchronously and
  re-entered the same handler while the break capture was still live — so Golden Touch and Lapis
  Blessing ran again on their own output, compounding each pass. The handler now guards against
  re-entry the way `AoeMiningHandler` does. Drop matching also required only that an item spawn
  within two blocks in the same tick, which claimed unrelated entities — a thrown stack, mob
  death drops, a broken chest's contents; it now matches the broken block's exact position and
  ignores owned stacks.
- **The forge no longer destroys an item to bank part of its metal.** `meltFromSlots` tested only
  that `addFluid` accepted something, but `addFluid` clamps to the space left and reports the
  partial amount — so a forge with less room than one unit consumed the whole item for a fraction
  of its output. Since capacity is `interiorCount * 1000` against unit sizes like 144, a sub-unit
  remainder is the normal end state of filling a forge, not an edge case. Melting now requires
  room for the entire unit.
- **Forge slots keep an item's NBT across a save.** Interior slots persisted only an item id and
  count, so anything enchanted, renamed, damaged, or composition-bearing came back bare after a
  reload or chunk unload — reachable in ordinary play, since the chamber vacuums any item entity
  and non-meltables simply sit there. Slots now store the full stack tag; the old id+count form
  is still read so existing worlds load unchanged.
- **Breaking the forge controller returns the forge's contents.** Interior items live only in the
  controller's block entity, and the existing give-back path runs only while that block entity is
  alive — so breaking a wall brick returned your items but breaking the controller, the natural
  way to dismantle or move a forge, ate them. `ForgeControllerBlock.onRemove` now drops them,
  which also covers explosions and `Level#destroyBlock`.
- **Mining with a Stonebound tool no longer strips its enchantments.** Forge routes every
  `setDamageValue` through `IForgeItem.setDamage`, and the override there re-ran a full
  recomposition whenever a durability-scaled modifier was present — the first statement of which
  clears the `Enchantments` tag. A table-enchanted Stonebound tool lost its enchantments on the
  first block it broke. The override is gone, and the stat paths that need wear now read it live.
- **Stonebound's mining bonus actually applies.** `getDestroySpeed`, `isCorrectToolForDrops` and
  the tooltip called the `ToolStats.compute` overload that hard-codes zero wear, while the melee
  path passed the real value — so the modifier's damage penalty reached the player but its mining
  reward never did, and the printed attack damage disagreed with the attribute line beneath it.
  All three now pass the stack's true wear fraction.
- **Draconic Evolution module effects are applied once instead of twice.** Three bridges
  duplicated work DE's own handlers already route through Smithery gear, because that gear
  implements DE's interfaces: `inventoryTick` called `handleTick` even though
  `ModularArmorEventHandler.livingTick` already ticks every `IModularItem` in every inventory
  compartment (doubling module energy drain, shield recharge and auto-feed); a JUMP_BOOST listener
  added a second boost on top of DE's, which its fall-damage credit is not sized for; and a
  MOVEMENT_SPEED modifier compounded with the one DE installs from the same module data under a
  different UUID. All three are removed — the guarded `handleTick` override stays, since that is
  what stops DE's own call path hitting the interface default's `orElseThrow`.

- **Wearing a non-draconic Smithery chestplate with Draconic Evolution installed no longer
  crashes.** Every Smithery armour piece implements DE's `IModularArmor` when DE is present, and
  BrandonsCore asks any equipped elytra-enabled item whether it can fly — from the fall-flying
  hook and from DE's elytra render layer, once per frame. DE's default `canElytraFlyBC`
  `orElseThrow`s on the module-host capability, which a composition with no draconic-tier
  material legitimately does not have. `DraconicSmitheryArmorItem` now guards both elytra hooks
  the same way it already guarded `handleTick`.

## [1.19.0] — 2026-08-16

Guided scenes, fluids a material can borrow, and a readable stack of molten metals.

### Added

- **Ponder scenes**, as an optional integration with KubeJS and PonderJS. Three animated
  walkthroughs — cutting a part on the Part Press, the conditions a Forge has to meet
  before its controller lamp goes green, and pouring a cast from the Drain through a pipe
  into a Casting Table. The scenes ship inside the jar as client scripts, so a pack that
  already has both mods gets them with nothing to install; without them the file is never
  read.
- **`MaterialStats.Builder.boundFluid(id)`** — a material can now pour a fluid that already
  exists instead of being minted a molten one of its own. Melting, alloying and the forge
  tank stay material-keyed exactly as before; only the fluid the forge hands out changes, so
  what it drains is the real thing and works everywhere that fluid already works. Intended
  for materials the game has a fluid for — a material bound to lava pours back into a fuel
  port, which a separate molten lava never could. The flowing variant, block and bucket are
  read off the bound fluid, and Smithery registers nothing for it.

### Changed

- **Molten layers in the forge controller now have a minimum height.** A trace amount used to
  render as a single pixel, and since the bands are the click targets as well as the picture,
  a forge holding several traces became a run of slivers nobody could aim at. Every layer is
  now guaranteed four pixels, with the borrowed pixels reclaimed from the tallest layers, so
  the top of the stack still reads as the fill level. Where the tank holds more materials than
  it has room to floor, the floor shrinks to an even split rather than dropping the last
  layers out of the picture, which is what the old code did.
- **Scrolling over the molten tank steps the drain output** through the materials present,
  wrapping at both ends — an escape hatch for a crowded tank, where stepping beats aiming.

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
