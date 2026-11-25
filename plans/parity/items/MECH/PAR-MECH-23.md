---
id: PAR-MECH-23
title: "Bladed axle in game: craft, place, spin with its blade, debug readout, drop (PAR-MECH-20 in-game check)"
area: MECH
status: open
wave: 54
deps: [PAR-MECH-20]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/render/blockentity/BladedAxleBlockEntityRenderer.java"
files_owned:
  - "docs/verification/PAR-MECH-23/**"
ui: true
in_game_steps:
  - "craft an oak bladed axle (oak axle + steel ingot, shapeless); place it in a line driven by the creative motor at step 8: it turns with the line at 30 rpm, its steel blade turning with the shaft; /tfp debug rotation lists it; break it: it drops itself. Screenshots at GUI scale 2 of the line at rest and turning (two frames), on 1.21.11"
plan:
  - "Created by PAR-MECH-20 (deferred in-game check, PROTOCOL 6.5): the bladed axle's CE blocks, its axle node and its spinning display (TFC's shaft-and-blade model) are done and tested off-server; this item runs the headless client check. A failure is fixed through PAR-MECH-20's files (tools/datagen rotation, paper/rotation) or a follow-up."
  - "Pending in-game check (container cannot run client): 2026-11-25 retry, the headless bot cannot place or click blocks here (no xdotool; the install was denied), RCON /tfp debug and ce commands need a player, vanilla /setblock does not know CE ids. Run on a host with xdotool: place by right-click per in_game_steps."
tests:
  - "none (in-game check)"
done_criteria:
  - "Bladed axles craft, place, join networks and spin with their blade (frames in docs/verification/PAR-MECH-23/)"
---

# PAR-MECH-23: Bladed axle in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
