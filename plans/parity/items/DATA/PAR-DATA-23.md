---
id: PAR-DATA-23
title: "Advancement triggers left from PAR-DATA-19: events published by the 17 owning features"
area: DATA
status: open
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
tests:
  - "paper: one scripted event per criterion awards it (CriteriaListenerTest); no PENDING criterion left (CriteriaMapTest)"
done_criteria:
  - "No criterion of TFC's tree is PENDING in CriteriaMap"
---

# PAR-DATA-23: Advancement triggers left from PAR-DATA-19

> Claimed: Claude session_011osUp9Lr5PbNofhx4XEc3S, 2026-11-25

Follow-up created by PAR-DATA-19. Follow `plans/parity/PROTOCOL.md`.
