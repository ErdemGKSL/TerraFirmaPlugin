# Cross-cutting tracks

Work that runs alongside every milestone. These tasks are continuous: tick them per milestone, or split them into milestone-specific tasks when they get concrete.

## Art & models

- [ ] **X-ART-01 Image list per milestone** · skills: image-generation
  - Do: at the start of each milestone, list the 2D art that TFC doesn't provide (GUI overlays, HUD frames, buttons, icons) and add them to the `image-generation` skill's `references/backlog.md` with exact sizes and slot coordinates, then generate them through the imagegen MCP. Use placeholders in `content/art/_placeholder/` until approved.
  - Done when (per milestone): no placeholder art remains in the milestone's features.

- [ ] **X-ART-02 Image pipeline upkeep**
  - Do: keep the `imagegen` entry in `.mcp.json`, the `image-generation` skill and `tools/images/postprocess.py` working. Add post-process options when new art types need them (animation strips, GUI sheets).

- [ ] **X-MOD-01 Model generator coverage** · skills: model-authoring
  - Do: add rigs as they become needed (`biped_bird` for M5 poultry, `fish`, `serpentine`), extend the animation library (`run`, `sleep`, `attack`, `swim`), keep the validator in CI for `content/` models.

## HUD & GUIs

- [ ] **X-HUD-01 HUD element catalogue** · skills: betterhud
  - Do: one BetterHud layout per HUD feature, all ids prefixed `tfp_`; placeholders read only snapshots; `tick:` set for slow values. Keep the vanilla-bar hiding sprite list current per MC version.

- [ ] **X-GUI-01 GUI framework** · skills: craftengine-gui-furniture
  - Do: shared Java helpers for textured inventories (title glyph builder via CE codepoints, alignment math, button slots, overlay layers, title refresh throttling) so each device GUI is small.

## Data, i18n, docs

- [~] **X-DATA-01 TFC update tracking**
  - Do: when the pinned TFC commit is bumped, re-run datagen, diff the data/asset reports, update the ledger and open tasks for changed mechanics.
  - Status: `tools/datagen` `TfcPinDrift` lists the watched TFC paths (`src/main/java/net/dries007/tfc`, `src/generated/resources/data/tfc`) changed between the pin in `docs/porting/tfc-pin.txt` and the checkout's HEAD, using git on a local clone (no network). Run `./gradlew :tools:datagen:tfcPinDrift -Ptfc.checkout=<clone>`; it fails if the clone has no git history or lacks the pinned commit. Tests: `TfcPinDriftTest` (8, incl. the real pinned clone, which is empty). Open: running it in CI, an automatic upstream feed, and the per-file ported-header check (`@ <commit>` lines against the pin).

- [~] **X-I18N-01 Locales**
  - Do: every player-facing string is translatable; ship all TFC locales including `tr_tr`; add missing keys for plugin-only UI.
  - Status: datagen `Locales.java` writes `lang/<locale>.json` for all 13 TFC locales (`tr_tr` included) with TFC's keys verbatim and mcresources comments dropped, plus `lang/index.json` with each locale's key, missing and extra counts against en_us. Verified on the pinned checkout: 13 locales, en_us 10075 keys, every locale 10075 keys with 0 missing and 0 extra. Tests: `LocalesTest` (9: real-checkout count and coverage, fixture extras and comments, 7 refusals). Open: the Paper lang loading and our message keys (`tfp.*`), translations of the plugin's own strings, the CraftEngine text components, and the review of each locale's coverage.

- [ ] **X-DOC-01 Keep design docs & skills current**
  - Do: update `ARCHITECTURE.md` and the affected skills in the same change as any design decision; resolve "UNVERIFIED" notes as spikes and features confirm them.

## Quality & performance

- [ ] **X-PERF-01 Performance budgets in CI** · skills: plugin-testing
  - Do: JMH baselines in `docs/perf/` (worldgen chunk, packet rewrites, lore build, collapse decision, rotation network); fail CI on >20% regressions without a note.

- [ ] **X-THR-01 Threading review gate** · skills: paper-folia-threading
  - Do: a checklist in the PR template (no BukkitScheduler; owner-thread access; callbacks only snapshot + enqueue; budgeted commits; packet handlers world-free); all self-tests also run on Folia.

- [ ] **X-VIS-01 Visual budget watch** · skills: craftengine-visuals-pack
  - Do: every PR adding blocks shows the allocation report delta per profile and version; defaults (`1.21: level-3a`, `26: level-2`) must keep ≥ 500 free solid-pool slots.

- [ ] **X-COMPAT-01 Dependency & version updates**
  - Do: track new MC/Paper/Folia/CE/BetterModel/BetterHud releases; update `content/compat.yml` and the CI matrix; re-verify the skills' version-specific claims.
