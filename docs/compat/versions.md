# Dependency versions

Checked 2026-11-19 (PAR-REL-03) against the Modrinth API (`https://api.modrinth.com/v2/project/<slug>/version`) and the PaperMC Fill API (`https://fill.papermc.io/v3/projects/<paper|folia>/versions/<mc>/builds`). Web search results gave older numbers and were not used.

## Client-visual plugins and the packet layer

| Plugin | Modrinth slug | Pinned in repo | Newest release | Newest build (any type) | Decision |
|---|---|---|---|---|---|
| CraftEngine | `craftengine` | 26.9.2 (`gradle/libs.versions.toml`, `content/compat.yml` min 26.9.2) | 26.9.2 (beta, 2026-09-28) | 26.9.2 | Keep. The pin is the newest build. |
| BetterModel | `bettermodel` | 3.5.1 | 3.7.0 (release, 2026-11-19, published today) | 3.7.0-SNAPSHOT-547 | Keep 3.5.1 for now. 3.7.0 skips 3.6.x, so an API change is possible; it is only worth adopting after the animal-model itest (PAR-REL-06) runs against it. Follow-up: bump `bettermodel` in `libs.versions.toml` and `compat.yml` together with a matrix run. |
| BetterHud | `betterhud2` | 2.2.0-SNAPSHOT-449 | 2.0.0 (2026-05-16) | 2.2.0-SNAPSHOT-449 (alpha, 2026-10-06) | Keep the snapshot. The 2.0.0 release does not have the Java 25 class files `compat.yml` relies on. |
| PacketEvents | `packetevents` | 2.14.0+spigot | 2.14.0 (2026-09-23) | 2.14.0 | Keep. |

## Server

| Platform | Supported (`content/compat.yml`) | Newest stable build | Decision |
|---|---|---|---|
| Paper 1.21.11 | 1.21.11 | build 132 | Keep. 1.21.11 is the last 1.21 release. |
| Folia 1.21.11 | 1.21.11 | build 14 | Keep. |
| Paper 26.3 | 26.3 | build 256 (the CI matrix does not pin a build) | Keep. |
| Folia 26.3 | 26.3 | build 26 | Keep. |

Java 25 everywhere (`compat.yml`). The CI matrix (`.github/workflows/ci.yml`) is {Paper, Folia} x {1.21.11, 26.3}, which equals the supported set; no change is needed.

Revisit when BetterModel 3.7.0 has been matrix-tested, a CraftEngine release newer than 26.9.2 or a BetterHud 2.2.0 release appears, or a Minecraft version after 26.3 ships. Update `content/compat.yml` and the CI matrix in the same change.
