---
id: PAR-INFRA-58
title: "Packet-layer boundary: move the PacketEvents-typed heat glow, item bar and block sound handlers under dev.tfp.paper.packet so ArchitectureTest.onlyThePacketLayerTouchesPacketEvents passes"
area: INFRA
status: open
wave: 15
deps: [PAR-HEAT-01, PAR-HEAT-14, PAR-INFRA-33]
size: S
aliases: ["follow-up of PAR-INFRA-50 review (2026-11-17): ./gradlew check fails in :dist:test with 204 violations of onlyThePacketLayerTouchesPacketEvents, all in paper/heat, paper/itembar and paper/blocksound"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/heat/HeatGlow.java"
  - "paper/src/main/java/dev/tfp/paper/heat/HeatGlowHandler.java"
  - "paper/src/main/java/dev/tfp/paper/heat/HeatGlowModule.java"
  - "paper/src/main/java/dev/tfp/paper/heat/HeatTooltipDecoration.java"
  - "paper/src/main/java/dev/tfp/paper/itembar/**"
  - "paper/src/main/java/dev/tfp/paper/blocksound/**"
  - "paper/src/main/java/dev/tfp/paper/packet/heat/**"
  - "paper/src/main/java/dev/tfp/paper/packet/itembar/**"
  - "paper/src/main/java/dev/tfp/paper/packet/blocksound/**"
  - "paper/src/test/java/dev/tfp/paper/heat/HeatGlowTest.java"
  - "paper/src/test/java/dev/tfp/paper/heat/HeatTooltipDecorationTest.java"
  - "paper/src/test/java/dev/tfp/paper/itembar/**"
  - "paper/src/test/java/dev/tfp/paper/blocksound/**"
  - "paper/src/test/java/dev/tfp/paper/packet/heat/**"
  - "paper/src/test/java/dev/tfp/paper/packet/itembar/**"
  - "paper/src/test/java/dev/tfp/paper/packet/blocksound/**"
ui: false
in_game_steps: []
plan:
  - "Current (2026-11-17): `dist` ArchitectureTest.onlyThePacketLayerTouchesPacketEvents (PacketEvents is optional; only dev.tfp.paper.packet.. may link against com.github.retrooper / io.github.retrooper) fails with 204 violations: HeatGlowHandler(+$1), HeatGlow, HeatTooltipDecoration (paper/heat), ItemBarHandler(+$1), ItemBarRender (paper/itembar), BlockSoundPacketHandler (paper/blocksound). PAR-INFRA-50's files have none."
  - "Do: move the classes that take or return PacketEvents types into dev.tfp.paper.packet.heat / .itembar / .blocksound (or split them: the pure decision over Bukkit/core values stays in the feature package, the PacketEvents wrapper code moves under packet). The modules keep registering the handlers through the packet registry; a module class must not reference PacketEvents types itself, so a server without PacketEvents still enables the module without NoClassDefFoundError."
  - "Do not weaken or exempt the rule (PROTOCOL 5.2)."
tests:
  - "dist: ArchitectureTest green; paper: the moved tests (HeatGlowTest, HeatTooltipDecorationTest, ItemBarRenderTest, BlockSoundRewriteTest) unchanged in behaviour"
done_criteria:
  - "./gradlew check passes with ArchitectureTest unchanged; heat glow, heat bar and block sound behaviour unchanged (existing tests green)"
---

# PAR-INFRA-58: Packet-layer boundary for heat glow, item bars and block sounds

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
