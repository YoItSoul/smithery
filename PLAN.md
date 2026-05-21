# Smithery — Implementation Plan

**Stack:** Minecraft 26.1.2 · NeoForge 26.1.2.61-beta · Java 25  
**Mod ID:** `smithery`  
**Design ref:** `SMITHERY_DESIGN.md`

Update this file as work completes. Every session should start by reading this file.

---

## Current State (as of 2026-05-20)

### Done
- [x] **API layer** — `MaterialStats`, `Material`, `PartType`, `ToolType`, `Modifier`, `ModifierEffect`, `AlloyDefinition`, `AlloyComponent`, `SynergyDefinition`, `SimpleRegistry`, `SmitheryAPI`
- [x] **Content registrations** — copper / gold / iron; sword + pickaxe tool types; `sharp` + `magnetized` modifiers; galvanic/gilded/verdant-veil synergies; full melting recipe table (nugget/ingot/raw/ore/block for all 3 metals)
- [x] **Forge multiblock** — `ForgeControllerBlockEntity`: structure validation (BFS shell + interior flood-fill, partial-structure tolerance, hole tracking, open/closed-top detection), heat simulation (lerp toward lava target, closed-top 1.2× factor), lava fuel system (accumulator drain across all ports), item melting in interior (temperature-gated melt rate formula → `fluidStorage` map)
- [x] **Forge blocks** — `ForgeControllerBlock` (tick, right-click chat readout, neighbor validate), `ForgeFuelPortBlock` (lava bucket fill/drain), `ForgeDrainBlock` (stub — no logic), `FURNACE_BRICKS`
- [x] **Part items** — `PartItem` (carries `materialId` + `partTypeId`), tint source (`PartMaterialTintSource`), runtime-generated assets (`SmitheryGeneratedPack` + `SmitheryPackProvider`)
- [x] **Tool items** — `SmitheryToolItem` (stats from composition, tooltip with parts/modifiers/synergies, `postHurtEnemy` + `mineBlock` active modifier hooks), `ToolComposition` data component, `ToolStats` (durability formula, attribute modifiers, tool component)
- [x] **Tool assembly recipe** — `ToolAssemblyRecipe` (shapeless by part-type multiset), serializer registered, JSON files for sword + pickaxe in `data/smithery/recipe/`
- [x] **Debug visualization** — `ForgeLeakDebugPayload` + `DebugBoxRenderer` (red wireframes on hole positions when right-clicking controller)
- [x] **Config** — server config spec (`Config.java`)

