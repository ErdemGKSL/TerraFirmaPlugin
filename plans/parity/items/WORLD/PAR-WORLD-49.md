---
id: PAR-WORLD-49
title: "Surface builder over every exposed floor: cave floors, arch and overhang floors get TFC's surface states (whole-column scan)"
area: WORLD
status: open
wave: 23
deps: [PAR-WORLD-24]
size: M
aliases: ["follow-up of PAR-WORLD-24: the surface covers only the first block below the opened top"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/surface/builder/NormalSurfaceBuilder.java"
  - "src/main/java/net/dries007/tfc/world/surface/SurfaceManager.java"
  - "src/main/java/net/dries007/tfc/world/surface/SurfaceBuilderContext.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/surface/ColumnSurface.java"
  - "core/src/test/java/dev/tfp/core/worldgen/surface/ColumnSurfaceGaps*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcTerrainSurfaceGaps*"
ui: false
in_game_steps: []
plan:
  - "TFC's NormalSurfaceBuilder scans the column from the world surface heightmap down to the bottom: at every air block it resets the surface depth (below sea level it switches to the cave top/mid/under states, above it to the last surface state), so cave floors, arch floors and overhang floors get surface blocks. PAR-WORLD-24 builds ColumnSurface on the first block below the density-opened top only; the cave and overhang floors stay bare rock (ledger divergence of the terrain density row)."
  - "Extend ColumnSurface.build to take the column's open predicate (terrain density and density caves, not the carvers, which run after the surface) and follow NormalSurfaceBuilder's loop and draws exactly; TfcTerrain passes Ground's densityOpened || noiseCarve != KEEP."
tests:
  - "core: golden columns with air gaps against a TFC oracle of NormalSurfaceBuilder (scratch harness outside the repo)"
done_criteria:
  - "Cave and overhang floors get TFC's surface states; golden columns green; ledger divergence removed"
---

# PAR-WORLD-49: Surface builder over every exposed floor: cave floors, arch and overhang floors get TFC's surface states (whole-column scan)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
