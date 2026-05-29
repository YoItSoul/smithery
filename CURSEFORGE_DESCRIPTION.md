<p align="center">
  <img src="https://media.forgecdn.net/attachments/1704/799/title-png.png" alt="Smithery" />
</p>

<p align="center">
  <strong>Build tools from interchangeable material parts in a multiblock forge.</strong><br/>
  <em>Inspired by the classics. Rebuilt from the ground up for modern Minecraft.</em>
</p>

<p align="center">
  <a href="https://github.com/YoItSoul/smithery"><img src="https://img.shields.io/badge/Source_Code-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="Source Code" /></a>
  <a href="https://ko-fi.com/yoitsoul"><img src="https://img.shields.io/badge/Support_Development-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Ko-fi" /></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1.2-62B47A?style=flat-square&logo=minecraft&logoColor=white" alt="Minecraft" />
  <img src="https://img.shields.io/badge/NeoForge-26.1.2-DC8A33?style=flat-square" alt="NeoForge" />
  <img src="https://img.shields.io/badge/Java-21+-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/License-Source--Available-3B5998?style=flat-square" alt="License" />
</p>


<p align="center">
  <img src="https://img.shields.io/badge/WORK_IN_PROGRESS-More_content_coming_soon!-FFA500?style=for-the-badge&labelColor=2b2b2b" alt="Work in Progress" />
  <br/>
  <img src="https://img.shields.io/badge/Some_textures_are_placeholders-888888?style=flat-square" alt="Some textures are placeholders" />
</p>

> The lists below represent what's implemented today. Many more materials, modifiers, synergies, and alloys are actively being added. Expect frequent updates!


---

## What is Smithery?

Smithery is a **modular tool crafting mod** for NeoForge. Smelt raw materials in a multiblock Forge, alloy them into advanced metals, cast them into parts, and assemble those parts into fully customizable tools and armor at a crafting table.

Every tool is made from **interchangeable, material-specific parts** — pair an iron blade with a copper guard and a gold handle, and each part contributes its own stats, modifiers, and visuals. No two tools need to be the same.

---

## Core Features

### Multiblock Forge
Build a fully enclosed Forge from **Furnace Bricks**, a **Forge Controller**, **Fuel Ports**, and **Drains**. No fixed blueprint — design any shape you want. The interior volume determines your fluid capacity (1,000 mB per air block).

- **Open-top or closed-top** — closed top gives 20% faster heating and 20% slower cooling
- **Partial builds still work** — the forge operates as the largest valid sub-structure it can find
- **Temperature simulation** — realistic heat-up, cool-down, and thermal mass (a full forge holds heat longer)
- **Lava-fueled** with RF coil support planned

### Mixed-Material Tools
Assemble tools from distinct material parts. Each part carries its own durability, damage, speed, and harvest level into the final tool.

**8 Tool Types:**

| Tool | Parts |
|---|---|
| Sword | Blade + Guard + Handle + Binder |
| Pickaxe | Head + 2x Handle + Binder |
| Axe | Head + 2x Handle + Binder |
| Shovel | Head + 2x Handle + Binder |
| Hoe | Head + 2x Handle + Binder |
| Spear | Head + 2x Handle + Binder |
| Bow | 2x Limb + Bowstring |
| Arrow | Head + Shaft + Fletching |

**4 Armor Pieces** (Helmet, Chestplate, Leggings, Boots) each assembled from a **Core + Plates + Trim**.

---

### 21+ Materials

Every material has unique stats, modifiers, and visual tinting — all auto-generated at runtime.

| Tier | Materials |
|---|---|
| **Starter** | Wood, Stone, Flint, Copper, Gold, Iron |
| **Specialty** | Lapis, Redstone, Amethyst, Prismarine, Blaze |
| **Advanced** | Diamond, Emerald, Netherite, Bedrock |
| **Organic** | Slime, Red Slime, Resin, Coral |
| **Alloys** | Slimeknightium, and more to discover... |
| **Bowstrings** | String, Flamestring, Breezestring, Kelp String |

---

### Per-Material, Per-Tool Modifiers

Materials don't just change stats — they grant **unique abilities** depending on which tool they're used in:

| Material | Tool | Modifier | Effect |
|---|---|---|---|
| Iron | Pickaxe | **Magnetized** | Pulls item drops within a 5-block radius |
| Copper | Sword | **Verdant** | 15% chance to poison targets |
| Copper | Pickaxe | **Corrosive** | 25% chance to apply Weakness II |
| Gold | Sword | **Lucky Strike** | 1.25x XP from kills |
| Gold | Pickaxe | **Gilded** | 1.25x XP from ores |
| Gold | Pickaxe | **Golden Touch** | Fortune I on ore drops |

---

### Material Synergies

Combine specific materials across parts to unlock **bonus modifiers at no slot cost**:

