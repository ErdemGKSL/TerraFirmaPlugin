---
id: PAR-TOOL-05
title: "Javelins: throwing, damage, pickup, javelin AI for skeletons"
area: TOOL
status: open
wave: 23
deps: [PAR-TOOL-01]
size: M
aliases: ["M5-HUNT-01 (javelin)", "M3-TOOL-01 (javelin)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/JavelinItem.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/ThrownJavelin.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/JavelinAttackGoal.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/javelin/**"
  - "core/src/test/java/dev/tfp/core/javelin/**"
  - "core/src/test/resources/dev/tfp/core/javelin/**"
  - "paper/src/main/java/dev/tfp/paper/javelin/**"
  - "paper/src/test/java/dev/tfp/paper/javelin/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/javelin/copper"
  - "throw at a pig; screenshot"
plan:
  - "Javelin item on a trident carrier (use/charge/throw handled by vanilla trident physics), the thrown entity's model swapped to the javelin item display; damage = HuntJavelin.thrownDamage; durability per throw; pickup returns the same stack (PDC kept)."
tests:
  - "paper: MockBukkit damage computation"
  - "core: HuntJavelinTest exists"
done_criteria:
  - "Throwing a copper javelin at a pig deals TFC damage and the javelin can be picked up"
---

# PAR-TOOL-05: Javelins: throwing, damage, pickup, javelin AI for skeletons

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
