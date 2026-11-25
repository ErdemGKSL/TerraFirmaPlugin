---
id: PAR-MECH-27
title: "Trip hammer in game: the tfp:trip_hammer CE block (TFC model, outline, item, loot), the block coverage owner, the in-game check of PAR-MECH-07"
area: MECH
status: open
wave: 56
deps: [PAR-MECH-20, PAR-METAL-28]
size: M
aliases: ["follow-up of PAR-MECH-07 (2026-11-25): its files_owned have no datagen, and the anvil's remote work is PAR-METAL-28"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TripHammerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates/trip_hammer.json"
  - "src/main/resources/assets/tfc/models/block/trip_hammer.json"
  - "src/main/resources/assets/tfc/models/item/trip_hammer.json"
  - "src/main/resources/data/tfc/loot_table/blocks/trip_hammer.json"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/TripHammerBlockEntityRenderer.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/triphammer/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/triphammer/**"
  - "content/generated/content/craftengine/tfp/configuration/triphammer/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/coverage/BlockCoverage.java"
  - "plans/parity/items/MECH/PAR-MECH-07.md"
  - "docs/verification/PAR-MECH-27/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:trip_hammer, tfp:metal/hammer/bronze, tfp:wood/bladed_axle/oak, tfp:wood/axle/oak, tfp:creative_motor, tfp:metal/anvil/bronze; place the trip hammer looking west (it faces east, toward the anvil: TFC faces the player), the bronze anvil east of it, the bladed axle on top along z with a creative motor (axis z) north of it, so the axle turns south (the facing's clockwise side); right-click the hammer with the bronze hammer; put a hot bronze ingot with a chosen plan on the anvil; click the motor 8 times"
  - "the hammer strikes about every 2 s (30 rpm): the anvil hit sound, the forging cursor moves by the light hit toward the target and stops there, the hammer loses durability; reverse the motor (steps below 0): the trip hammer breaks and drops itself and its hammer; screenshots at GUI scale 2 of the layout and the anvil screen cursor before and after"
plan:
  - "Code done (PAR-MECH-27 commit): block, tests; pending in-game check"
  - "PAR-MECH-07 holds the logic (core/triphammer TripHammerRun over core/machine TripHammerRules; paper/triphammer: device kind tfp:trip_hammer with its hammer slot, the 20-tick strike task reading the bladed axle above, placement facing, chunk index) and is blocked only on this block and PAR-METAL-28's remote work."
  - "Datagen triphammer/TripHammerBlock: tfp:trip_hammer (facing, TFC strength and sounds, tfp:device, loot, TFC's model turned per facing, item, names); TFC's BASE_SHAPES outline from the hitbox allocation where a carrier fits, else a barrier carrier with a ledger divergence; TFC's renderer draws the held hammer swinging (a static display of the held item, or a follow-up)."
  - "Move the trip_hammer owner rule of BlockCoverage to this item (the block becomes generated); when this item is done set PAR-MECH-07 to done in its item file and STATUS (its Done line: in-game check here)."
  - "Pending in-game check (container cannot run client): 2026-11-25 retry, the headless bot cannot place or click blocks here (no xdotool; the install was denied), RCON /tfp debug and ce commands need a player, vanilla /setblock does not know CE ids. Run on a host with xdotool: place by right-click per in_game_steps."
tests:
  - "datagen: the trip hammer block (4 states, loot, behaviour, look per facing)"
done_criteria:
  - "A trip hammer works an anvil in game (frames in docs/verification/PAR-MECH-27/)"
---

# PAR-MECH-27: Trip hammer in game

> Claimed: claude MECH session 011osUpA, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
