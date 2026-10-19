# Dependency versions

Checked against the Modrinth API (`https://api.modrinth.com/v2/project/<slug>/version`) on 2026-10-18. Web search results gave older numbers and were not used.

| Plugin | Modrinth slug | Pinned in repo | Newest release | Newest build (any type) | Decision |
|---|---|---|---|---|---|
| CraftEngine | `craftengine` | 26.9.2 (`spikes/AGENT-GUIDE.md`, `itest`); `content/compat.yml` requires >= 26.9.1 | 26.9.2 | 26.9.2 (beta) | Keep. The pin is the newest build. |
| BetterModel | `bettermodel` | 3.5.1 (`spikes/AGENT-GUIDE.md`, `itest`) | 3.5.1 (2026-10-15) | 3.6.0-SNAPSHOT-531 (beta, 2026-10-18) | Keep 3.5.1. The 3.6.0 build is a snapshot, not a release. |
| BetterHud | `betterhud2` | 2.2.0-SNAPSHOT-449 (`spikes/AGENT-GUIDE.md`, `itest`) | 2.0.0 (2026-05-16) | 2.2.0-SNAPSHOT-449 (alpha, 2026-10-06) | Keep the snapshot. `content/compat.yml` needs 2.2.0 for Java 25 class files that the 1.21.4 remapper can read. The 2.0.0 release does not meet that need. |

Revisit when a newer CraftEngine or BetterModel release or a BetterHud 2.2.0 release appears, and update `content/compat.yml` and the CI matrix in the same change.
