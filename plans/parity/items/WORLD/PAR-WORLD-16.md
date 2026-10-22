---
id: PAR-WORLD-16
title: "Spawn and world settings: spawn offset, forced TFC gamerules, structures off, dimension policy"
area: WORLD
status: open
wave: 26
deps: []
size: S
aliases: ["M2-GEN-16", "gaps: spawn offset"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/settings"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcSpawn.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcFirstJoinSpawn.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcSpawn*"
  - "docs/worldgen/settings.md"
ui: false
in_game_steps: []
plan:
  - "Explain the 2-9 block first-join offset (vanilla spawn radius on first join) and either set the player exactly or document it."
  - "enableForcedTFCGameRules: apply TFC's gamerules (doInsomnia off, doTraderSpawning off, doPatrolSpawning off, naturalRegeneration handled by us) on world load."
  - "enableNetherPortals=false cancels PortalCreateEvent (TFC onCreateNetherPortal); vanilla structures off documented."
tests:
  - "paper: MockBukkit gamerule application"
  - "itest: spawn case"
done_criteria:
  - "Fresh world: player spawn equals level.dat spawn or documented divergence"
---

# PAR-WORLD-16: Spawn and world settings: spawn offset, forced TFC gamerules, structures off, dimension policy

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
