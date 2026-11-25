---
id: PAR-METAL-09
title: "Charcoal forge GUI and insulation/chimney multiblock scan"
area: METAL
status: done
wave: 31
deps: [PAR-METAL-08, PAR-INFRA-03, PAR-INFRA-10]
size: M
aliases: ["M4-FORGE-01 (GUI, insulation)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/CharcoalForgeScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/CharcoalForgeContainer.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/gui/**"
  - "paper/src/test/java/dev/tfp/paper/charcoalforge/gui/**"
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/CharcoalForgeModule.java"
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/CharcoalForgeDevice.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/charcoalforge/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/charcoalforge/**"
ui: true
in_game_steps:
  - "lit forge; right-click; screenshot GUI"
  - "break a side block; tfp debug block"
plan:
  - "Done: ForgeScreens/ForgeScreenLink/ForgeScreenSync/ForgeGuiListener (charcoalforge/gui), CharcoalForgeScreenArt datagen (176 x 186 background re-laid onto a 4-row chest, 52-frame temperature bar), the device's structure scan (extinguish on neighbour change and at least every 1200 ticks) and the module wiring. Tests: paper ForgeScreenLayoutTest (3), ForgeStructureScanMockBukkitTest (4); datagen CharcoalForgeScreenArtTest (3). Pending in-game check (container cannot run client): the in_game_steps stay here; verdict goes to docs/verification/PAR-METAL-09/. files_owned widened narrowly to the module, the device and the datagen art package."
  - "GUI per catalogue `m4-forge-01-charcoal-forge` with temperature bar; the insulation (#tfc:forge_insulation on 4 sides) and open-sky checks decided async through the neighbour runner; invalid structure extinguishes."
tests:
  - "paper: structure rule"
done_criteria:
  - "Aligned GUI screenshot; removing a side block puts the forge out"
---

# PAR-METAL-09: Charcoal forge GUI and insulation/chimney multiblock scan

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
