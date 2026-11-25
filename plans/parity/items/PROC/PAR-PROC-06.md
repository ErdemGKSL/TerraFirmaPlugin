---
id: PAR-PROC-06
title: "Loom: weaving cloth, burlap, wool, silk"
area: PROC
status: done
wave: 41
deps: [PAR-INFRA-02, PAR-DECO-01]
size: S
aliases: ["M3-PROC-02 (loom)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/loom"
files_owned:
  - "core/src/main/java/dev/tfp/core/loom/**"
  - "core/src/test/java/dev/tfp/core/loom/**"
  - "core/src/test/resources/dev/tfp/core/loom/**"
  - "paper/src/main/java/dev/tfp/paper/loom/**"
  - "paper/src/test/java/dev/tfp/paper/loom/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/loom/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/loom/**"
  - "core/src/main/java/dev/tfp/core/craft/Loom*"
ui: false
plan:
  - "Done: core LoomRules/LoomState (push rule, 20-tick step, lazy settle), paper LoomDevice (2 slots; TFC onRightClick order), PaperLoomEffects (weave sound, wake-up 20 ticks after a push, placement facing), LoomListener, LoomModule; datagen LoomBlock (tfp:wood/loom/<wood>, 20 woods x 4 states, hitbox-only carrier cactus[age=10] + item-display look), 4 loom shape rows. Tests: core LoomRulesTest (5), paper LoomMockBukkitTest (5), datagen LoomBlockTest (3). Pending in-game check and the in-progress cloth texture: PAR-PROC-48."
  - "Per-wood loom block; add inputs, right-click pushes (LoomRules cooldown); in-progress texture; output taken by hand."
tests:
  - "paper: push cooldown"
done_criteria:
  - "Weaving the recipe's yarn gives the cloth (paper LoomMockBukkitTest). Deferred check tracked in PAR-PROC-48 (in game, with the in-progress cloth visual)"
---

# PAR-PROC-06: Loom: weaving cloth, burlap, wool, silk


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
