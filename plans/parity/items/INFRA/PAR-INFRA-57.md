---
id: PAR-INFRA-57
title: "Item-list tooltip summary: ContentsPdc writes tfp:view so sealed vessel, powderkeg, crucible and barrel items show their contents lines; deferred in-game barrel contents check of PAR-INFRA-50"
area: INFRA
status: open
wave: 16
deps: [PAR-INFRA-04, PAR-PROC-41]
size: S
aliases: ["follow-up of PAR-INFRA-50 (2026-11-17): the contents lines read a summary inside tfp:contents because the Netty thread cannot decode the serialized slots (TooltipData has no byte arrays)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/itemdata/ContentsPdc.java"
  - "paper/src/test/java/dev/tfp/paper/itemdata/ContentsPdcViewTest.java"
ui: true
in_game_steps:
  - "Survival Bot: fill a barrel (PAR-PROC-08) with 3 tfp:food/barley_grain and 1000 mB water, seal it, break it; hover the dropped barrel item at GUI scale 2: dark green 'Contents:' then '3 x Barley Grain' and '1000 mB of Water' right under the name; screenshot (moved here from PAR-INFRA-50 as a deferred check)"
plan:
  - "PAR-INFRA-50 renders the contents lines from `tfp:contents.tfp:view`, a string list with one entry per non-empty slot in slot order: `<count>\\t<item id>\\t<identity>` (FeatureData.viewEntry / FeatureData.parse in paper/tooltip/feature). The identity must be equal exactly for stacks TFC's barrel line merges (ItemComponent equality: the item and its data): the item id plus a digest of the stack's other data (for example a hash of `serializeAsBytes` of the stack with count 1)."
  - "Do: in ContentsPdc.write, add the `tfp:view` list next to `tfp:items` (same version; readers ignore unknown keys). The item id is the CraftEngine id when the stack is a CE item, else the vanilla key: ContentsPdc has no CE access, so take an id function from the caller or a static hook set by the module that owns CE ids; decide and note it here."
  - "Then reword the ledger divergence 'TFC reads an item list's stacks directly in appendHoverText' (the summary stays, the missing-summary case goes)."
tests:
  - "paper: ContentsPdcViewTest (written view = the slots in order with counts; equal identity for equal stacks, different for a food with another creation date or trait; empty slots skipped; FeatureData.contents reads it back through TooltipData)"
done_criteria:
  - "Every item list written by ContentsPdc carries the summary; the barrel item of PAR-PROC-41 shows TFC's contents lines in game (screenshot passes review); tests green"
---

# PAR-INFRA-57: Item-list tooltip summary for the contents lines

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
