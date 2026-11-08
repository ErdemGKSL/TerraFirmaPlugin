---
id: PAR-SURV-13
title: "Intoxication in-game verification: beer from a jug, drunk chat"
area: SURV
status: open
wave: 27
deps: [PAR-SURV-08, PAR-POT-07]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
  - "src/main/java/net/dries007/tfc/common/items/JugItem.java"
files_owned:
  - "docs/verification/PAR-SURV-13/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/jug; tfp debug fluid Bot jug tfp:beer 100 (debug from PAR-FLUID-01)"
  - "hold right-click to drink (jug drink from PAR-POT-07); tfp debug intox Bot: intoxication 0.093 (4000 of 43200 ticks left)"
  - "refill and drink four more times (5 drinks = 20000 ticks, level 0.463, slur chance 0.230); tfp debug intox Bot"
  - "Bot sends the chat line 'the quick brown fox jumps over the lazy dog' ten times; screenshot the chat at GUI scale 2"
  - "tfp debug intox Bot clear; send the line again; screenshot (unchanged)"
plan:
  - "In-game check of PAR-SURV-08 (PROTOCOL 6.5), split off because drinking from a jug is PAR-POT-07 (wave 26), which calls IntoxicationService.drink with the drained fluid. TFC gives no nausea or other effect for alcohol: the visible effect is drunk chat above 0.2 (swapped, repeated and replaced letters). Run the steps on Paper 1.21.11 with the headless client (docs/testing/headless.md). A failure is fixed in PAR-SURV-08's files (`core/.../intox/**`, `paper/.../intox/**`) or PAR-POT-07's through a follow-up."
tests:
  - "none (in-game check only)"
done_criteria:
  - "Each jug of beer adds 4000 ticks of intoxication (tfp debug intox), regardless of how full the jug was"
  - "After five jugs some chat words show swapped, repeated or replaced letters; once cleared, chat is unchanged"
---

# PAR-SURV-13: Intoxication in-game verification: beer from a jug, drunk chat

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