### Not Done
- [ ] Forge neighbor-change revalidation (placing bricks/ports/drain after controller doesn't immediately trigger validate)
- [ ] Melting feedback (particles/sounds — items sit silently while melting)
- [ ] Alloy system (processing algorithm in `serverTick`)
- [ ] Forge Controller GUI (right-click → actual screen, not chat)
- [ ] Forge Drain fluid extraction (stub block only)
- [ ] Molten fluid registration (`smithery:molten_iron` etc. as actual `FluidType` objects)
- [ ] Casting Basin block + block entity
- [ ] RF Coil blocks (T1 + T2)
- [ ] Repair system (Sharpening Stones)
- [ ] Post-craft modifier items (Anvil integration)

---

## Known Bugs

### Bug: Item melting silently does nothing
**Symptom:** Dropping iron ore into the forge interior appears to do nothing.  
**Root causes:**
1. Temperature gate — iron needs 1,538°C; lava lerp with `HEAT_RATE_PER_TICK = 0.003f` takes ~45 s to reach that threshold. Items are skipped silently every tick until threshold is crossed.
2. No feedback — no particles, sounds, or item glow while melting is in progress.
3. Item entity position — if the item entity's foot position lands on a shell block (not an interior air block), `lastValidation.interior.contains(foot)` skips it silently.

**Fix plan:**
- Add flame/smoke particles at each melting item entity once per second
- Add a subtle hissing/sizzle sound
- Consider allowing items on the floor of the interior (foot pos one Y below interior) — or at minimum log a debug line

### Bug: Forge blocks don't trigger controller revalidation on placement
**Symptom:** Adding a fuel port, drain, or brick to a partially-built forge doesn't re-validate until the next 40-tick passive sweep.  
**Fix plan:** Override `onPlace` + `onRemove` on `ForgeFuelPortBlock`, `ForgeDrainBlock`, and a new `FurnaceBricksBlock` class to walk 26-connected neighbors looking for a `ForgeControllerBlockEntity` and call `validateStructure()` on it.

---

## Next Up (ordered)

### Step 1 — Fix: Neighbor revalidation + melting feedback
Files to touch:
- `ForgeFuelPortBlock.java` — add `onPlace` / `onRemove` → find + revalidate nearby controller
- `ForgeDrainBlock.java` — same
- `SmitheryBlocks.java` — change `FURNACE_BRICKS` to use a custom `FurnaceBricksBlock` subclass with same hooks
- `ForgeControllerBlockEntity.java` — add `spawnMeltingParticles(ServerLevel, ItemEntity)` called from `meltItemsInInterior` once every 20 ticks per entity

Helper: write `ForgeControllerBlockEntity.revalidateNearbyController(Level, BlockPos)` static util — walks 26-connected shell neighbors looking for a controller BE, calls `validateStructure()`.

---

### Step 2 — Alloy system
**Where:** `ForgeControllerBlockEntity.serverTick` — call `tickAlloys(level)` every 20 ticks.

**Algorithm** (from SMITHERY_DESIGN.md §9.3):
```
1. Collect all fluids in fluidStorage with mb >= 144 (minimum batch unit)
2. Find all AlloyDefinition where:
   - forge temp >= alloy.minTemp
   - ALL components have enough fluid (component.ratio × 144 mB each)
3. Sort matches: component count DESC, then by fluidStorage insertion order for tiebreak
4. Maintain a "reserved" fluid map (starts empty each cycle)
5. Iterate sorted list:
   a. If ALL components available in (fluidStorage − reserved): reserve min-batch of each, add to execute queue
   b. Else: skip
6. Execute all queued: drain reserved amounts, add result fluid
```

Files to create/touch:
- `ForgeControllerBlockEntity.java` — add `private void tickAlloys(ServerLevel level)` + call it in `serverTick` every 20 ticks with a `alloyTickCounter` field

---

### Step 3 — Forge Controller GUI
**Goal:** Right-clicking the controller opens a menu screen showing: temp (°C/°F toggle), fuel level + burn time estimate, fluid list (material + mB), structure status + hole count.

**NeoForge menu pattern:**
- Create `ForgeControllerMenu extends AbstractContainerMenu` — no item slots needed, just data sync
- Create `ForgeControllerScreen extends AbstractContainerScreen` — draws the panel
- Register `MenuType<ForgeControllerMenu>` in a new `SmitheryMenus` registry class
- `ForgeControllerBlock.useWithoutItem` → `NetworkHooks.openScreen(player, menuProvider, pos)` (server), returns `InteractionResult.SUCCESS` (client)
- Data sync: use `ContainerData` (int array synced automatically) for temp (×100 fixed-point), fuel mB, fuel capacity, fluid count, validity flag
- Fluid list needs a custom packet since it's variable-length: send on open + when contents change

---

### Step 4 — Forge Drain fluid extraction
**Goal:** The drain block exposes real molten fluid so buckets and pipes work.

Blocked by Step [Molten fluids] below — need real `FluidType` objects before `IFluidHandler` can expose them.

---

### Step 5 — Molten fluid registration
**Goal:** Auto-register `smithery:molten_<material>` as a real NeoForge `FluidType` + `FlowingFluid` pair per material.

**NeoForge pattern:**
- `DeferredRegister<FluidType>` in a new `SmitheryFluids` class
- For each material at registration time: `FLUID_TYPES.register("molten_" + mat.getPath(), () -> new FluidType(...))`
- Also register the still + flowing `Fluid` objects and a fluid block
- Color/texture: driven by `material.stats().moltenColor()` — use a tinted overlay on a shared base lava texture
- Expose via `SmitheryAPI` so the drain and casting basin can reference them

---

### Step 6 — Casting Basin
**Goal:** Standalone block. Player places a Cast Mold item, routes molten fluid in (bucket or pipe), part solidifies automatically once the correct mB is reached.

- `CastingBasinBlock extends Block implements EntityBlock`
- `CastingBasinBlockEntity` — holds one mold item slot + fluid tank (accepts one fluid type at a time)
- Right-click with mold item → stores it; right-click with molten bucket → fills fluid
- Each server tick: if mold present + fluid type matches mold's required material + enough mB → produce part item, return mold, clear fluid
- GUI: simple screen showing mold slot + fluid bar + progress

---

### Step 7 — RF Coil blocks
**Goal:** T1 (max 1,200°C) and T2 (max 3,000°C) RF heat sources. Takes priority over lava when present + powered.

Design constants (from SMITHERY_DESIGN.md §4.5):
- T1: `TIER_COEFFICIENT = 0.8`, max 1,200°C
- T2: `TIER_COEFFICIENT = 2.5`, max 3,000°C
- `RF_per_tick = TIER_COEFFICIENT × target_temp + (stored_total_mB / 200)`
- Has internal buffer (size TBD); target temp set via GUI

Files:
- `ForgeRfCoilBlock` — two instances (T1, T2) or parameterized
- `ForgeRfCoilBlockEntity` — implements `IEnergyStorage`, stores target temp + buffer
- `SmitheryBlocks` — register `forge_rf_coil_t1` + `forge_rf_coil_t2`
- `ForgeControllerBlockEntity.serverTick` — check shell for RF coil, if present + powered: skip lava consumption, use coil as heat source

---

### Step 8 — Repair system (Sharpening Stones)
**Goal:** Tiered stone consumables applied at vanilla Anvil, repair 25% durability.

- `SharpeningStoneItem` — carries tier (0–4) as data component
- Register stones via `SmitheryItems`
- `AnvilUpdateEvent` handler: if left=Smithery tool + right=matching-tier stone → set output with repaired durability, set XP cost
- Casting Basin: Sharpening Stone Mold produces stones from any material at matching harvest level

---

### Step 9 — Post-craft modifier items
**Goal:** Apply modifier items at vanilla Anvil to consume a modifier slot and add a modifier.

- `ModifierApplicationItem` — carries `modifierId` + `params` as data components
- Register Nether Star modifier item
- `AnvilUpdateEvent` handler: if left=Smithery tool + right=modifier item + tool has remaining slots → apply modifier, set XP cost

---

## Open Questions (from SMITHERY_DESIGN.md §15)

| # | Question | Status |
|---|---|---|
| 1 | Exact Minecraft version | Resolved: MC 26.1.2 |
| 2 | 3×3 grid slot layout for Sword + Pickaxe | Shapeless for now (ToolAssemblyRecipe ignores grid positions) |
| 3 | Part size scalars for durability | Placeholder values in `SmitheryPartTypes` — needs balance pass |
| 4 | Binder multiplier value per material | Currently in `MaterialStats.binderMultiplier()` — needs values |
| 5 | Sharpening stone repair % | 25% (TBD balance) |
| 6 | Anvil XP cost for modifiers | TBD |
| 7 | Lava burn rate | 0.1 mB/tick = 1 bucket / ~500s — needs balance pass |
| 8 | Casting Basin: direct pipe or bucket only? | TBD |
| 9 | Cast Molds: clay → permanent, or always permanent? | TBD |
| 10 | Texture strategy for auto-generated parts | Done: grayscale template + tint source |
| 11 | What happens to fluid when forge is destroyed? | Currently: fluid stays in controller NBT (locked until rebuilt) |
| 12 | RF Coil internal buffer size | TBD |
| 13 | RF Coil crafting recipes | TBD |
| 14 | Field Guide auto-open or item? | TBD |
| 15 | Future metals (tin, silver, nickel, lead)? | TBD — design melt temps when adding |
