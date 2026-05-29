# SMITHERY MOD — Project Design Document

**Version:** 0.1 (Draft)  
**Mod ID:** `smithery`  
**Loader:** NeoForge 26.1.2 *(confirm which Minecraft version this targets)*

---

## 1. Overview

Smithery is a modular, modder-friendly tool crafting mod centered around a multiblock Forge. Players smelt raw materials into molten metal, alloy them within the forge, cast them into tool parts, and assemble those parts into finished tools at a crafting table. Post-craft upgrades are applied at the vanilla Anvil.

**Design Pillars:**
- **Modular** — every tool is assembled from interchangeable, material-specific parts
- **Modder-Friendly** — zero overhead to add materials, parts, alloys via Java API or datapack JSON
- **Balanced** — temperature gates smelting/alloying progression; harvest level gates repair
- **Expandable** — new ToolTypes require Java; stats, modifiers, alloys, and recipes are data-overridable

---

## 2. System Overview

| System | Description |
|---|---|
| Forge (Multiblock) | Smelts materials, auto-alloys metals, stores molten fluid internally |
| Materials Registry | Master stats, modifier lists, melting temps per material |
| Modifier System | Per-material, per-tool-type passive and active bonuses |
| Material Synergies | Bonus modifiers unlocked by specific cross-material part combinations |
| Tool Types | Defined output templates assembled from typed parts |
| Alloy System | Ratio-based automatic in-forge alloying; conflict resolution via complexity priority |
| Heat & Temperature | Lava fuel or RF coil (priority) drives forge temperature |
| Fluid & Casting | mB-based molten storage; cast at a Casting Basin into molds |
| Repair System | Sharpening Stones gated by harvest level tier |
| Auto-Generation | Every registered material auto-generates fluid, parts, and recipes |
| Modder API | Full Java API + Datapack JSON override layer |

---

## 3. The Forge (Multiblock)

### 3.1 Structure

The Forge is a fully enclosed hollow multiblock. The count of interior air blocks defines its molten fluid capacity. There is no enforced minimum or maximum size — only structural validity rules.

**Required blocks:**

| Block ID | Role | Quantity |
|---|---|---|
| `smithery:forge_brick` | Wall / floor / ceiling | Any |
| `smithery:forge_controller` | GUI, temperature display, fluid readout | Exactly 1 |
| `smithery:forge_fuel_port` | Lava input (bucket or pipe) | At least 1 |
| `smithery:forge_drain` | Molten fluid output (bucket or pipe) | At least 1 |
| `smithery:forge_rf_coil_t1` | Tier 1 RF heat source; max 1,200°C | 0 or 1 |
| `smithery:forge_rf_coil_t2` | Tier 2 RF heat source; max 3,000°C | 0 or 1 |

**Validation rules (checked on load and on block change):**
- All interior faces fully enclosed by forge blocks
- Exactly 1 `forge_controller`
- At least 1 `forge_fuel_port`
- At least 1 `forge_drain`
- At least 1 interior air block
- At most 1 RF coil block of any tier (Tier 1 and Tier 2 cannot coexist in the same forge)

**Capacity formula:**
```
capacity_mB = interior_air_block_count × 1000
```
*Example: 3×3×3 hollow interior (27 blocks) = 27,000 mB capacity*

### 3.2 Controller GUI

The `forge_controller` block opens the Forge GUI:
- Current temperature (°C or °F — client config toggle)
- Fuel level + estimated remaining burn time
- Molten fluid list: fluid name + current mB per fluid type (scrollable)
- Structure status indicator (Valid / Invalid + reason)

### 3.3 Fuel System

**Heat source priority:** If an RF coil block is present in the forge structure and has incoming RF power, it takes full control of forge temperature. Lava in `forge_fuel_port` blocks is held in reserve but not consumed. If RF is lost or the coil is removed, the forge immediately falls back to lava. This allows players to use RF as a clean, precise heat source while keeping lava as a backup.

**Lava fuel:**
- Input via lava bucket into `forge_fuel_port` or fluid pipe connection
- Stored as a fluid in the `forge_fuel_port` block(s)
- Consumed continuously while the forge is below max lava temperature and no RF coil is active
- Consumption rate scales with delta between current temp and target temp
- **Burn duration:** 1 bucket (1,000 mB) of lava ≈ 10 real-time minutes at sustained max temp *(TBD — balance pass)*

**Future fuels** are registered via a fuel registry with `maxTemp` and `burnRate` fields. This is the primary non-RF progression gate for higher-tier materials.

---

## 4. Heat & Temperature System

### 4.1 Temperature Values

All values stored internally as °C. Display converts per client config.

