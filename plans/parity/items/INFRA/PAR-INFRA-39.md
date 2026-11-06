---
id: PAR-INFRA-39
title: "Ore overlay adapter wiring through the IntegrationProvider SPI instead of Class.forName"
area: INFRA
status: open
wave: 8
deps: [PAR-INFRA-16]
size: S
aliases: ["PAR-INFRA-16 architecture review follow-up: `OreOverlayModule` loads CraftEngineOreSource, the display sender and the packet handler by class name"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/oreoverlay/OreOverlayModule.java"
  - "paper/src/main/java/dev/tfp/paper/integration/Integrations.java"
  - "paper/src/main/java/dev/tfp/paper/integration/IntegrationContext.java"
  - "paper/src/test/java/dev/tfp/paper/integration/IntegrationsTest.java"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/CraftEngineProvider.java"
ui: false
in_game_steps: []
plan:
  - "Current: `OreOverlayModule` creates `dev.tfp.integration.craftengine.oreoverlay.CraftEngineOreSource`, `dev.tfp.paper.packet.display.OverlayDisplaySender` and `OreOverlayPacketHandler` with `Class.forName` (ARCHITECTURE, ore overlay section, 'Wiring'). It links, `ArchitectureTest` holds, but a renamed class only fails at runtime."
  - "Fix: the CraftEngine provider publishes an `OreBlockSource` service (as the other adapter services are published through `Integrations`); the module asks for it and stays off when it is absent. The PacketEvents-side classes keep one documented lookup or move behind the packet layer's own factory. Update the ARCHITECTURE 'Wiring' bullet."
tests:
  - "paper: IntegrationsTest: the ore block source is absent without CraftEngine and present with a fake provider; OreOverlayModule stays off with a log line when it is absent"
done_criteria:
  - "No `Class.forName` for CraftEngineOreSource; ARCHITECTURE updated; ./gradlew check green; self-test `ore.overlay` still green on Paper 1.21.11"
---

# PAR-INFRA-39: Ore overlay adapter wiring through the IntegrationProvider SPI instead of Class.forName

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.

Follow-up of the PAR-INFRA-16 architecture/tests review (non-blocking note 3): the class-name wiring was recorded as a
"follow-up refactor" in ARCHITECTURE without an item.
