# Translating Smithery

Smithery would love help getting translated into more languages. The mod is
playable in any locale Minecraft supports — all in-game text routes through
language files. If you'd like to contribute a translation, this is the place.

---

## TL;DR

1. Copy [`src/main/resources/assets/smithery/lang/en_us.json`](src/main/resources/assets/smithery/lang/en_us.json)
   to a new file in the same directory, named `<locale>.json` (e.g. `de_de.json`,
   `fr_fr.json`, `ja_jp.json`).
2. Translate the **values** (the right-hand strings). Leave the **keys** (the
   left-hand strings) untouched — they are referenced from code and must match
   English exactly.
3. Keep any `%s`, `%1$s`, `%2$s` placeholders in the same order. They are
   substituted at runtime with values like material names, numbers, or
   temperatures.
4. Open a pull request against `main` — see [`CONTRIBUTING.md`](CONTRIBUTING.md)
   for the workflow. Title it `i18n: add <locale>` so it's easy to find.

That's it. You don't need to touch any Java code to add a translation.

---

## Locale file format

Minecraft language files are JSON dictionaries. Smithery's English file lives at:

```
src/main/resources/assets/smithery/lang/en_us.json
```

There are about **160 keys**. They group roughly as:

| Prefix                                  | What it labels                                  |
| --------------------------------------- | ----------------------------------------------- |
| `itemGroup.smithery.*`                  | Creative-tab names                              |
| `block.smithery.*`                      | Block names (forge controller, casting table, molten blocks, impressed sand variants…) |
| `fluid.smithery.*`                      | Molten fluid display names                      |
| `item.smithery.*`                       | Item names (tools, buckets, the `part_combo` pattern) |
| `smithery.material.smithery.*`          | Material names (iron, gold, prismarine…)        |
| `smithery.part.smithery.*`              | Tool part names (pick head, handle, binder…)    |
| `smithery.tool.smithery.*`              | Tool type names (pickaxe, sword)                |
| `smithery.modifier.smithery.*`          | Modifier names + `.description` lines           |
| `smithery.synergy.smithery.*`           | Synergy names                                   |
| `tooltip.smithery.*`                    | In-tooltip lines (stats, parts, modifier hints) |
| `jei.smithery.*`                        | JEI category titles and recipe labels           |
| `book.smithery.*`                       | Field-guide entries                             |

Locale codes follow [Minecraft's official list](https://minecraft.wiki/w/Language)
— lowercase, underscore-separated (e.g. `de_de`, `pt_br`, `zh_cn`).

---

## Placeholders to preserve

A few strings contain printf-style placeholders that get filled in at runtime.
Keep them in the right order:

| Key                                      | Placeholders                                 |
| ---------------------------------------- | -------------------------------------------- |
| `item.smithery.part_combo`               | `%1$s` = material name, `%2$s` = part name. The default `"%1$s %2$s"` yields `"Iron Pick Head"`. Swap or wrap as the target language needs (e.g. `"%2$s en %1$s"`). |
| `tooltip.smithery.tool.modifier_slots`   | Two `%s` — used slots, then total.           |
| `tooltip.smithery.part.in_tool`          | `%s` = tool name.                            |
| `tooltip.smithery.part.harvest_level`    | `%s` = numeric harvest tier.                 |
| `tooltip.smithery.part.durability_add`   | `%s` = durability bonus.                     |
| `tooltip.smithery.part.durability_mul`   | `%s` = multiplier (e.g. `1.20`).             |
| `tooltip.smithery.part.attack_damage`    | `%s` = damage value.                         |
| `tooltip.smithery.part.mining_speed`     | `%s` = speed value.                          |
| `tooltip.smithery.tool.durability`       | `%s` = max durability.                       |
| `tooltip.smithery.tool.attack_damage`    | `%s` = damage value.                         |
| `tooltip.smithery.tool.mining_speed`     | `%s` = speed value.                          |
| `jei.smithery.melting.temperature`       | `%s` = temperature in °C.                    |
| `jei.smithery.melting.amount`            | `%s` = millibuckets.                         |
| `jei.smithery.casting.amount`            | `%s` = millibuckets.                         |

The unit characters (`°C`, `mB`, `+`, `×`) are part of the string — you can keep
them or substitute the language's local convention.

---

## Testing your translation

1. Run `./gradlew runClient` (see [`CONTRIBUTING.md`](CONTRIBUTING.md) for the dev setup).
2. In Minecraft, open **Options → Language** and pick your locale. The mod's
   strings should switch over immediately.
3. Hover over a Smithery tool, a part item, and the Forge Controller to sanity-
   check tooltips. Open JEI's **Tool Assembly**, **Forge Melting**, **Smeltery
   Casting**, and **Part Press** categories to check the recipe labels.

If a string appears as a raw key (e.g. `tooltip.smithery.tool.stats`), that
means the key is missing or misspelled in your file. Compare against `en_us.json`.

---

## Updating an existing translation

When new English keys land in `en_us.json`, existing locale files don't break —
Minecraft falls back to the English value for any missing key. To bring a
translation up to date, diff the locale file against `en_us.json` and add the
new entries.

---

## Questions or stuck?

Open an issue tagged `i18n` or `translation`. If you're partway through a
language and want feedback before finishing, that's fine too — open a draft PR
and ask for review.

Thank you for translating!
