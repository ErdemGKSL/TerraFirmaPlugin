---
id: PAR-INFRA-49
title: "Block coverage gate: every TFC blockstate is a loaded CE block, a family block, an owned id of a named parity item or an NP entry (reports/tfc-block-coverage.json)"
area: INFRA
status: open
wave: 14
deps: [PAR-PROC-19, PAR-INFRA-13]
size: S
aliases: ["parity audit 2026-11-14: PAR-PROC-19's ItemCoverage gates TFCItems only; the 4,521 TFC blockstates have no gate, and 3,140 of them are no loaded CE block today. One family (tfc:wood/workbench, now PAR-DECO-17) had no owner at all and was found only by hand; reports/crafting-recipes.json routes 647 recipes as 'block item with no CE item yet' without naming an owner"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates"
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/coverage/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/coverage/**"
  - "content/generated/content/reports/tfc-block-coverage.json"
ui: false
in_game_steps: []
plan:
  - "Current (audit 2026-11-14): tools/datagen/material/ItemCoverage writes reports/tfc-item-coverage.json and fails the run on an unowned TFCItems registration (1,618 items: 959 generated, 659 owned, 0 unowned). Blocks have no such gate. A scratch parse of content/generated/content/craftengine against /opt/tfc/src/main/resources/assets/tfc/blockstates finds 1,381 loaded CE blocks for 4,521 TFC blockstates; the rest are spread over many open items (crops PAR-FARM-03, plants PAR-WORLD-12, wood/rock/metal families PAR-DECO-01/06/09, devices, soils PAR-WORLD-04, ...) and nothing proves that each one has an owner."
  - "tools/datagen/coverage/BlockCoverage (modelled on ItemCoverage, same report shape): the TFC block ids are every blockstate file under assets/tfc/blockstates of the pinned checkout (the same source id_map.json uses). Each id is classified in this order: generated (a `blocks:` entry of a loaded CE file: configuration/** or devices/**, parsed from the emitted output tree, not from a second hand-kept list), family (an entry of families/*.yml that is generated but not loaded yet: report the file), owned (the first matching rule of an ordered OWNERS list of {regex, parity item id, what}), not_parity (an NP entry of plans/parity/NOT-PARITY.md, e.g. the tfc:fluid/* world fluids and river/spring water of NP-FLUID-01: report the NP id), else unowned. Write reports/tfc-block-coverage.json with totals, counts per class, per owner and per generated file, and the full id -> class/owner map; fail the run when unowned > 0, listing every unowned id. Add the run line to Datagen.java (hub) after the last block generator and after ItemCoverage."
  - "Build OWNERS from the item files of plans/parity/items as they stand (titles, plans and tfc_refs): e.g. crop/, dead_crop/, wild_crop/ -> PAR-FARM-03/PAR-FARM-05; plant/ shapes -> PAR-WORLD-12, fruit tree and berry blocks -> PAR-FARM-06/07/08, banana -> PAR-FARM-09, plant/potted -> PAR-DECO-13; coral/, sea_pickle -> PAR-WORLD-25; wood/workbench -> PAR-DECO-17; device blocks to their device items. The OWNERS rules must not shadow a generated id (generated wins) and each rule must match at least one id (stale-rule check), as ItemCoverage does."
  - "Note in the report comment that a loaded CE block can still be a placeholder (vanilla stand-in visual or missing behaviour): this gate proves ownership, not parity; the owners' done criteria prove parity."
tests:
  - "tools/datagen: BlockCoverageTest on the real checkout (/opt/tfc): unowned == 0; every owner id names an existing file in plans/parity/items (not done items only when the id is still listed as owned); every NP id exists in NOT-PARITY.md; no rule is stale; a synthetic TFC id with no rule makes the run fail and names the id"
  - "tools/datagen: verifyContent passes with the new report"
done_criteria:
  - "reports/tfc-block-coverage.json lists every TFC blockstate with its class and owner, unowned is 0 on the pinned checkout, the run fails on a new unowned TFC block, tests green"
---

# PAR-INFRA-49: Block coverage gate: every TFC blockstate is a loaded CE block, a family block, an owned id of a named parity item or an NP entry (reports/tfc-block-coverage.json)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
