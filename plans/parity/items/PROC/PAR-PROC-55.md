---
id: PAR-PROC-55
title: "Powder bowl: the soda ash step is not written back to the glass blowpipe"
area: PROC
status: open
wave: 32
deps: [PAR-PROC-52, PAR-POT-09]
size: M
aliases: ["found by PAR-PROC-52 (2026-11-25) in game"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BowlBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/bowl/**"
  - "paper/src/test/java/dev/tfp/paper/bowl/**"
  - "paper/src/main/java/dev/tfp/paper/glass/**"
  - "paper/src/test/java/dev/tfp/paper/glass/**"
  - "docs/verification/PAR-PROC-55/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/bowl, tfp:powder/soda_ash 4, tfp:blowpipe, tfp:silica_glass_batch; load the batch onto the blowpipe; place the bowl, put the powder in; heat the blowpipe (tfp debug heat Bot 1200); right-click the bowl with the blowpipe: one powder is used, the cooldown starts and the blowpipe tooltip lists Soda Ash"
plan:
  - "In game (PAR-PROC-52): with the glass listener out of the way (it now yields to device blocks) the bowl takes the click but the blowpipe gets no soda ash step. `BowlDevices.Handler.useWithItem` passes `ctx.item()` (a clone made by `DeviceService.use`) to `BowlGlass.apply`, which writes the PDC into the clone, and `Action.ApplyGlass` never sets the changed stack back to the hand (`setHand`), so the step is lost (and the bowl still loses its powder). Write the modified stack back to `ctx.hand()` like the Salt and Insert actions do; add a MockBukkit test where the held blowpipe afterwards has the step; then the in-game check."
tests:
  - "paper bowl: a blowpipe right-clicked on a bowl with soda ash ends with the soda ash step and one powder less"
done_criteria:
  - "A glass blowpipe gets the powder step from a powder bowl in game (docs/verification/PAR-PROC-55/)"
---

# PAR-PROC-55

> Claimed: PROC worker session_011osUpAUVyyokAxf5Kiw72D, 2026-11-25: Powder bowl: the soda ash step is not written back to the glass blowpipe

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
