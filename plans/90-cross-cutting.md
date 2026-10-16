# Cross-cutting tracks

Work that runs alongside every milestone. These tasks are continuous: tick them per milestone, or split them into milestone-specific tasks when they get concrete.

## Art & models

- [ ] **X-ART-01 Image request flow per milestone** · skills: image-requests
  - Do: at the start of each milestone, list the 2D art that TFC doesn't provide (GUI overlays, HUD frames, buttons, icons) and file the `IMAGES.md` requests with exact sizes and slot coordinates. Use placeholders in `content/art/_placeholder/` until approved.
  - Done when (per milestone): no placeholder art remains in the milestone's features.

- [ ] **X-ART-02 Codex workflow upkeep**
  - Do: keep `AGENTS.md`, `.agents/skills/generate-required-images` and `tools/images/postprocess.py` working. Add post-process options when new art types need them (animation strips, GUI sheets).

- [ ] **X-MOD-01 Model generator coverage** · skills: model-authoring
  - Do: add rigs as they become needed (`biped_bird` for M5 poultry, `fish`, `serpentine`), extend the animation library (`run`, `sleep`, `attack`, `swim`), keep the validator in CI for `content/` models.

## HUD & GUIs

- [ ] **X-HUD-01 HUD element catalogue** · skills: betterhud
  - Do: one BetterHud layout per HUD feature, all ids prefixed `tfp_`; placeholders read only snapshots; `tick:` set for slow values. Keep the vanilla-bar hiding sprite list current per MC version.

- [ ] **X-GUI-01 GUI framework** · skills: craftengine-gui-furniture
  - Do: shared Java helpers for textured inventories (title glyph builder via CE codepoints, alignment math, button slots, overlay layers, title refresh throttling) so each device GUI is small.

## Data, i18n, docs

- [ ] **X-DATA-01 TFC update tracking**
  - Do: when the pinned TFC commit is bumped, re-run datagen, diff the data/asset reports, update the ledger and open tasks for changed mechanics.

- [ ] **X-I18N-01 Locales**
  - Do: every player-facing string is translatable; ship all TFC locales including `tr_tr`; add missing keys for plugin-only UI.

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
