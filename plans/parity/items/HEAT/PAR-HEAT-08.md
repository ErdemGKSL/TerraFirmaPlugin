---
id: PAR-HEAT-08
title: "Grill on firepit: placement, five slots, cooking, GUI"
area: HEAT
status: done
wave: 15
deps: [PAR-HEAT-07, PAR-INFRA-13]
size: M
aliases: ["M3-HEAT-03 (grill)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/GrillScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/grill/**"
  - "core/src/test/java/dev/tfp/core/grill/**"
  - "core/src/test/resources/dev/tfp/core/grill/**"
  - "paper/src/main/java/dev/tfp/paper/grill/**"
  - "paper/src/test/java/dev/tfp/paper/grill/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Grill.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/GrillTest.java"
  - "content/generated/content/craftengine/tfp/devices/grill.yml"
  - "content/generated/content/craftengine/tfp/configuration/devices/grill.yml"
  - "core/src/main/java/dev/tfp/core/cooking/Grill*"
  - "core/src/test/java/dev/tfp/core/cooking/Grill*"
ui: false
in_game_steps:
  - "Moved to PAR-HEAT-24 (PROTOCOL 6.5): tfp:grill is not a loaded CraftEngine block until that item writes configuration/devices/grill.yml"
plan:
  - "Wrought iron grill used on a firepit converts it to a grill (keep fuel/temperature); GrillRules lazy cooking per slot; charcoal_grilled/wood_grilled traits; items visible on top via entity_renderer (5 item displays) per CE gui-furniture skill; GUI per catalogue."
  - "Split (PROTOCOL 3 + 6.5): writing configuration/devices/grill.yml changes DatagenTest's fixture expectation (non_occluding gains tfp:grill), a file outside this item, as PAR-HEAT-06 found for the firepit; the TFC grill model look needs hitbox model demands (HitboxDemands/DeviceSpecs, after PAR-HEAT-23) and the screen art needs a datagen generator. Those, the in-game steps and the screenshot criterion moved to PAR-HEAT-24. `Grill.loadedYaml` already builds the loaded entry (GrillTest pins it)."
  - "Done: core/grill GrillSim (TFC serverTick + GrillBlockEntity.handleCooking replayed per tick through FirepitSim: addTemp on the five grill slots, then the recipe test on the heated temperature; stops at the first conversion tick so the caller swaps the item; the rest of the span is the firepit's catch-up once nothing can heat; `converted` = TFC copyFrom: burn/air/burn temperature/temperature and lit/smoke kept, ash/dirtiness/lastMaxBurnTicks reset), GrillSlots (SLOT_RENDER_SHAPES/BOUNDS/CENTERS, getSlotForSelection, the 11 px grill top, the 0.6 log height); paper/grill GrillDevice (kind tfp:grill, 9 one-item slots, isItemValid, catch-up with the conversions in place + tfp:wood_grilled via Recipes.modifiers().addTrait; useItemOn order: slot insert by hit, sneak/invalid extract, sneak ash, sneak removal (grill item back, 1 tfp:grill damage + cool sound when lit, convert back), fuel below 0.6, the firepit's douse, the screen; strong fire start lights it), GrillConversion (both ways: fuel slots moved, the old block entity's removal drops the other slots and the ash, copyProperties, place sound, neighbour update), GrillListener (wrought iron grill on a firepit, LOW, used up outside creative, sneak left alone, same-tick off-hand cancelled), PaperGrillEffects (five persistent item displays at TFC's slot centres, scale 0.3, x 90 z 180), GrillScreens/GrillScreenLayout/GrillGuiListener + DeviceGrillLink (4-row chest, fuel column, grill row 3..7, the firepit thermometer, commit + cascade, reload every 10 ticks, access hook), GrillModule (service file line, lang gui.grill.title). Datagen: Grill.loadedYaml. Tests: core GrillSimTest 7 + GrillSlotsTest 3 (GrillGoldenTest unchanged), paper GrillMockBukkitTest 11 (conversion keeps fuel state, refused conversion, beef cooks with the trait and shows on top, sneak extract vs screen, fuel below 0.6, taking a lit grill off and back to a firepit with its fuel, ash and douse, the grill item listener, strong light, screen link, isItemValid) + GrillScreensMockBukkitTest 3, datagen GrillTest +1. Ledger row + divergences. Verification: deferred check tracked in PAR-HEAT-24."
  - "Review fixes: taking the grill off hands out the wrought iron grill, the grill damage and the cool sound only after the conversion back succeeded (GrillEffects.toFirepit runs them from the scheduled task; a refused firepit placement hands out nothing; MockBukkit case added); shift-click sends #tfc:firepit_fuel only to the fuel slot (GrillContainer.moveStack either-or); a top click on the outer 2 px rim lands at TFC's 2 px base plate (GrillSlots.topHitY), so fuel goes in there; GrillSim's dead-grill test also checks burnTemperature 0; the ledger names the grill in the douse and no-automation rows. The in-game capture failure is tfp:firepit's real-state allocation (configuration/devices/firepit.yml), outside this item."
tests:
  - "core: GrillGoldenTest exists"
  - "paper: conversion keeps fuel state"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-24"
---

# PAR-HEAT-08: Grill on firepit: placement, five slots, cooking, GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
