---
id: PAR-FAUNA-07
title: "Nest box: laying, hatching, GUI"
area: FAUNA
status: done
wave: 43
deps: [PAR-FAUNA-06, PAR-INFRA-03, PAR-INFRA-02, PAR-INFRA-13]
size: M
aliases: ["M5-LIV-02 (nest box)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/NestBoxScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/nestbox/**"
  - "core/src/test/java/dev/tfp/core/nestbox/**"
  - "core/src/test/resources/dev/tfp/core/nestbox/**"
  - "paper/src/main/java/dev/tfp/paper/nestbox/**"
  - "paper/src/test/java/dev/tfp/paper/nestbox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/nestbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/nestbox/**"
  - "content/generated/content/craftengine/tfp/configuration/nestbox/**"
ui: false
in_game_steps: []
plan:
  - "Hens sit in a nest box (Seat-like), lay lazily (NestRules 30-tick grid), fertilized eggs hatch after the hatch days; 4-slot GUI."
  - "Done: runtime without the block. core/nestbox (NestBoxRules: SLOTS 4, limit 1, accepts, firstFree, hatch rule, delayToCheck/delayToHatch on the 30-tick grid; NestBoxState: the sitter, checkedThrough so a check is never rolled twice; NestBoxScreenLayout) over the existing NestRules; paper/nestbox: device kind tfp:nest_box (NestBoxDevice: filter, one egg per slot, sneak passes, lazy sitter via NestRules.advance, hatch scan, wake queue, drop on removal), NestSeeker (a ready hen looks for the nearest nest box with a free slot within 16 blocks), PaperNestHens (hold and release), PaperNestEffects, NestBoxScreens/NestBoxLink (4-slot screen), NestFinder, NestBoxModule (service file line, chunk-load callback); datagen nestbox/NestBoxScreenArt (TFC small_inventory.png as tfp:gui/nest_box, configuration/nestbox/nestbox_gui.yml, Datagen run line); lang gui.nestbox.title. Tests: core NestBoxRulesTest 10, paper NestBoxMockBukkitTest 13 + NestFinderTest 2, datagen NestBoxScreenArtTest 3. The CraftEngine block (HitboxDemands) and the ground-laying hand-off (ProductsModule) are outside files_owned: PAR-FAUNA-27. Pending in-game check (container cannot run client), moved to PAR-FAUNA-27 with the block."
tests:
  - "core: NestLayGoldenTest exists"
done_criteria:
  - "A fertilized egg hatches a chick in a nest box (paper NestBoxMockBukkitTest)"
  - "Screenshot check of the block and screen: Deferred check tracked in PAR-FAUNA-27"
---

# PAR-FAUNA-07: Nest box: laying, hatching, GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
