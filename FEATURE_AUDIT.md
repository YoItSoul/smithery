# Smithery Feature Audit — vs. Tinkers' Construct 2 (1.12.2) + Construct's Armory

**Date:** 2026-07-13
**Purpose:** Gap analysis against the two genre benchmarks: Tinkers' Construct 1.12.2 (verified against the `1.12` source branch) and Construct's Armory 1.12.2 (verified against its repo + wiki). This is a planning document — it lists what those mods shipped, what Smithery has, and what we want to add. Where Smithery deliberately does something differently, that's recorded as a **design divergence**, not a gap.

Legend: ✅ have it · 🔷 have a different take on it (divergence, by design) · 🚧 partial · ❌ gap · 🚫 explicitly out of scope

---

## 1. Verdict at a glance

Smithery's **infrastructure** is at or beyond TC2 parity: the Forge multiblock simulates temperature properly (heat-up/cool-down, thermal mass, open/closed top) where TC's smeltery only threshold-checks fuel temp; multi-fuel, alloying, entity melting, casting, JEI, and a modifier engine are all live. What TC2 has that we don't is **content breadth**: ~21 tools vs our 8, ~40 material traits vs our ~12 modifiers, ~25 modifiers vs our ~12, plus repair, books, and gadgets.

Construct's Armory is the bigger gap: we have its stat skeleton (Core/Plates/Trim, formulas, 21 materials) but **none of its gameplay** — no assembly path, no worn rendering, no armor traits/modifiers, no armor repair. CoA's trait system (38 armor-specific traits with on-hurt/on-fall/on-jump hooks) is the heart of the mod and is what we're committing to add.

---

## 2. Stations & crafting flow

| TC2 feature | TC2 behavior | Smithery |
|---|---|---|
| Crafting Station | 3×3 grid, keeps items, chest passthrough | 🔷 vanilla crafting table does assembly (design: no new GUIs) |
| Stencil Table + Blank Patterns | pattern-per-part gating | 🔷 not needed — Part Press selects part in-world; casting impresses with part/template |
| Part Builder | GUI part cutting for non-metals (+shard byproduct) | 🔷 **Part Press** (in-world, redstone-driven) covers non-meltables |
| Tool Station / Tool Forge | assemble, modify, **repair**, rename; Forge gates large tools | 🔷 assembly at crafting table, modifiers at anvil; ❌ **no repair**, ❌ **no rename support**, ❌ **no progression gate for "large" tools** (we have no large tools yet) |
| Pattern/Part chests | station-linked storage | 🚫 GUI-heavy, out of scope |

**Takeaways:** our station-less flow is intentional and complete except: (a) repair (§6), (b) a progression gate equivalent if/when we add heavy tools — candidate: large parts require casting (Forge-only), never the Part Press, which matches the press/forge domain split.

## 3. Forge vs. Smeltery

| Feature | TC2 | Smithery |
|---|---|---|
| Free-form multiblock | rectangular footprints, any height | ✅ more free-form (BFS shell, partial builds, open/closed top) |
| Fuel | liquid fuel in seared tanks; hotter = faster | ✅ multi-fuel registry + real temperature simulation (better) |
| Melting / ore doubling | ✅ (2× ore) | ✅ (ore = 288 mB = 2 ingots) |
| Alloying | 5 base alloys, auto | ✅ data-driven `AlloyRecipe`, auto + GUI toggle; 🚧 only 3 alloy recipes (content, not tech) |
| Entity melting | mobs → blood, mapped entities → fluids | ✅ `ForgeMobDrops` (modder-extensible; arguably better) |
| Faucet + casting channel | pour routing | 🔷 drain pump + fluid pipes |
| Item input automation | drain accepts items | ✅ item port |
| Seared Furnace (batch smelting multiblock) | ✅ | ❌ — worth considering later; low priority |
| Tinker Tank (bulk fluid storage multiblock) | ✅ | ❌ — Forge interior already stores fluid; low priority |
| Seared decorative set (bricks/stairs/slabs, glass) | large block family | ❌ Furnace Bricks only — **add decorative shell variants** (cheap win, big build-appeal) |

