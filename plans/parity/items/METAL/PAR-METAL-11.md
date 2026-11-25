---
id: PAR-METAL-11
title: "Bellows: push, air boost, crank, animation"
area: METAL
status: done
wave: 32
deps: [PAR-METAL-08]
size: M
aliases: ["M4-FORGE-01 (bellows)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/IBellowsConsumer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/bellows/**"
  - "core/src/test/java/dev/tfp/core/bellows/**"
  - "core/src/test/resources/dev/tfp/core/bellows/**"
  - "paper/src/main/java/dev/tfp/paper/bellows/**"
  - "paper/src/test/java/dev/tfp/paper/bellows/**"
  - "core/src/main/java/dev/tfp/core/charcoalforge/Bellows*"
  - "core/src/test/java/dev/tfp/core/charcoalforge/Bellows*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSpecs.java"
  - "paper/src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule"
ui: true
in_game_steps:
  - "bellows facing a lit forge; right-click 5 times; tfp debug block"
plan:
  - "Right-click pushes (20-tick cadence, BellowsRules) adding air to the consumer in front (forge, bloomery, blast furnace via tuyere); bellows model compresses via entity_renderer morph for a few ticks; crank-driven push from PAR-MECH-05."
  - "Done: core dev.tfp.core.bellows (BellowsData with codec, BellowsMotion: stroke, receiver offsets by facing) on the existing BellowsRules; paper dev.tfp.paper.bellows (BellowsDevice kind tfp:bellows: right click -> BellowsRules.press, air to the consumers at the (1,0,0) and (1,0,-1) offsets, push tick stored, blow sound + poof; BellowsConsumers registry, charcoal forge registered; crank hook for PAR-MECH-05; BellowsModule). Datagen: DeviceSpecs.facing adds the tfp:device behaviour so the bellows block routes to the kind (one line, outside files_owned, listed above). Tests: core BellowsMotionTest 3, BellowsRulesTest; paper BellowsDeviceMockBukkitTest 7. Ledger: feature row + 4 divergences. Pending in-game check (no cheap-model subagent tool and the container cannot run client and server together): in_game_steps not run; the visual animation and TFC blow sound moved to PAR-METAL-26."
tests:
  - "paper: push cadence"
done_criteria:
  - "Pushing bellows raises a forge to heat level 7"
---

# PAR-METAL-11: Bellows: push, air boost, crank, animation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