| Fuel / Material | Temperature (°C) | Notes |
|---|---|---|
| Lava (forge max) | 1,650°C | Fantasy Minecraft lava; hot enough for all starting metals |
| Copper melting point | 1,085°C | Realistic value |
| Gold melting point | 1,064°C | Realistic value |
| Iron melting point | 1,538°C | Realistic value |

Every material in the registry has a `meltingTemp` field. The forge must meet or exceed this value before the material liquefies or participates in alloying.

### 4.2 Heating Behavior

- **Ramp time:** Cold forge reaches max fuel temp in approximately 30 seconds
- **At max temp:** Lava is consumed at a steady rate to maintain temperature
- **Fuel depleted:** Temperature decays gradually

**Cooling rate formula:**
```
cool_rate = base_cool_rate / (1 + stored_total_mB / 1000)
```
More molten mass = higher thermal inertia = slower cooling. An empty forge cools much faster than a full one.

### 4.3 Temperature Gating

- Materials will not melt if forge temp < material `meltingTemp`
- Alloys will not form if forge temp < alloy `minTemp`
- Parts already liquid are unaffected by temperature dropping below their melt point (they remain liquid until cast or the forge cools to ambient)

### 4.4 Melt Rate (Temperature-Dependent)

The higher the forge temperature relative to a material's melting point, the faster that material melts. This uses the *temperature excess ratio* — how much hotter the forge is beyond the minimum threshold, normalized by the melt point itself.

**Formula:**
```
temp_excess   = forge_temp - material_melt_temp        // negative = no melting
excess_ratio  = temp_excess / material_melt_temp
melt_rate     = BASE_MELT_RATE × (1 + TEMP_SCALE × excess_ratio)   // mB/tick
```

| Constant | Default Value | Notes |
|---|---|---|
| `BASE_MELT_RATE` | 1.0 mB/tick | Rate when forge is exactly at melt point |
| `TEMP_SCALE` | 2.0 | Tunable — scales how much excess heat matters |

**Starting material melt rates in a lava forge (1,650°C):**

| Material | Melt Point | Excess Ratio | melt_rate (mB/tick) | 1 Ingot (144 mB) |
|---|---|---|---|---|
| Lead *(future)* | 327°C | 4.04 | ~9.1 mB/tick | ~0.8 sec |
| Gold | 1,064°C | 0.55 | ~2.1 mB/tick | ~3.4 sec |
| Copper | 1,085°C | 0.52 | ~2.0 mB/tick | ~3.6 sec |
| Iron | 1,538°C | 0.07 | ~1.1 mB/tick | ~6.5 sec |

Iron melts roughly 6× slower than gold in the same lava forge — barely clearing the threshold. This naturally incentivizes higher-temperature fuels as progression unlocks more materials with higher melt points.

**Per-item inputs use the same rate** — an ingot placed in the forge melts in the time above; an ore (288 mB) takes ~2× as long; a block (1,296 mB) takes ~9×.

Both `BASE_MELT_RATE` and `TEMP_SCALE` should be exposed as server config values for balance tuning.

### 4.5 RF Heat Sources

RF coil blocks replace lava as the heat source when present and powered. They accept RF via the NeoForge energy API (`IEnergyStorage`) on any face. The player sets a **target temperature** via the coil's GUI; the coil then draws RF/t to reach and hold that temperature. RF cost scales with both the target temperature and the forge's thermal mass (more molten metal = more energy needed to maintain temp).

**RF/t Formula (both tiers):**
```
RF_per_tick = TIER_COEFFICIENT × target_temp_°C + (stored_total_mB / THERMAL_MASS_DIVISOR)
```

| Tier | Block ID | Max Temp | `TIER_COEFFICIENT` | `THERMAL_MASS_DIVISOR` |
|---|---|---|---|---|
| Tier 1 — Resistive Coil | `smithery:forge_rf_coil_t1` | 1,200°C | 0.8 RF/°C | 200 |
| Tier 2 — Arc Coil | `smithery:forge_rf_coil_t2` | 3,000°C | 2.5 RF/°C | 200 |

**Example RF costs (base, empty forge):**

| Scenario | RF/t |
|---|---|
| T1 at 1,064°C (Gold melt) | ~851 RF/t |
| T1 at 1,085°C (Copper melt) | ~868 RF/t |
| T1 at 1,200°C (T1 max) | ~960 RF/t |
| T2 at 1,538°C (Iron melt) | ~3,845 RF/t |
| T2 at 1,650°C (lava-equivalent) | ~4,125 RF/t |
| T2 at 3,000°C (T2 max) | ~7,500 RF/t |

**Thermal mass surcharge:** +`stored_mB / 200` RF/t. A full 27,000 mB forge adds ~135 RF/t on top of the base cost.

