---
id: PAR-DATA-24
title: "Advancement triggers left from PAR-DATA-23: glass pour, chisel, fertilizer and rotten compost"
area: DATA
status: open
wave: 57
deps: [PAR-DATA-23, PAR-PROC-17, PAR-TOOL-09]
size: S
aliases: ["follow-up of PAR-DATA-23"]
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
  - "src/main/java/net/dries007/tfc/common/items/GlassBlowpipeItem.java"
  - "src/main/java/net/dries007/tfc/common/items/ChiselItem.java"
  - "src/main/java/net/dries007/tfc/common/items/RottenCompostItem.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/advancement/criteria/**"
  - "paper/src/test/java/dev/tfp/paper/advancement/criteria/**"
  - "docs/advancements/criteria-map.md"
  - "paper/src/main/java/dev/tfp/paper/glassblock/**"
  - "paper/src/main/java/dev/tfp/paper/chisel/**"
  - "paper/src/main/java/dev/tfp/paper/farm/fertilizer/**"
ui: false
in_game_steps: []
plan:
  - "PAR-DATA-23 wired 12 of the 17 triggers through AdvancementTriggerEvent (published by the owning feature, awarded by CriteriaListener). Five stay PENDING in CriteriaTable: story/basin_pour and story/table_pour (GlassBlowpipeItem lines 96 and 128: pouring a blowpipe onto a glass basin or a pouring table; the feature is PAR-PROC-17, still open, no glass pour code exists), story/smooth_stone (ChiselItem line 77: chiseling to a block of #c:stones/smooth; PAR-TOOL-09 open, ToolListener only handles chisel durability), world/full_fertilizer (CropHelpers line 274: a fertilizer use leaves the farmland maxed out; the code is farm/fertilizer/FertilizerUse and FertilizerListener, outside PAR-DATA-23's files_owned) and world/rotten_compost (RottenCompostItem line 55: rotten compost used on a crop kills it; no item behaviour exists, tfp:rotten_compost is only a composter output)."
  - "Build: publish AdvancementTriggerEvent(player, CriteriaMap.<X>) from each feature after its effect (the constants and the EVENT route are added here: add the five CriterionRefs to CriteriaMap, remove the rows from CriteriaTable.PENDING, update the PENDING count in CriteriaMapTest to 0 and the replay in tfcTreeIsObtainableThroughScriptedEvents). For rotten_compost add the use of tfp:rotten_compost on a crop (ICropBlock.die with the mature flag, fertilizer sound) in farm/fertilizer, as RottenCompostItem.useOn."
tests:
  - "paper: one scripted event per criterion awards it (CriteriaListenerTest); no PENDING criterion left (CriteriaMapTest)"
done_criteria:
  - "No criterion of TFC's tree is PENDING in CriteriaMap"
---

# PAR-DATA-24: Advancement triggers left from PAR-DATA-23

Follow-up created by PAR-DATA-23. Follow `plans/parity/PROTOCOL.md`.
