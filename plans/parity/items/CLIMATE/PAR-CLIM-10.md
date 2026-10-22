---
id: PAR-CLIM-10
title: "Plugin enables on Paper 26.x: SnowService builds the icicle tip without casting to PointedDripstone"
area: CLIMATE
status: open
wave: 4
deps: []
size: S
aliases: ["follow-up of PAR-INFRA-13 (in-game run at b00239a: the plugin does not enable on Paper 26.3)"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/weather/SnowService.java"
  - "paper/src/test/java/dev/tfp/paper/weather/SnowServiceBlockDataTest.java"
ui: false
in_game_steps:
  - "Headless Paper 26.3 (and 26.1.2) with CraftEngine, BetterModel and BetterHud: the plugin enables, the TFC world generator is set, no ClassCastException in the log"
plan:
  - "Current: the PAR-INFRA-13 in-game run on Paper 26.3 found that TerraFirmaPlugin does not enable: `SnowService.java:135-136` casts the BlockData of `Material.POINTED_DRIPSTONE.createBlockData(...)` (a `CraftPointedDripstone` on 26.3) to `org.bukkit.block.data.type.PointedDripstone` and throws a ClassCastException in the field initialiser, so the service, the rest of enable and the world generator never run. Nothing of the plugin can be checked on 26.x until this is fixed (it blocks PAR-INFRA-13/PAR-INFRA-23's 26.x verification)."
  - "Fix: build the icicle tip from the state string (`Bukkit.createBlockData(\"minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]\")`) or check the type with `instanceof` and fall back to the string, never a blind cast; look for the same pattern in the other BlockData field initialisers of SnowService (snow layers) and fix them the same way. Keep the 1.21.x behaviour unchanged."
  - "Run the enable smoke on both families (delegate to a Haiku agent: `spikes/run-matrix.sh` with 1.21.11 and 26.3, report the enable line and any exception)."
tests:
  - "paper: SnowServiceBlockDataTest (MockBukkit) builds the icicle tip and snow layer data without a cast to the interface"
  - "Server matrix: enable on 1.21.11 and 26.3 without exception"
done_criteria:
  - "The plugin enables on Paper 26.3 and 26.1.2 and sets the world generator; 1.21.x unchanged"
---

# PAR-CLIM-10: Plugin enables on Paper 26.x: SnowService builds the icicle tip without casting to PointedDripstone

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
