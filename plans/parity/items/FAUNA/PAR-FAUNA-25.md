---
id: PAR-FAUNA-25
title: "Mob bucket items: the 20 TFC fish buckets as CE items (follow-up of PAR-FAUNA-11)"
area: FAUNA
status: open
wave: 43
deps: [PAR-FAUNA-11]
size: S
aliases: ["follow-up of PAR-FAUNA-11 (2026-11-25): datagen is outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/resources/assets/tfc/models/item/bucket"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/aquatic/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/aquatic/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/material/ItemCoverage.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:bucket/cod; screenshot the item in the hotbar"
  - "tfp debug animal spawn bluegill adult in a pond, right click it with a water bucket: the tfp:bucket/bluegill item appears; right click water with it: the fish is released"
plan:
  - "PAR-FAUNA-11 ships the Paper runtime (pickup and release by id tfp:bucket/<species>) but the items do not exist: CE items for COD_BUCKET, PUFFERFISH_BUCKET, JELLYFISH_BUCKET, TROPICAL_FISH_BUCKET and the 16 freshwater fish buckets (TFCItems lines 422-428), stack 1, TFC models/textures/names, on the vanilla bucket material. ItemCoverage.MOB_BUCKETS currently assigns them to PAR-FAUNA-11: move the owner to this item and generate them."
tests:
  - "datagen: the 20 items exist with TFC's texture and name; coverage report names the generator"
done_criteria:
  - "Every mob bucket id the runtime asks for exists as a CE item; screenshot reviewed"
---

# PAR-FAUNA-25: Mob bucket items

Follow `plans/parity/PROTOCOL.md`.
