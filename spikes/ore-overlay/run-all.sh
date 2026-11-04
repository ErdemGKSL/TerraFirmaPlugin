#!/usr/bin/env bash
# PAR-WORLD-17 (spike S1): the whole census: vanilla proxy for seeds 1-3, then our TFC world for seeds 1-3 with the
# proxy's mean cave faces per block (for the combined estimate), then one small Folia run (thread ownership).
# Usage: run-all.sh <scratch-dir> <port> [chunks]
# Reports land in $OUT (default spikes/ore-overlay/out/).
set -eu
WORK=$1 PORT=$2 CHUNKS=${3:-32}
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${OUT:-$HERE/out}
export OUT
# SKIP_VANILLA=1 reuses the vanilla reports already in $OUT.
for seed in 1 2 3; do
  [ -n "${SKIP_VANILLA:-}" ] && break
  "$HERE/run-census.sh" vanilla "$seed" "$CHUNKS" "$WORK/vanilla-$seed" "$PORT"
done
CAVE=$(python3 -I -c 'import json,sys
v=[json.load(open(p))["census"]["fractions"]["cave_faces_per_block"] for p in sys.argv[1:]]
print(round(sum(v)/len(v), 5))' "$OUT"/ore-census-vanilla-[123]-paper.json)
echo "proxy cave faces per block: $CAVE"
for seed in 1 2 3; do
  rm -rf "$WORK/tfc-$seed"
  "$HERE/run-census.sh" tfc "$seed" "$CHUNKS" "$WORK/tfc-$seed" "$PORT" "$CAVE"
done
rm -rf "$WORK/folia-tfc-1"
"$HERE/run-census.sh" tfc 1 4 "$WORK/folia-tfc-1" "$PORT" "$CAVE" folia
echo "all done"
