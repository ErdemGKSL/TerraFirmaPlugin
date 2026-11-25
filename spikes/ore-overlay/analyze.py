# Licensed under the EUPL, Version 1.2.
# PAR-WORLD-19 (spike S1): exhaustive radius totals of ore.census reports, from their per_chunk_raw arrays.
# Usage: python3 -I analyze.py <report.json>...
# For every report: faces per chunk p50 / p95 / max (mean), then for radius 3 (40 m cutoff), 4 (48 m, gated) and the view
# distances 6 / 8 / 10, over every square window of (2r+1)^2 chunks that lies inside the area: p50 / p95 / max and the share
# of windows above 8 000 faces. Measured faces = faces_surface + faces_cave. When the report has an estimate
# (census.estimate.cave_faces_per_block), the combined estimate (surface + max(cave, blocks x cave faces per block)) too.
# Percentiles: nearest rank, as CensusReport.percentile.
import json
import math
import sys

CAP = 8000


def pct(values, q):
    s = sorted(values)
    if not s:
        return 0
    return s[max(0, math.ceil(q * len(s)) - 1)]


def windows(grid, size, r):
    w = 2 * r + 1
    if w > size:
        return None
    # summed-area table
    sat = [[0] * (size + 1) for _ in range(size + 1)]
    for z in range(size):
        for x in range(size):
            sat[z + 1][x + 1] = grid[z * size + x] + sat[z][x + 1] + sat[z + 1][x] - sat[z][x]
    out = []
    for z in range(size - w + 1):
        for x in range(size - w + 1):
            out.append(sat[z + w][x + w] - sat[z][x + w] - sat[z + w][x] + sat[z][x])
    return out


def describe(name, grid, size):
    mean = sum(grid) / len(grid)
    print(f"  {name}: faces per chunk p50 {pct(grid, .5)} p95 {pct(grid, .95)} max {max(grid)} (mean {mean:.1f}),"
          f" total {sum(grid)}")
    for label, r in (("radius 3 (40 m)", 3), ("radius 4 (48 m, gated)", 4), ("VD 6", 6), ("VD 8", 8), ("VD 10", 10)):
        t = windows(grid, size, r)
        if t is None:
            continue
        above = sum(1 for v in t if v > CAP) / len(t)
        print(f"    {label}: {len(t)} windows, p50 {pct(t, .5)} p95 {pct(t, .95)} max {max(t)}, share > 8k {above:.1%}")


def main(paths):
    for path in paths:
        root = json.load(open(path))
        c = root["census"]
        size = c["size"]
        raw = c["per_chunk_raw"]
        surface, cave, blocks = raw["faces_surface"], raw["faces_cave"], raw["blocks"]
        print(f"{path}: {root.get('world_kind')} seed {root.get('seed')}, {size}x{size} chunks at {c['origin_chunk']},"
              f" empty chunks {len(root.get('empty_chunks', []))}")
        print(f"  ore blocks per chunk p50 {pct(blocks, .5)} p95 {pct(blocks, .95)} max {max(blocks)}"
              f" (mean {sum(blocks) / len(blocks):.0f}); cave faces per ore block {sum(cave) / max(1, sum(blocks)):.4f}")
        describe("measured", [s + v for s, v in zip(surface, cave)], size)
        est = c.get("estimate") or {}
        cpb = est.get("cave_faces_per_block")
        if cpb:
            describe(f"estimate (cave faces per block {cpb})",
                     [s + max(v, round(b * cpb)) for s, v, b in zip(surface, cave, blocks)], size)


if __name__ == "__main__":
    main(sys.argv[1:])
