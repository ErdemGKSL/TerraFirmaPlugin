---
id: PAR-HEAT-30
title: "Firepit, grill and pot world rules: fuel thrown on top is gathered, a lit firepit burns entities standing on it (hot floor)"
area: HEAT
status: open
wave: 18
deps: [PAR-HEAT-06, PAR-HEAT-08, PAR-HEAT-09, PAR-HEAT-19, PAR-HUD-12]
size: S
aliases: ["parity audit 2026-11-17: ledger divergence 'TFC gathers fuel items thrown onto the firepit every 20 ticks (gatherAndConsumeItems) ... hurts entities standing on a lit firepit (hotFloor)', revisit 'the item gather and the hot floor as event-driven follow-ups', with no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/util/Helpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/firepit/world/**"
  - "paper/src/test/java/dev/tfp/paper/firepit/world/**"
  - "itest/src/main/java/dev/tfp/itest/cases/FirepitGatherCase.java"
  - "core/src/main/java/dev/tfp/core/firepit/FirepitGather*.java"
  - "core/src/test/java/dev/tfp/core/firepit/FirepitGather*Test.java"
  - "docs/verification/PAR-HEAT-30/**"
ui: true
in_game_steps:
  - "op Bot (run the debug commands from client chat). tfp debug place tfp:firepit[lit=false] on a solid block; ce item give Bot tfp:stick_bundle 3; Bot drops them onto it (q while looking at it); within 1 s tfp debug device: the fuel slots hold the bundles and no item entity is left; screenshot"
  - "drop a non-fuel item (minecraft:dirt) on it: it stays as an item entity"
  - "tfp debug place tfp:firepit[lit=true] with fuel (or light the first one with a firestarter); gamemode survival Bot; Bot walks onto the firepit: health drops 1 (0.5 heart) per hurt cooldown with the hot_floor damage type; screenshot the HUD"
  - "gamemode creative Bot; stand on it: no damage"
plan:
  - "TFC: AbstractFirepitBlockEntity.serverTick calls Helpers.gatherAndConsumeItems every 20 game ticks over the box (x, y, z)-(x+1, y+0.5, z+1), inserting each item that slots SLOT_FUEL_CONSUME..SLOT_FUEL_INPUT accept (fuel only), one item at a time, up to all of them. FirepitBlock.stepOn (inherited by GrillBlock and PotBlock) hurts any LivingEntity with damageSources().hotFloor() for 1 while LIT. PAR-HEAT-06/08/09 have neither (ledger)."
  - "Gathering without a per-block tick: on ItemSpawnEvent / PlayerDropItemEvent / BlockDropItemEvent, schedule an entity task on the item (Scheduler, entity scheduler) that checks every 20 ticks while the item moves and for at most 10 checks after it rests; when its box overlaps a firepit/grill/pot box (DeviceService.inspect on the block it is in and below), enqueue one DeviceService.edit that inserts what the fuel slots accept (TFC order, one at a time) and shrinks the item entity. Pure decision (which counts go where) in core/firepit/FirepitGather*."
  - "Hot floor: use a CraftEngine block behaviour stepOn/entityInside callback if the CE API exposes one (craftengine-java-api skill). Otherwise check players only, from the existing per-player 1 s pass or PlayerMoveEvent (block change only), and record mobs as a divergence (no EntityMoveEvent: its cost applies to every mob). Damage through the vanilla minecraft:hot_floor type, 1 per hit. Game modes (PROTOCOL 4.7): creative and spectator take no damage (vanilla invulnerability), adventure as survival; fire resistance blocks it as vanilla hot_floor does."
  - "Record the remaining parts in the ledger (fire spread is PAR-HEAT-13; hopper automation is PAR-INFRA-60; heating the block above is PAR-METAL-12; bellows air PAR-METAL-11) and update the firepit row's divergence to what is left."
tests:
  - "core: FirepitGather golden vs Helpers.gatherAndConsumeItems (fuel only, slot order, partial insert, count limits)"
  - "paper MockBukkit: a dropped log above an unlit firepit is gathered on the next check; dirt is not; a creative player on a lit firepit takes no damage, a survival one takes 1"
done_criteria:
  - "Fuel dropped on a firepit, grill or pot is gathered into the fuel slots like TFC; other items stay"
  - "A lit firepit/grill/pot hurts survival players standing on it with hot_floor; not in creative/spectator"
  - "./gradlew check passes; screenshots and verdict in docs/verification/PAR-HEAT-30/"
---

# PAR-HEAT-30: Firepit, grill and pot world rules: fuel gathering and hot floor

> Claimed: claude-heat-mech-proc-infra, 2026-11-19

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
