---
id: PAR-HEAT-33
title: "Firepit block state follows calendar jumps: the lit look turns off when a time skip burns the fuel out, smoke level in step after a douse"
area: HEAT
status: open
wave: 17
deps: [PAR-HEAT-23]
size: S
aliases: ["follow-up of PAR-HEAT-23 (its in-game run, 2026-11-17: the firepit files are outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/firepit/FirepitDevice.java"
  - "paper/src/main/java/dev/tfp/paper/firepit/FirepitModule.java"
  - "paper/src/main/java/dev/tfp/paper/firepit/PaperFirepitEffects.java"
  - "paper/src/test/java/dev/tfp/paper/firepit/**"
ui: true
in_game_steps:
  - "op Bot; from client chat: `/tfp debug place tfp:firepit[lit=false]` on a solid block, one `tfp:stick_bundle` in, light it with a firestarter (sneak, hold 3.6 s); `/tfp debug device` reads lit=true"
  - "`/tfp time add hours 1` (the bundle burns 600 ticks): without any click the block shows the unlit model within a second; `/tfp debug device` reads lit=false with the ash"
  - "light a fuelled firepit, douse it with a water bucket: the block state reads smoke_level=0 like the record (`/tfp debug device` first line)"
  - "Screenshots at GUI scale 2 and 3 before and after the time add"
plan:
  - "Found in the PAR-HEAT-23 run (docs/verification/PAR-HEAT-23/verdict.md): the burn-out callback (`FirepitEffects.callbackIn`) is queued in server ticks, so a calendar jump (`/tfp time add`, and TFC's sleep skip) that burns the fuel out leaves the lit model up until the next click or chunk load; the click then extinguishes and the empty hand takes the ash first. Also after a douse the record had smokeLevel 0 while the block state kept smoke_level=1."
  - "Fix: on a calendar jump (the calendar's skip event or the device service's catch-up hook) re-run `neighbourChanged` for loaded lit firepits so the block state follows the record, decide async, apply sync; keep smoke_level in the block state equal to the record after every finish."
tests:
  - "MockBukkit: a lit firepit record with one bundle, the calendar advanced by 1000 ticks, the effects receive show(unlit) without an interaction"
  - "MockBukkit: douse leaves the shown state at smoke_level 0"
done_criteria:
  - "A time skip past the fuel turns the lit model off without a click (screenshots)"
  - "Block state smoke_level matches the record after a douse"
---

# PAR-HEAT-33: Firepit block state follows calendar jumps

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