## 4. Tool roster

**Smithery (8):** sword, pickaxe, axe, shovel, hoe, spear, bow, arrow.
**TC2 (21+):** pickaxe, shovel, hatchet, mattock, kama, broadsword, longsword, rapier, frypan, battlesign, shortbow, shuriken, arrows / hammer, excavator, lumberaxe, scythe, cleaver, longbow, crossbow, bolts.

Gaps that matter, in rough priority:
1. ❌ **AoE tool tier** — hammer (3×3 pick), excavator (3×3 shovel), lumberaxe (tree-feller), scythe (3×3×3 harvest). This is TC2's endgame draw. Needs large parts (e.g., hammer head = 8 ingots) → natural Forge-casting progression gate.
2. ❌ **Weapon variety** — at least one alternative melee identity (rapier's armor-pierce/speed, cleaver's slow+beheading). Our spear + lunge is one; 2–3 more distinct identities close most of the gap.
3. ❌ **Crossbow/bolts** — bolt cores (metal cast onto shafts) are a genuinely distinct ammo mechanic; medium priority.
4. 🚫 Frypan/battlesign/shuriken — novelty items, only if we want the flavor.

## 5. Materials, traits, modifiers

- **Materials:** 28 built-in vs TC2's ~22+compat — ✅ parity in count, and our non-meltable/bowstring classes map to TC's craftable/bowstring material split. TC has **no bedrock-tier**; our harvest 5 is extra headroom.
- **Traits:** ❌ **the big content gap.** TC2 ships ~40 traits with strong identities (Stonebound, Jagged, Autosmelt, Momentum, Insatiable, Alien, Petramor, Writable…). We have ~12 modifiers + 3 synergies. Our modifier action library (codec-composable) + per-material-per-tool grants is the right engine; it needs ~2–3× more distinct effects, especially **stateful ones** (Momentum's ramping speed, Jagged/Stonebound's durability-scaled stats, Alien's evolving stats).
- **Modifiers (player-applied):** we have haste/sharp/luck-analogs. Missing TC2 staples worth adding: ❌ **silk-touch analog**, ❌ **reinforced/unbreakable ladder**, ❌ **soulbound**, ❌ **fiery**, ❌ **knockback**, ❌ **AoE expanders** (once AoE tools exist), ❌ **embossment** (donate a material's traits — pairs beautifully with our synergy system), ❌ **moss/mending analog** (self-repair).
- **Extra modifier slots:** TC2 grants slots via Paper parts (Writable). We grant slots per material part — ✅ same lever, already more flexible.
- ✅ **Synergies are our differentiator** — TC2 has nothing equivalent; keep investing here.

## 6. Repair (gap in both audits)

TC2: station repair with head material, sharpening-kit + flint grid repair, moss self-repair. CoA: same via core material + **Polishing Kit** + mending/parasitic/ecological traits.
Smithery: ❌ nothing. Plan (design §10, PLAN Step 3): **Sharpening Stones** (tools) + **Polishing Stone** armor analog, both castable, applied at anvil — one system, both audits closed.

## 7. World gen, gadgets, books

- **World gen:** TC2 = cobalt/ardite ore + slime islands. Smithery = none, **by design** (materials melt from vanilla resources). Revisit only if we add an exclusive endgame metal; note netherite chain already fills the "special endgame material" slot.
- **Gadgets:** slime sling/boots, EFLN, piggyback, drying rack, brownstone, wooden rail/hopper… 🚫 out of scope as a module; our Red Slime block already covers the bouncy-block niche as an easter egg.
- **Books:** TC2 ships 5 progression manuals; ours is a one-page scaffold. 🚧 Field guide content is a real onboarding gap — a multiblock mod without in-game docs is hard to discover. Priority: medium-high, after armor.
- **Advancements:** TC2 has a progression tab; ❌ we have none. Cheap, good onboarding.

---

## 8. Construct's Armory — the adoption list

We already have: ✅ Core/Plates/Trim part structure, ✅ CoA's stat formula shape (re-tuned to vanilla-1.21 scale), ✅ full-set defense budget split (their 0.14/0.30/0.40/0.16 verbatim), ✅ 21 materials with armor stats, ✅ cast volumes per part.

To add, in priority order:

1. **Assembly + worn rendering** (already PLAN Steps 1–2). CoA used an Armor Station; we assemble at the crafting table like tools. Include CoA's **broken-armor** behavior: at 0 durability the piece stays equipped but grants nothing (renders damaged) instead of shattering — it's a great loop with repair.
2. **Armor trait hooks.** Extend the modifier action/event system with armor lifecycles: `onHurt` (pre-damage, can modify), `onDamaged` (post), `onKnockback`, `onFalling`, `onJumping`, `onArmorTick`, `onEquip/onUnequip`, plus a protection-math hook. This is the enabling work for everything below.
3. **Armor traits per material** (~1–2 each, CoA-inspired identities re-flavored for our material set), e.g.: iron → Magnetic (reuse), copper → bonus-XP, gold → something gilded-flavored, slime → bounce/fall-negate, prismarine → swim speed + wet bonus (galvanic synergy already knows "wet"), blaze/magma-analog → fire-feedback, bone-analog → toughness, netherite → knockback resist + fire resist, bedrock → the Indomitable-style flat bonus. Full CoA trait table is in the audit source notes for mining ideas.
4. **Armor modifiers:** speedy, reinforced, diamond/emerald, typed resistances (fire/blast/projectile), mending analog, soulbound, high stride, sticky (thorns-slow), amphibious (reserve air), frost-walker analog, glowing. **Gauntlet trio** (attack damage / attack speed / reach as chest-or-arm modifiers) is distinctive and cheap once hooks exist.
5. **Polishing Stone** (armor repair kit; see §6) + **Polished** modifier (raise toughness to kit material's).
6. **Embossment for armor** (donor material's traits, no stats) — shared implementation with tool embossment (§5).
7. **Per-piece protection caps** — CoA caps each piece's damage-absorption share (0.12/0.24/0.32/0.12); adopt to keep stacked traits from breaking balance.
8. **Accessories (belt/knapsack/goggles/cloak)** — CoA's travel-gear system. ⚠️ Deferred: storage accessories need new GUIs (against our design rule) and render layers are heavy. If we ever do it, start with the GUI-less ones: cloak (slow-fall/invis), goggles (night vision), high-stride boots already covered as a modifier.

---

## 9. Consolidated priority queue

| # | Work item | Closes gap from | Size |
|---|---|---|---|
| 1 | Armor assembly recipes + worn rendering + broken-armor state | CoA | M |
| 2 | Armor trait/modifier hook layer (onHurt/onDamaged/onFall/onJump/onTick/equip) | CoA | M |
| 3 | Armor traits for existing materials + first modifier batch (speedy, reinforced, resistances, soulbound) | CoA | L |
| 4 | Repair: Sharpening Stones (tools) + Polishing Stones (armor) | TC2 + CoA | M |
| 5 | Trait depth pass on tools: stateful effects (momentum/jagged/stonebound/autosmelt/silky/fiery analogs) | TC2 | L |
| 6 | AoE tool tier (hammer/excavator/lumberaxe/scythe) with cast-only large parts as the progression gate | TC2 | L |
| 7 | Embossment (tools + armor) | TC2 + CoA | S–M |
| 8 | Field guide content + advancements | TC2 | M |
| 9 | Decorative Furnace Brick family (stairs/slabs/glass/window) | TC2 | S |
| 10 | 1–2 new weapon identities (rapier-style, cleaver-style) + crossbow/bolts | TC2 | M |
| 11 | More alloy content (needs more metals — tin/silver/nickel/lead question, design §15) | TC2 | M |

Out of scope (recorded): gadgets module, pattern/stencil system, station GUIs, worldgen ores/slime islands, seared furnace / tinker tank, storage accessories.
