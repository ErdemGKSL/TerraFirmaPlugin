---
id: PAR-MECH-19
title: "Spinning shafts in game: axles turn at the right speed and hand on 1.21.11 and 26.x (PAR-MECH-12 in-game check)"
area: MECH
status: open
wave: 53
deps: [PAR-MECH-12]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/render/blockentity/AxleBlockEntityRenderer.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/rotation/spin/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/spin/**"
  - "docs/verification/PAR-MECH-19/**"
ui: true
in_game_steps:
  - "the PAR-MECH-01 setup (creative motor axis x, 5 oak axles, an oak gearbox with the west and south faces hammered, an encased axle south of it, one more oak axle beyond it along z) at speed step 8; record 10 frames of each axle 2 ticks apart (GUI scale 2): the shafts turn at 30 rpm (a quarter turn in 10 ticks), the axles on the two sides of the gearbox in opposite hands; step 0 stops them where they are; a second player (or a relog) joining sees them already turning; break an axle: its display goes"
  - "repeat the frames on a 26.3 client/server"
plan:
  - "Created by PAR-MECH-12 (PROTOCOL 6.5: its in-game check was not run in that session). Run the steps with a cheap-model agent (docs/testing/headless.md, the bot named Bot, CraftEngine self-host so the pack applies, PacketEvents installed), judge the frames in the main session, store crops and verdict.md in docs/verification/PAR-MECH-19/ (note PAR-MECH-12 in the verdict)."
  - "Check what PAR-MECH-12 assumed: the left/right rotation pair interpolates to up to 340 degrees per packet (else lower core RotationAnglesSpin.MAX_SWEEP to one arc, a PAR-MECH-02 file: follow-up), display metadata indices 8/9/11-14/17/23/24 on 26.x, the render item draws with item_model = tfp:render/rotation/wood/axle/<wood> in display context none, a display spawned with the chunk interpolates from its second tick (SpinShafts sends the segment one tick after the spawn)."
  - "Follow-up note: integration/craftengine/rotation/SpinVisualService and SpinSink (PAR-MECH-02) are unused since PAR-MECH-12 (the paper module cannot reach the integration module); remove them in an item that owns those files."
  - "Partial in-game run 2026-11-25 (docs/verification/PAR-MECH-19/verdict.md): the shaft turns (8 frames at different angles); rate, hands, step 0, relog and 26.x not measured. Pending in-game check."
tests:
  - "none new unless a fix needs one (paper: SpinShaftsTest)"
done_criteria:
  - "Axles visibly spin in the right direction at the right speed on 1.21.11 and 26.x (frames and verdict in docs/verification/PAR-MECH-19/)"
---

# PAR-MECH-19: Spinning shafts in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