| Synergy | Materials | Tool | Bonus |
|---|---|---|---|
| **Galvanic** | Iron + Copper | Sword | +2 damage vs wet targets |
| **Gilded** | Iron + Gold | Sword | 1.25x kill XP |
| **Gilded** | Iron + Gold | Pickaxe | 1.25x ore XP |
| **Verdant Veil** | Copper + Gold | Sword | 15% poison chance |

---

### Anvil-Applied Modifiers

Upgrade your tools further at the vanilla Anvil using special items:

| Modifier | Source Item | Effect |
|---|---|---|
| **Sharp** | Nether Quartz | +0.5 damage per level (10 levels) |
| **Haste** | Redstone | +1.5 mining & attack speed per level (10 levels) |
| **Lapis Blessing** | Lapis Lazuli | Fortune & Looting scaling (10 levels) |
| **Nether Sharpened** | Nether Star | +6 attack damage |
| **Lunge** | Phantom Membrane | Forward dash on spear use (3 levels) |
| **Ender Affinity** | Ender Pearl | 30% chance to teleport target on hit |

Modifier slot count is determined by your **Binder material**: Iron = 2 slots, Gold = 3, Netherite = 4, Bedrock = 5.

---

### Alloy System

The forge automatically alloys fluids when the right ratios and temperatures are met:

| Alloy | Inputs | Temperature |
|---|---|---|
| **Netherite** | 4 Gold + 4 Ancient Debris | 2,200 C |
| **Slimeknightium** | 4 Red Slime + 4 Iron | 1,700 C |

---

### Two Part-Production Paths

| Method | Domain | How It Works |
|---|---|---|
| **Forge + Casting Table** | Meltable materials (metals, gems) | Melt item -> molten fluid -> pour into sand cast -> part |
| **Part Press** | Non-meltable materials (wood, flint, slime) | Insert raw item -> redstone pulse -> cut part |

Every material knows which path it belongs to. No overlap, no shortcuts.

---

### Machinery & Blocks

| Block | Role |
|---|---|
| **Forge Controller** | Master block — GUI for temperature, fluids, and forge status |
| **Furnace Bricks** | Structural shell for the forge multiblock |
| **Forge Fuel Port** | Lava input — accepts buckets or fluid pipes |
| **Forge Drain** | Molten fluid output for casting |
| **Forge Item Port** | Item input — accepts items via hopper |
| **Casting Table** | Pour molten metal into sand casts to form parts or ingots |
| **Casting Sand** | Impressed with part shapes to create molds |
| **Fluid Pipe** | Single-fluid transport with per-face IN/OUT control |
| **Part Press** | Redstone-driven part cutter for non-meltable materials |
| **Red Slime Block** | Bouncy, sticky, emits constant redstone signal |

---

### Runtime Asset Generation

Every registered material automatically generates:
- Molten fluid + bucket
- All applicable tool parts (tinted per material)
- Casting recipes, smelting recipes, melting recipes
- Item models and lang entries

**Zero boilerplate JSON per material.** Register a material in Java and everything else follows.

---

## For Modders

Smithery is built from the ground up to be **modder-friendly**:

- **Java Builder API** — register materials, parts, tool types, modifiers, synergies, and alloys with clean builder syntax
- **ForgeMobDrops API** — map any entity class to a fluid for forge mob processing
- **JSON Modifier System** — define new modifiers entirely in data packs using 9 built-in action types
- **Datapack Overrides** — all recipes and balance parameters are data-overridable
- **No boilerplate** — runtime generation handles models, textures, items, and recipes automatically

---

## Roadmap

| Feature | Status |
|---|---|
| Materials, parts, tools, modifiers, synergies | Done |
| Crafting (tool assembly) | Done |
| Multiblock Forge validator | Done |
| Heat / fluid / alloy simulation | In Progress |
| Forge controller GUI | Experimental |
| In-game Field Guide (Modonomicon) | In Progress |
| RF heat coils (Tier 1 + Tier 2) | Planned |
| Casting basin + cast molds | Planned |
| Sharpening stones (repair) | Planned |
| Anvil-applied modifier items | Planned |
| Worn armor textures | Planned |
| Datapack JSON override loaders | Planned |

---

## Acknowledgements

- Built on [NeoForge](https://neoforged.net/)
- Texture style follows vanilla Minecraft conventions
- In-game documentation powered by [Modonomicon](https://github.com/klikli-dev/modonomicon)
- JEI integration for recipe browsing

---

<p align="center">
  <a href="https://ko-fi.com/yoitsoul"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support on Ko-fi" /></a>
</p>

<p align="center">
  <a href="https://github.com/YoItSoul/smithery"><img src="https://img.shields.io/badge/View_Source_Code-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub" /></a>
</p>
