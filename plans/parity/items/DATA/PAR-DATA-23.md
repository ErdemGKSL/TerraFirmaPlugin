---
id: PAR-DATA-23
title: "Advancement triggers left from PAR-DATA-19: events published by the 17 owning features"
area: DATA
status: done
wave: 56
deps: [PAR-DATA-19]
size: L
aliases: ["follow-up of PAR-DATA-19"]
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
  - "src/main/java/net/dries007/tfc/util/advancements/TFCAdvancements.java"
  - "src/main/java/net/dries007/tfc/util/events/StartFireEvent.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/advancement/criteria/**"
  - "paper/src/test/java/dev/tfp/paper/advancement/criteria/**"
  - "docs/advancements/criteria-map.md"
  - "paper/src/main/java/dev/tfp/paper/firepit/**"
  - "paper/src/main/java/dev/tfp/paper/grill/**"
  - "paper/src/main/java/dev/tfp/paper/pot/**"
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/**"
  - "paper/src/main/java/dev/tfp/paper/kiln/**"
  - "paper/src/main/java/dev/tfp/paper/powderkeg/**"
  - "paper/src/main/java/dev/tfp/paper/glass/**"
  - "paper/src/main/java/dev/tfp/paper/anvil/**"
  - "paper/src/main/java/dev/tfp/paper/windmill/**"
  - "paper/src/main/java/dev/tfp/paper/lamp/**"
  - "paper/src/main/java/dev/tfp/paper/compost/**"
  - "paper/src/main/java/dev/tfp/paper/livestock/**"
  - "paper/src/main/java/dev/tfp/paper/crop/**"
  - "paper/src/main/java/dev/tfp/paper/tool/**"
  - "paper/src/main/java/dev/tfp/paper/toolarea/**"
ui: false
in_game_steps: []
plan:
  - "PAR-DATA-19 wired hooked_entity (fishing, advanced_fishing), stab_entity (greatest_hunter) and present_day (the_future) from existing Bukkit events. The other 17 criteria need a small Bukkit event published by the owning feature (none exists, and the features' files were outside PAR-DATA-19): basin_pour and table_pour (glass pour from the blowpipe, GlassListener), firepit/grill/pot created (FirepitDevice/GrillConversion/PotConversion when a player lights or converts; TFC FIREPIT_CREATED with the new block), lit for the charcoal forge, pit kiln and powderkeg (TFC StartFireEvent cancelled by the lighting handler, LIT with the block), perfectly_forged (AnvilBlockEntity line 331: the forging bonus when the work lands exactly), chiseled to smooth stone (ChiselItem line 77), rock_anvil (RockConvertableToAnvilBlock line 60), max_windmill (WindmillBlock line 95: the 5th blade slot filled), fed_animal (TFCAnimalProperties line 194: familiarity raised by feeding), full_fertilizer (CropHelpers line 274), full_powderkeg (PowderkegBlockEntity line 188), lava_lamp (LampBlock line 119: lava put into a lamp), rotten_compost_kill (RottenCompostItem line 55)."
  - "Build: one event class per feature in its package (PlayerEvent subclass fired on the player's thread, after the effect), fire it from the feature's code; CriteriaListener subscribes at MONITOR and awards through Advancements.fire; add rows to CriteriaMap (Kind.EVENT) and remove them from CriteriaTable.PENDING, update the count in CriteriaMapTest."
  - "Done: 12 of the 17 triggers wired through AdvancementTriggerEvent (one shared PlayerEvent in paper/advancement/criteria, published by the feature after its effect, awarded by CriteriaListener.onTrigger at MONITOR through Advancements.fire, CriteriaMap.publishable): firepit (firepit factory build), pot (PotInteractListener), grill (GrillListener), forge, pit_kiln and powderkeg (the FireLightables of their modules; forge only when a built forge is lit), full_powderkeg (PowderkegDevice.lightAt, strength 64 with a player igniter), perfectly_forged (AnvilForgeScreens, COMPLETED with the PERFECT bonus), stone_anvil (AnvilListener.convert), windmill (WindmillDevices, fifth blade), familiarity (LivestockModule.onFeed when the feeding raised familiarity), lava_lamp (LampDevice, endless fuel lit by a player). Five stay PENDING and are recorded in PAR-DATA-24: basin_pour and table_pour (PAR-PROC-17 open: no glass pour code), smooth_stone (PAR-TOOL-09 open), full_fertilizer (farm/fertilizer is outside this item's files_owned) and rotten_compost (no rotten compost use on crops exists). Tests: CriteriaMapTest 7 (route per trigger, PENDING count 5), CriteriaListenerTest 8 (3 new: every published criterion awards, own-event and unrouted criteria ignored, publish through the Bukkit bus); affected classes (grill, pot, powderkeg, windmill, anvil, anvil/forge, lamp, livestock, kiln, charcoalforge, firepit) pass, 174 tests in 17 classes. Ledger rows and a divergence added. Pending in-game check (container cannot run client)."
tests:
  - "paper: one scripted event per criterion awards it (CriteriaListenerTest); no PENDING criterion left (CriteriaMapTest)"
done_criteria:
  - "No criterion of TFC's tree is PENDING in CriteriaMap (5 left, owned by PAR-DATA-24: their features are open or outside this item's files)"
---

# PAR-DATA-23: Advancement triggers left from PAR-DATA-19

Follow-up created by PAR-DATA-19. Follow `plans/parity/PROTOCOL.md`.
