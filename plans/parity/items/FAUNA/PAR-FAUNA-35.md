---
id: PAR-FAUNA-35
title: "Fishing rod items: the 9 TFC metal rods as CE items (follow-up of PAR-FAUNA-18)"
area: FAUNA
status: open
wave: 45
deps: [PAR-FAUNA-18]
size: S
aliases: ["follow-up of PAR-FAUNA-18 (2026-11-25): datagen is outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java"
  - "src/main/resources/assets/tfc/models/item/metal/fishing_rod"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/fishing/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/fishing/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/material/ItemCoverage.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/fishing_rod/copper; right click a bait onto it in the inventory; cast near fish"
plan:
  - "PAR-FAUNA-18 ships the Paper runtime (rod id prefix tfp:metal/fishing_rod/, tags holds_small_fishing_bait / holds_large_fishing_bait, the tfp:bait PDC, the hook, the goal) but the rod items do not exist: generate CE items for the nine metal rods (copper, bronze, black bronze, bismuth bronze, wrought iron, steel, black steel, blue steel, red steel; TFC metal/fishing_rod/<metal>), material fishing_rod, durability from the metal tier as TFC's rod (tier uses), TFC models (cast and uncast state through the CE fishing rod cast predicate), textures, names. ItemCoverage owner rule metal/fishing_rod/.+ moves to this item."
tests:
  - "datagen: the 9 items exist with TFC's texture, name and tags; coverage report names the generator"
done_criteria:
  - "Every rod id the runtime asks for exists as a CE item; screenshot reviewed"
---

# PAR-FAUNA-35: Fishing rod items

> Claimed: sonnet-5.5 session_011osUp8, 2026-11-25

Follow `plans/parity/PROTOCOL.md`.
