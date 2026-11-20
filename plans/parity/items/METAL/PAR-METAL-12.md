---
id: PAR-METAL-12
title: "Crucible block and GUI"
area: METAL
status: done
wave: 33
deps: [PAR-INFRA-03, PAR-INFRA-02, PAR-METAL-01, PAR-HEAT-04, PAR-INFRA-13]
size: M
aliases: ["M4-CRUC-01 (crucible)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/crucible/**"
  - "core/src/test/java/dev/tfp/core/crucible/**"
  - "core/src/test/resources/dev/tfp/core/crucible/**"
  - "paper/src/main/java/dev/tfp/paper/crucible/**"
  - "paper/src/test/java/dev/tfp/paper/crucible/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/crucible/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crucible/**"
  - "content/generated/content/craftengine/tfp/configuration/crucible/**"
ui: true
in_game_steps:
  - "crucible above a lit forge; add copper+tin ores; tfp time add hours 1; open GUI; screenshot"
plan:
  - "Crucible on a heat source (forge/firebox below): 9 input slots melt via CrucibleRules (lazy temperature), fluid alloy up to crucibleCapacity, output slot fills molds; GUI with temperature bar and scroll; molten visual via emissive texture."
  - "Done: tfp:crucible block (16 channel states, shell hitbox, SAVE device), lazy CrucibleSim melt/cool replay, heat consumer for the source below, 6-row chest GUI with TFC art re-laid, debug /tfp debug crucible; molds, output slot and held tooltip are PAR-METAL-23. Tests: core CrucibleSimTest (7), paper CrucibleCatchUpMockBukkitTest (5), datagen CrucibleBlockTest (2) + CrucibleScreenArtTest (3), GeneratedCarrierTest. Verification docs/verification/PAR-METAL-12/ (real heat source not loaded in game: debug heat run stands in)."
tests:
  - "core: CrucibleGoldenTest exists"
  - "paper: melt catch-up"
done_criteria:
  - "Melting bronze in a crucible over a forge"
---

# PAR-METAL-12: Crucible block and GUI


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
