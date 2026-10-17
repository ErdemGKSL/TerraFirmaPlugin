# Worldgen chunk cost (M2-GEN-15, partial)

Measured 2026-10-17 on the core stages only (single thread, JDK 25, seed -5793902121713522665).

| Measurement | Value |
|---|---|
| Chunks (spread grid, 4-chunk spacing, 352 sea / 148 land) | 500, after 50 warm-up |
| Mean per chunk | 13.40 ms |
| p95 per chunk | 18.23 ms |
| Min / max | 8.80 / 165.9 ms (max not explained) |
| Stage means: heights / climate+slopes / surface+rock+veins / volcano | 0.24 / 0.51 / 12.46 / 0.19 ms |
| Contiguous grid at (0,0), all sea | mean 12.5-12.9 ms, p95 16.0-17.1 ms |

Scope: block writes, trees, plants, groundcover, deposits, corals, loose rocks, caves
and the Paper terrain fill are NOT included (TfcTerrain.fill needs a Bukkit world).

Budget: PRD.md:68 names the target "Worldgen throughput within 1.5x of vanilla at equal
thread count". No vanilla baseline is measured yet, so no ratio is claimed.

Exposed gems: 0 of 48 gem positions touch air or water (all volcano GEM_ORE in these
chunks; no vein gems). Caveat: placeholder terrain with no caves, trees or decoration,
so this is not a decision about the overlay yet.

Open: the full-pipeline measurement through TfcTerrain.fill, the vanilla baseline, and
the gem overlay decision.