**Design notes:**
- Tier 1 cannot reach iron's melting point (1,538°C) — it is a mid-tier upgrade for non-ferrous metals only
- Tier 2 covers all planned material temperatures and is deliberately expensive (late-game)
- Unlike lava, RF coils do not cool the forge — when RF is cut, the forge retains heat via thermal mass and then cools normally
- The RF coil has an internal buffer (capacity TBD) so brief power interruptions don't immediately drop temperature
- Both tiers use the same RF/t formula; only the coefficient and max temp differ
- `TIER_COEFFICIENT` and `THERMAL_MASS_DIVISOR` are server config values

---

## 5. Materials Registry

### 5.1 Material Stats Schema

```java
MaterialStats {
    ResourceLocation id;                            // e.g., smithery:iron
    int harvestLevel;                               // default: 0
    float miningSpeed;
    float attackDamage;
    int durabilityPerIngot;                         // base durability unit per ingot-equivalent part
    float meltingTemp;                              // °C
    int moltenColor;                                // ARGB hex for auto-generated fluid texture
    Map<PartType, Integer> modifierSlots;           // max post-craft modifier slots per part type
    Map<ToolType, List<ModifierEffect>> modifiers;  // per-tool-type modifiers auto-applied at craft
}
```

### 5.2 Harvest Level Reference

| Level | Label | Example Materials |
|---|---|---|
| 0 | By Hand | Wood, Leather |
| 1 | Stone | Stone, Flint |
| 2 | Iron | Copper, Gold, Iron |
| 3 | Diamond | Diamond |
| 4 | Netherite | Netherite-tier alloys |

*Note: Gold is Harvest Level 2 despite vanilla behavior. Its high speed and low durability are expressed via stats, not harvest level.*

### 5.3 Starting Materials

| Material | Harvest Level | Mining Speed | Attack Damage | Durability/Ingot | Melting Temp |
|---|---|---|---|---|---|
| Copper | 2 | 6.0 | 5.0 | 80 | 1,085°C |
| Gold | 2 | 12.0 | 4.0 | 32 | 1,064°C |
| Iron | 2 | 6.5 | 4.0 (+2 SHARP → 6) | 150 | 1,538°C |

