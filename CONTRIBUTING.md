# Contributing to Smithery

Thanks for your interest in contributing! Smithery is built to be modder-friendly
and welcomes patches, ideas, bug reports, and design discussion. The license is
explicitly fork- and pull-request-friendly — see [`LICENSE`](LICENSE) for the legal
terms, this document for the practical workflow.

---

## Quick workflow

1. **Fork** the repository on GitHub (or your Git host of choice).
2. **Clone** your fork locally:
   ```sh
   git clone https://github.com/<your-user>/smithery.git
   cd smithery
   ```
3. **Branch** off `main` for each change:
   ```sh
   git checkout -b feat/my-cool-thing
   ```
4. **Make your changes.** Build often:
   ```sh
   ./gradlew build
   ```
5. **Test in-game** with `./gradlew runClient` where the change is user-visible.
6. **Commit** with a short, descriptive message.
7. **Push** to your fork and **open a pull request** against `main` in the upstream
   repository, describing the change and any test results.

---

## What to work on

- Issues tagged `good first issue` or `help wanted` are great starting points.
- **Translations are very welcome** — see [`TRANSLATIONS.md`](TRANSLATIONS.md)
  for a no-Java workflow that adds a new locale by copying one JSON file.
- Larger features — new tool types, new subsystems, anything that touches the
  Forge multiblock simulation — should be discussed in an issue first to align on
  design.
- The long-term plan lives in [`SMITHERY_DESIGN.md`](SMITHERY_DESIGN.md). Please
  align with it for substantial work; if you think the design needs to change,
  open an issue to discuss before sending a PR.

---

## Code style

- **Language:** Java 21 syntax. Use records, switch expressions, sealed types, and
  pattern matching where they read naturally.
- **NeoForge 26.1.2 APIs.** Avoid older Forge or Yarn names.
- **Package layout** (current convention):
  - `com.soul.smithery.api` — modder-facing types (Material, PartType, ToolType,
    Modifier, Synergy, ...).
  - `com.soul.smithery.registry` — DeferredRegister setups and registry helpers.
  - `com.soul.smithery.content` — built-in registrations of the API types.
  - `com.soul.smithery.block`, `item`, `client`, `network` — concrete blocks,
    items, client-side rendering, and packets respectively.
- **Clarity over cleverness.** Comments should explain *why*, not *what*. Skip
  obvious comments; add them when the next reader would otherwise need to dig
  through commit history.
- **No speculative APIs.** Add interfaces and abstractions when there are real
  consumers, not before.
- **Run the build before each commit.** A PR that doesn't compile won't land.

---

## Submitting bug reports

Open an issue with:

- **What you did** — concrete steps to reproduce, including any in-game actions.
- **What you expected to happen.**
- **What actually happened.**
- **Versions** — Minecraft, NeoForge, and Smithery (the latter from `gradle.properties`
  or the in-game mod list).
- **Logs** — paste the relevant section of `logs/latest.log`, or attach the crash
  report from `crash-reports/` if applicable. Use a paste service for anything
  longer than ~50 lines.
- **Screenshots or short video** for visual bugs.

---

## Licensing of contributions

By submitting a contribution, you agree to license it as described in
[Section 3 of the LICENSE](LICENSE) — granting the project a perpetual,
non-exclusive, sublicensable right to incorporate and distribute your
contribution as part of Smithery. You retain ownership of your own work.

---

## Questions and discussion

For design questions or open-ended discussion, open an issue and tag it
`discussion`. Prefer GitHub issues over chat where possible so the
conversation is searchable later.
