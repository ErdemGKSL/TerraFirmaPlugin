# Project plans

Phased TODO plans for porting TerraFirmaCraft to TerraFirmaPlugin. Scope and priorities come from `PRD.md`, the design from `ARCHITECTURE.md`, and per-feature status/divergences live in `docs/porting/ledger.md`.

| File | Milestone | Goal | Plan status (checkbox counts) |
|---|---|---|---|
| `00-m0-spikes.md` | M0: Spikes | Answer the open technical questions before building on them | Done (11 of 11) |
| `01-m1-skeleton.md` | M1: Skeleton | Buildable plugin, installer, threading primitives, registries, datagen v1, CI | Done (35 of 35) |
| `02-m2-living-world.md` | M2: Living world | Calendar, climate, weather, seasons, full TFC world generation | In progress: 9 done, 10 in progress, 8 to do |
| `03-m3-stone-age.md` | M3: Stone age | Survival basics, heat, knapping, fire, pottery, crops, collapse: play to copper | In progress: 0 done, 30 in progress, 2 to do |
| `04-m4-metal-age.md` | M4: Metal age | Metallurgy chain, anvil/forging, tool & armor tiers, bloomery, blast furnace, steel | In progress: 0 done, 13 in progress, 1 to do |
| `05-m5-husbandry.md` | M5: Husbandry | Animals with BetterModel: livestock, wildlife, aquatic, pets, mounts, fishing | In progress: 0 done, 6 in progress, 8 to do |
| `06-m6-parity.md` | M6: Parity | Mechanical power, glass, decoration families, field guide, polish, release | In progress: 0 done, 11 in progress, 4 to do |
| `90-cross-cutting.md` | All | HUD, GUIs, art, models, i18n, performance, docs: tracks that run alongside every milestone | To do: 12 of 12 |

Counts are the checkbox markers in each file. Some `[x]` items still list open checks in their status lines (for example M1-TEST-01, M2-CAL-02, M2-GEN-05, M2-GEN-06), so the plan files do not fully agree; the checkboxes are not changed here.

## Task format

```
- [ ] **M2-CLIM-03 Title** · deps: M2-CAL-01, M1-REG-02 · skills: paper-folia-threading, tfc-feature-porting
  - TFC: <source paths, relative to src/main/java/net/dries007/tfc/ unless noted>
  - Do: concrete steps
  - Done when: verifiable acceptance criteria (tests, self-tests, renders, ledger row)
```

- **IDs** are stable: `M<n>-<AREA>-<nn>`. Reference them in commits, PRs and the ledger.
- **Status:**
  - `[ ]` todo; `[~]` in progress (add `(@who)` after the title); `[x]` done (add the commit/PR link);
  - `[-]` dropped (add the reason);
  - a task blocked by an upstream issue keeps `[ ]` and gets a `blocked:` note.
- **Ordering:** inside a file, tasks are roughly in dependency order. Tasks with satisfied `deps` can run in parallel.
- **Granularity:** a task should fit in one focused PR. Split it if it grows. Add new tasks with the next free number in their area; never renumber.

## Definition of done (every gameplay task)

From the `plugin-testing` skill:
1. Rules live in `core/` (platform-free) with **golden-value tests** against TFC numbers. TFC's own tests are ported where they exist.
2. Data comes from `tools/datagen` (never hand-copied JSON), and the data-load test passes.
3. Time-based state is **lazy** (calendar timestamps), with a lazy-vs-stepped equivalence test.
4. World reactions use **decide async, apply sync** (`AsyncDecisionPipeline`). Callbacks only snapshot and enqueue.
5. Self-test cases pass on **Paper and Folia** (1.21.11 and 26.3).
6. Visuals: `/tfp debug showcase <feature>` exists, and the visual checklist is done. 2D art through the `image-generation` skill (imagegen MCP), models through `model-authoring`.
7. `docs/porting/ledger.md` row updated. Divergences recorded.

## How agents pick work
1. Read `CLAUDE.md`, then this file, then the milestone file.
2. Take the lowest-numbered unblocked `[ ]` task in the current milestone (M0 first). Mark it `[~]`.
3. Load the skills listed on the task before writing code.
4. When done, tick it, update the ledger, and add follow-up tasks you discovered (with new IDs).