Attack Damage is the blade material's contribution; sword tools add any sword-modifier
bonuses on top (e.g., iron's SHARP adds +2 → 6 total, matching vanilla iron sword).
Pickaxes use the head material's value directly (no SHARP for pickaxe), so iron
pickaxe = 4 damage, matching vanilla.

### 5.4 Auto-Generated Content Per Registered Material

The following are generated automatically when a material is registered. No manual registration needed.

| Generated Asset | ID Pattern |
|---|---|
| Molten fluid | `smithery:molten_<material_id>` |
| Fluid bucket item | `smithery:molten_<material_id>_bucket` |
| Tool part item (per PartType) | `smithery:<material_id>_<part_type_id>` |
| Smelting input recipes | ore → 288 mB, ingot → 144 mB, block → 1,296 mB, nugget → 16 mB |
| Casting recipes | mB amount + cast mold → part item |
| Tool assembly recipes | shaped crafting recipe per ToolType |

Molten fluid textures are generated using a tinted base texture driven by the material's `moltenColor` field.

---

## 6. Fluid & Casting System

### 6.1 mB Reference Values

| Input Form | Output (mB) |
|---|---|
| 1 Nugget | 16 mB |
| 1 Ingot | 144 mB |
| 1 Raw Ore / Ore Block | 288 mB (2× ingot bonus) |
| 1 Storage Block (9 ingots) | 1,296 mB |

### 6.2 Internal Fluid Storage

- Molten metal is stored per-fluid-type inside the forge's internal tank
- Total capacity: `interior_air_blocks × 1,000 mB` shared across all fluid types
- Fluid handler API exposed on `forge_drain` and `forge_fuel_port` blocks for pipe compatibility with other mods

### 6.3 Casting Basin

The **Casting Basin** is a standalone block (not part of the forge multiblock) used to solidify molten fluid into parts.

**Process:**
1. Player places a **Cast Mold** item into the Casting Basin GUI
2. Molten fluid is routed into the basin via pipe or by right-clicking the `forge_drain` with a bucket, then the basin
3. Once the basin receives the exact mB amount required for the mold, the part solidifies automatically
4. Player retrieves the finished part item; the Cast Mold is returned undamaged (reusable)

**Cast Molds:**
- One mold item per PartType (not per material)
- Example: `smithery:cast_sword_blade`, `smithery:cast_pick_head`
- Mold items are crafted separately (recipe TBD — suggest: clay cast consumed on first use, then replaced by permanent cast)

---

## 7. Tool Types & Parts

### 7.1 ToolType Overview

Tool types define the final assembled item and its shaped crafting recipe. Registering a new ToolType requires Java. Each ToolType specifies:
- Which PartTypes it requires and how many
- Which slot each part occupies in the 3×3 crafting grid
- Which part type plays a multiplier role vs. additive role in durability

### 7.2 Durability Formula

```
durability = (Σ additive_part_durabilities) × binder_multiplier × Π(modifier_durability_multipliers)
```

- Each additive part contributes: `material.durabilityPerIngot × part_size_scalar` *(part_size_scalar TBD per part type, e.g., a pick head is 1.0, a handle is 0.4)*
- Binder contributes a flat multiplier (e.g., 0.8 = −20% total durability)
- Post-craft modifier items can add further multipliers, both above and below 1.0 — intentionally

**Example (iron pickaxe):**
```
head1 = 150, head2 = 150, handle1 = 60, handle2 = 60, binder_mult = 0.8
base = (150 + 150 + 60 + 60) × 0.8 = 336
with modifier ×1.5 → 336 × 1.5 = 504
```

**Balance anchor — iron sword with wood handle:**
Composition `iron blade × iron guard × wood handle × iron binder` is the reference point —
it matches vanilla iron sword's 250 durability exactly:
```
150 (iron 150 × blade 1.0) + 60 (iron 150 × guard 0.4) + 40 (wood 100 × handle 0.4)
  × 1.0 (iron binder) = 250
```
Wood's `durabilityPerIngot` is set to 100 specifically to make this math work cleanly; a
pure-iron sword overshoots vanilla at 270 (a small "luxury" bonus), and a pure-wood sword
lands around 126 (~2× vanilla wood sword).

### 7.3 Sword (SwordType)

**Parts:**

| Part | Count | Durability Role |
|---|---|---|
| Sword Blade | 1 | Additive |
| Guard | 1 | Additive |
| Handle | 1 | Additive |
| Binder | 1 | Multiplier (no additive durability) |

**Shaped recipe layout (3×3, TBD — finalize during implementation):**
```
[ Blade  ][       ][       ]
[ Guard  ][       ][       ]
[ Binder ][ Handle][       ]
```

**Stats derived from:** Blade material (primary: damage, modifiers), Handle material (secondary: speed, modifiers), Guard material (tertiary: modifiers)

### 7.4 Pickaxe (PickType)

**Parts:**

| Part | Count | Durability Role |
|---|---|---|
| Pick Head | 2 | Additive each |
| Handle | 2 | Additive each |
| Binder | 1 | Multiplier (no additive durability) |

**Shaped recipe layout (3×3, TBD — finalize during implementation):**
```
[ Head   ][ Binder ][ Head  ]
[        ][ Handle ][       ]
[        ][ Handle ][       ]
```

**Stats derived from:** Pick Head materials (primary: harvest level, mining speed, modifiers), Handle materials (secondary: modifiers)

---

## 8. Modifier System

### 8.1 Modifier Structure

```java
ModifierEffect {
    ResourceLocation modifierId;
    Map<String, Object> params;   // e.g., {"damage": 2.0}
}
```

Modifiers are registered globally. Their passive/active behavior is defined in Java. Material stats associate specific modifier instances (with params) to `(material, ToolType)` pairs. This means the same modifier ID can appear on the same material for different tool types with different param values.

### 8.2 Modifier Categories

**Passive:** Applied at craft time, baked into tool NBT stats. No runtime overhead.  
**Active:** Hook into game events at runtime. Applied every time the event fires.

| Modifier | Category | Effect |
|---|---|---|
| `smithery:sharp` | Passive | +N attack damage (N from params) |
| `smithery:magnetized` | Active | On block break: pull all dropped items within 5 blocks toward the player |

### 8.3 Modifier Slots

Each material defines a max modifier slot count per PartType:

```java
material.modifierSlots = Map.of(
    PartType.SWORD_BLADE,  2,
    PartType.SWORD_GUARD,  1,
    PartType.SWORD_HANDLE, 1,
    PartType.PICK_HEAD,    2,
    PartType.PICK_HANDLE,  1,
    PartType.BINDER,       0   // binder contributes no modifier slots
);
```

A tool's **total modifier slot capacity** = sum of all part modifier slots at crafting time.

At-craft modifiers (from material definitions) consume slots first. Remaining slots are available for post-craft modifier items.

### 8.4 Post-Craft Modifier Application (Anvil)

Players apply modifier items at the vanilla Anvil:
- Modifier items are **not** craftable tool part materials — they are special drop/loot items
- Each modifier item type is registered with a defined modifier effect, param values, and XP cost
- Applying a modifier item consumes 1 modifier slot on the tool
- Application fails if the tool has no remaining slots
- Modifier item examples:

| Item | Modifier Applied | Effect |
|---|---|---|
| Wither Star | `smithery:nether_sharpened` | +6 attack damage |
| *(more TBD)* | | |

### 8.5 Durability Modifiers

Some modifiers carry a `durabilityMultiplier` value. These are applied multiplicatively after the base durability formula. Values < 1.0 reduce durability; values > 1.0 increase it. Both outcomes are intentional and part of trade-off design.

### 8.6 Material Synergies

A **synergy** is a bonus modifier automatically applied to a tool when two specific materials appear together across its parts. Synergies are a reward for intentional material mixing — they do **not** consume modifier slots and are not available through any other means.

**Rules:**
- Synergies are defined as `(materialA, materialB) → ModifierEffect`
- The pairing is order-independent: Iron+Copper and Copper+Iron are the same synergy
- Only two-material synergies exist (no three-way combinations) — keeping them discoverable and simple
- A tool can trigger multiple synergies if it contains multiple qualifying pairs
- Synergies are shown clearly in the tool tooltip under "Active Synergies"
- Synergies are registered via Java API or datapack JSON and are fully modder-extensible

**Synergy data structure:**
```java
SynergyDefinition {
    ResourceLocation materialA;
    ResourceLocation materialB;
    Map<ToolType, ModifierEffect> effectsPerToolType; // can be empty for a tool type = no synergy on that type
}
```

**Starting Synergies (Iron × Copper × Gold):**

| Material A | Material B | Synergy Name | Sword Effect | Pickaxe Effect |
|---|---|---|---|---|
| Iron | Copper | **Galvanic** | +2 damage vs. mobs touching water or in rain | No durability loss when mining underwater |
| Iron | Gold | **Gilded** | +25% XP from kills | +25% XP from ore/mineral mining |
| Copper | Gold | **Verdant Veil** | 15% chance to apply Poison I (3s) on hit | 10% chance to drop an extra flint or stone when mining rock |

**Discoverability:** A craftable **Smithery Field Guide** (given to the player on first forge interaction) lists all registered synergies plainly. No hidden mechanics — the synergy table is fully visible before the player crafts their first tool. Active synergies on a tool are called out explicitly in the tooltip.

**Datapack JSON (adding a synergy):**

`data/<namespace>/smithery/synergies/<synergy_id>.json`
```json
{
  "material_a": "smithery:iron",
  "material_b": "mymod:tin",
  "effects": {
    "sword": { "id": "smithery:sharp", "params": { "damage": 1.5 } },
    "pickaxe": { "id": "smithery:magnetized" }
  }
}
```

**Java API:**
```java
SmitheryAPI.registerSynergy(
    SynergyBuilder.create("mymod:tin_iron_synergy")
        .materials("smithery:iron", "mymod:tin")
        .addEffect(ToolType.SWORD, "smithery:sharp", Map.of("damage", 1.5f))
        .addEffect(ToolType.PICKAXE, "smithery:magnetized")
        .build()
);
```

---

## 9. Alloy System

### 9.1 Alloy Registry

Alloys are a separate registry from base materials. An alloy is itself a full Material (all MaterialStats apply), plus an alloy definition. Alloys support **2 to 4 component materials**.

```java
AlloyDefinition {
    ResourceLocation alloyMaterialId;
    List<AlloyComponent> components;  // 2–4 entries
    float minTemp;                    // forge must meet or exceed this
}

AlloyComponent {
    ResourceLocation materialId;
    int ratio;
}
```

### 9.2 Ratio System

Ratios scale uniformly across fluid volumes. The minimum batch = 1 ratio-unit = `ratio × 144 mB` per component.

**2-component example — Bronze (2 Copper : 1 Tin → 3 Bronze):**
- 288 mB Copper + 144 mB Tin → 432 mB Bronze
- 576 mB Copper + 288 mB Tin → 864 mB Bronze

**3-component example — Constantan (1 Gold : 1 Silver : 1 Copper → 3 Constantan):**
- 144 mB Gold + 144 mB Silver + 144 mB Copper → 432 mB Constantan

**4-component example — Invar (3 Iron : 1 Nickel : 1 Copper : 1 Tin → 6 Invar):**
- 432 mB Iron + 144 mB Nickel + 144 mB Copper + 144 mB Tin → 864 mB Invar

### 9.3 Alloy Conflict Resolution

When a forge contains multiple molten fluids, several alloy recipes may be simultaneously satisfiable. Recipes are also allowed to share component materials — this is intentional and handled by a priority-and-reservation algorithm.

**Core rule: more complex recipes (more components) always win over simpler ones that share the same materials.**

Example: Silver + Gold = Electrum (2-component), Gold + Silver + Copper = Constantan (3-component).  
If all three fluids are present, the forge must detect Constantan first and reserve Gold and Silver for it before Electrum can claim them.

**Processing algorithm (runs every 20 ticks):**

```
1. Collect all fluids present in the forge with quantity ≥ 1 minimum-batch unit (144 mB)
2. Find all registered alloy recipes where ALL component fluids satisfy their required ratio
3. Sort matching recipes:
   a. PRIMARY sort: component count DESCENDING (most complex first)
   b. TIEBREAKER: earliest component fluid in forge insertion order (top → bottom as seen in GUI)
4. Maintain a "reserved fluid" pool starting empty
5. Iterate through sorted recipe list:
   a. Check if ALL of this recipe's components are available in the non-reserved pool at required ratio
   b. If YES → reserve the minimum batch of each component, add recipe to the execute queue
   c. If NO (any component is already reserved) → skip this recipe this cycle
6. Execute all queued recipes simultaneously, producing their output fluids
7. Repeat next cycle — any remaining fluid continues trying
```

**Why this works:**
- Complex recipes claim their materials first, so simpler sub-recipes never steal from them
- Non-conflicting recipes (no shared components) all process in the same cycle
- A player who adds too many materials accidentally may get unexpected alloys — this is a natural consequence and part of the learning curve. The Smithery Field Guide clearly documents all alloy recipes

**Constraint on multi-component alloys:**
No component material in a 3- or 4-component alloy recipe may share all its components with a 2-component recipe using only those same materials. (Meaning: if Gold+Silver = Electrum is registered, you cannot also register a 3-component alloy that uses *only* Gold and Silver as two of its components — it must introduce a third distinct material. This prevents ambiguous priority situations.)

### 9.4 Alloy JSON Format

`data/<namespace>/smithery/alloys/<alloy_id>.json`

```json
{
  "result": "smithery:constantan",
  "minTemp": 1085.0,
  "components": [
    { "material": "smithery:gold",   "ratio": 1 },
    { "material": "smithery:silver", "ratio": 1 },
    { "material": "smithery:copper", "ratio": 1 }
  ]
}
```

---

## 10. Repair System

### 10.1 Sharpening Stones

**Sharpening Stones** are tiered repair consumables applied at the vanilla Anvil.

**Tiers match harvest levels:**

| Stone Tier | Harvest Level | Eligible Repair Target |
|---|---|---|
| Tier 0 | 0 | Tools where max part harvest level ≤ 0 |
| Tier 1 | 1 | Tools where max part harvest level ≤ 1 |
| Tier 2 | 2 | Tools where max part harvest level ≤ 2 |
| Tier 3 | 3 | Tools where max part harvest level ≤ 3 |
| Tier 4 | 4 | Tools where max part harvest level ≤ 4 |

**Stone tier requirement logic:**
- Inspect all parts of the target tool
- Find the highest `harvestLevel` across all part materials
- Required stone tier = that value

**Crafting stones:**
- Cast any material's molten fluid (at least `144 mB`) into a **Sharpening Stone Mold** at the Casting Basin
- The resulting stone tier = the material's `harvestLevel`
- Any material sharing a harvest level can produce the same tier stone (Copper and Iron both at level 2 → both produce Tier 2 stones)

**Repair amount per stone:** Restores 25% of tool's max durability *(TBD — balance pass)*  
**Anvil XP cost:** TBD

---

## 11. Data-Driven Override System (Datapack JSON)

### 11.1 Material Override

`data/<namespace>/smithery/materials/<material_id>.json`

```json
{
  "parent": "smithery:iron",
  "harvestLevel": 3,
  "miningSpeed": 8.0,
  "attackDamage": 2.5,
  "durabilityPerIngot": 200,
  "meltingTemp": 1538.0,
  "moltenColor": "0xFFFF8800",
  "modifierSlots": {
    "sword_blade": 3,
    "pick_head": 2
  },
  "modifiers": {
    "sword": [
      { "id": "smithery:sharp", "params": { "damage": 3.0 } }
    ],
    "pickaxe": [
      { "id": "smithery:magnetized" }
    ]
  }
}
```

Fields omitted inherit from `parent`. If no parent is specified and the material already exists in the registry, missing fields are inherited from the existing registration. Use `"remove": true` at the root level to unregister a material entirely.

### 11.2 Alloy Override / Addition

`data/<namespace>/smithery/alloys/<alloy_id>.json`

```json
{
  "result": "smithery:bronze",
  "minTemp": 1085.0,
  "components": [
    { "material": "smithery:copper", "ratio": 2 },
    { "material": "mymod:tin",       "ratio": 1 }
  ]
}
```

Components list supports 2–4 entries. The conflict resolution algorithm handles any overlapping recipes automatically.

### 11.3 Modifier Param Override

`data/<namespace>/smithery/modifiers/<modifier_id>.json`

```json
{
  "id": "smithery:sharp",
  "params": {
    "damage": 4.0
  }
}
```

Modifier *behavior* is Java-defined and cannot be overridden via data. Only numeric/string params are data-overridable.

### 11.4 Smelting Input Recipe

`data/<namespace>/smithery/melting/<recipe_id>.json`

```json
{
  "input": { "tag": "forge:ores/copper" },
  "output": { "fluid": "smithery:molten_copper", "amount": 288 },
  "minTemp": 1085.0
}
```

---

## 12. Java Modder API

### 12.1 Registering a Material

```java
// Call during MaterialRegistryEvent or similar registration event
SmitheryAPI.registerMaterial(
    MaterialBuilder.create("mymod:mithril")
        .harvestLevel(3)
        .miningSpeed(9.0f)
        .attackDamage(3.0f)
        .durabilityPerIngot(300)
        .meltingTemp(1600f)
        .moltenColor(0xFF88CCFF)
        .modifierSlots(PartType.SWORD_BLADE, 3)
        .modifierSlots(PartType.PICK_HEAD, 2)
        .addModifier(ToolType.SWORD, "smithery:sharp", Map.of("damage", 4.0f))
        .addModifier(ToolType.PICKAXE, "smithery:magnetized")
        .build()
);
```

### 12.2 Registering an Alloy

```java
// 2-component alloy
SmitheryAPI.registerAlloy(
    AlloyBuilder.create("mymod:mithril_steel")
        .addComponent("mymod:mithril", 1)
        .addComponent("smithery:iron", 1)
        .minTemp(1600f)
        .build()
);

// 3-component alloy (conflict resolution handles priority automatically)
SmitheryAPI.registerAlloy(
    AlloyBuilder.create("mymod:mithril_bronze")
        .addComponent("mymod:mithril", 1)
        .addComponent("smithery:iron", 1)
        .addComponent("smithery:copper", 2)
        .minTemp(1650f)
        .build()
);
```

### 12.3 Overriding or Removing an Existing Material

```java
// Override specific stats — other stats are unchanged
SmitheryAPI.overrideMaterial("smithery:iron", builder ->
    builder.harvestLevel(3).attackDamage(2.5f)
);

// Remove a material and all its auto-generated content
SmitheryAPI.removeMaterial("smithery:gold");
```

### 12.4 Registering a Custom Modifier

```java
SmitheryAPI.registerModifier(
    ModifierBuilder.create("mymod:lifesteal")
        .onAttackEntity((context) -> {
            context.getAttacker().heal(context.getDamageDealt() * 0.1f);
        })
        .durabilityMultiplier(0.9f)
        .build()
);
```

### 12.5 Registering a New Tool Type

```java
SmitheryAPI.registerToolType(
    ToolTypeBuilder.create("mymod:spear")
        .addPart(PartType.SPEAR_HEAD, 1, DurabilityRole.ADDITIVE)
        .addPart(PartType.HANDLE, 1, DurabilityRole.ADDITIVE)
        .addPart(PartType.BINDER, 1, DurabilityRole.MULTIPLIER)
        .setItemClass(SpearItem.class)
        .setRecipePattern(new String[]{"SH ", "  H ", "  B "}, ...)
        .build()
);
```

### 12.6 Registering a Post-Craft Modifier Item

```java
SmitheryAPI.registerModifierItem(
    ModifierItemBuilder.create("smithery:wither_star_modifier")
        .sourceItem(Items.NETHER_STAR)
        .appliesModifier("smithery:nether_sharpened", Map.of("damage", 6.0f))
        .xpCost(30)
        .build()
);
```

---

## 13. Starting Content

### 13.1 Materials

| ID | Harvest | Speed | Damage | Dur/Ingot | Melt Temp | Sword Modifier | Pick Modifier |
|---|---|---|---|---|---|---|---|
| `smithery:copper` | 2 | 6.0 | 5.0 | 80 | 1,085°C | Verdant (+poison chance) | Corrosive (−armor on hit) |
| `smithery:gold`   | 2 | 12.0 | 4.0 | 32 | 1,064°C | Lucky Strike (+XP from kills) | Gilded (bonus XP from ore) |
| `smithery:iron`   | 2 | 6.5 | 4.0 | 150 | 1,538°C | Sharp (+2 dmg → 6 sword) | Magnetized (pulls drops) |

Sword damage = blade material's `attackDamage` + any sword modifier bonuses
(SHARP is iron/diamond/netherite's flavor — +2/+3/+4 respectively, closing the
gap to vanilla parity: iron 4+2=6, diamond 4+3=7, netherite 4+4=8).

### 13.2 Tool Types

| Type | Class | Parts |
|---|---|---|
| Sword | `SwordType` | Blade × 1, Guard × 1, Handle × 1, Binder × 1 |
| Pickaxe | `PickType` | Pick Head × 2, Handle × 2, Binder × 1 |

### 13.3 Fuels & Heat Sources

| Source | Type | Max Temp | Notes |
|---|---|---|---|
| Lava | Fluid fuel | 1,650°C | Default; consumed over time |
| Resistive Coil (T1) | RF block | 1,200°C | ~851–960 RF/t; non-ferrous metals only |
| Arc Coil (T2) | RF block | 3,000°C | ~3,845–7,500 RF/t; covers all materials |

### 13.4 Forge Blocks

| Block ID | Purpose |
|---|---|
| `smithery:forge_brick` | Structural wall/floor/ceiling |
| `smithery:forge_controller` | GUI and logic hub |
| `smithery:forge_fuel_port` | Lava input + internal fuel storage |
| `smithery:forge_drain` | Molten fluid output |
| `smithery:forge_rf_coil_t1` | Tier 1 RF heat source (priority over lava) |
| `smithery:forge_rf_coil_t2` | Tier 2 RF heat source (priority over lava) |

### 13.5 Starting Material Synergies

| Materials | Synergy | Sword Effect | Pickaxe Effect |
|---|---|---|---|
| Iron + Copper | **Galvanic** | +2 dmg vs. mobs in water/rain | No durability loss mining underwater |
| Iron + Gold | **Gilded** | +25% XP from kills | +25% XP from ore mining |
| Copper + Gold | **Verdant Veil** | 15% chance Poison I (3s) on hit | 10% chance bonus flint/stone drop |

### 13.6 Post-Craft Modifier Items

| Item Source | Modifier Applied | Effect |
|---|---|---|
| Nether Star | `smithery:nether_sharpened` | +6 attack damage |

---

## 14. Technical Constants

| Constant | Value |
|---|---|
| mB per ingot | 144 mB |
| mB per nugget | 16 mB |
| mB per ore/raw | 288 mB |
| mB per storage block | 1,296 mB |
| Forge capacity per interior block | 1,000 mB |
| Alloy check interval | 20 ticks (1 second) |
| Max alloy components | 4 |
| Alloy conflict rule | Highest component count wins; forge insertion order breaks ties |
| Forge heat ramp (cold → max) | ~30 seconds |
| Cooling thermal mass divisor | `cool_rate = base / (1 + stored_mB / 1000)` |
| Sharpening stone repair amount | 25% of max durability (TBD) |
| RF Coil T1 max temp | 1,200°C |
| RF Coil T2 max temp | 3,000°C |
| RF Coil T1 coefficient | 0.8 RF/°C |
| RF Coil T2 coefficient | 2.5 RF/°C |
| RF Coil thermal mass divisor | 200 mB per 1 RF/t surcharge |
| Synergy slot cost | 0 (synergies are free bonuses) |

---

## 15. Open Questions (TBD Before Coding)

| # | Question |
|---|---|
| 1 | Exact NeoForge 26.1.2 Minecraft version target — confirm |
| 2 | Exact 3×3 grid slot layout for Sword and Pickaxe shaped recipes |
| 3 | Part size scalars for durability (how much does a pick head vs handle contribute per ingot?) |
| 4 | Binder multiplier value per material — is this a MaterialStats field or a PartType field? |
| 5 | Sharpening stone repair amount per use (currently 25% — balance pass needed) |
| 6 | Anvil XP cost for post-craft modifier items |
| 7 | Lava burn rate (mB/tick consumed at max temp) |
| 8 | Casting Basin — does it connect to forge directly or only via pipes/buckets? |
| 9 | Cast Mold items — single-use clay molds that produce a permanent cast, or always permanent? |
| 10 | Texture strategy for auto-generated part items (layer tinting system vs. per-material textures) |
| 11 | What happens to molten metal in a forge that is destroyed? (drop buckets, void, or serialize?) |
| 12 | RF Coil internal energy buffer size (how long does it sustain on interruption?) |
| 13 | RF Coil crafting recipes (deferred — noted as TBD) |
| 14 | Should the Smithery Field Guide auto-open on first forge interaction, or just be given as an item? |
| 15 | Future real metals planned (for melt temp / harvest level pre-planning): tin, silver, nickel, lead, etc.? |
