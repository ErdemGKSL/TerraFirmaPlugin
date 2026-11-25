---
id: PAR-MECH-08
title: "Power loom"
area: MECH
status: done
wave: 51
deps: [PAR-MECH-01, PAR-PROC-06]
size: S
aliases: ["M6-MECH-03 (power loom)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PowerLoomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/powerloom/**"
  - "core/src/test/java/dev/tfp/core/powerloom/**"
  - "core/src/test/resources/dev/tfp/core/powerloom/**"
  - "paper/src/main/java/dev/tfp/paper/powerloom/**"
  - "paper/src/test/java/dev/tfp/paper/powerloom/**"
  - "core/src/main/java/dev/tfp/core/machine/PowerLoom*"
  - "core/src/test/java/dev/tfp/core/machine/PowerLoom*"
ui: false
in_game_steps:
  - "axle into power loom with yarn; wait; screenshot (moved to PAR-MECH-22)"
plan:
  - "Done: core `powerloom/` PowerLoomState (the loom record: progress, last push, pending step, ticker, the speed of the last look, the recipe numbers, push count; codec), PowerLoomCatchUp (the ticks since the last look stepped with `machine/PowerLoomRules.tick`; a gap over 100 ticks is time TFC does not tick, only a waiting step counts), PowerLoomFacing (the sink face = FACING.getClockWise()); paper `powerloom/` PowerLoomDevice (device kind tfp:power_loom: loom hand rules without the push, the catch-up as the kind's advance, recipe numbers read from the slots so hopper-fed yarn counts, a finished weave makes the cloth), PowerLoomService + PaperPowerLoomAccess (looks every 10 ticks while a rotation node touches the sink face or a step waits: async speed from the rotation actor, region-side store of the speed with the catch-up first, one weave sound per look with pushes), PowerLoomIndex (watched looms per chunk), PowerLoomListener (facing on placement), PowerLoomModule. Tests: core PowerLoomCatchUpTest (10: the golden weave 16/48/80 and finish at 100, 200 random splits equal to stepping, gap rule, codec, facing), paper PowerLoomMockBukkitTest (9), PowerLoomServiceTest (9). The CE block, its item and model, the bottom support rule and the in-game check are PAR-MECH-22."
  - "Lazy pushes from rotation (PowerLoomRules)."
tests:
  - "core: PowerLoomGoldenTest exists"
done_criteria:
  - "A power loom weaves cloth (paper PowerLoomMockBukkitTest: yarn in, rotation set, the cloth in the output after 100 ticks). Deferred check tracked in PAR-MECH-22 (in game, once the tfp:power_loom block exists)"
---

# PAR-MECH-08: Power loom


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
