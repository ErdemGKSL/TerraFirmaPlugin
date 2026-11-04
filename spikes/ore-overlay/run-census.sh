#!/usr/bin/env bash
# PAR-WORLD-17 (spike S1): runs the itest case `ore.census` on a fresh server for one world kind and seed and copies the
# report next to the other runs.
# Usage: run-census.sh <tfc|vanilla> <seed> <chunks> <run-dir> <port> [cave-faces-per-block] [paper|folia]
#   tfc:      TFC world (bukkit.yml generator), priming start first (CraftEngine restart-only keys, docs/testing/headless.md)
#   vanilla:  plain vanilla world of the same seed (the exposure proxy until PAR-WORLD-06 ports the cave tunnels)
# Needs dist/build/libs/TerraFirmaPlugin-*.jar and itest/build/libs/itest-*.jar (./gradlew :dist:shadowJar :itest:jar).
# Writes <run-dir>/ore-census-<kind>-<seed>.json and copies it to $OUT (default spikes/ore-overlay/out/).
set -eu
KIND=$1 SEED=$2 CHUNKS=$3 DIR=$4 PORT=$5 CAVE=${6:-} PLATFORM=${7:-paper}
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
CLIENT=$ROOT/spikes/client
OUT=${OUT:-$HERE/out}
MC=${MC:-1.21.11}
TFP=$(ls "$ROOT"/dist/build/libs/TerraFirmaPlugin-*.jar | head -1)
ITEST=$(ls "$ROOT"/itest/build/libs/itest-*.jar | head -1)
mkdir -p "$DIR" "$OUT"
printf 'online-mode=false\nspawn-protection=0\nallow-nether=false\nlevel-seed=%s\n' "$SEED" > "$DIR/server.properties"
wait_for() { # <file> <pattern> <seconds>
  for _ in $(seq 1 "$3"); do grep -q "$2" "$1" 2>/dev/null && return 0; sleep 1; done; return 1
}
if [ "$KIND" = tfc ]; then
  TFP_WORLD=1 "$CLIENT/server.sh" "$PLATFORM" "$MC" "$DIR" "$PORT" "$TFP" "$ITEST" > "$DIR/prime.log" 2>&1 &
  wait_for "$DIR/prime.log" 'Done (' 600 || { echo "priming start did not finish"; exit 1; }
  wait_for "$DIR/prime.log" 'Generated resource pack\|resource pack' 180 || true
  sleep 20
  "$CLIENT/stop.sh" "$DIR" "$PORT"
  rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end"
fi
JVM="-Dtfp.selftest=true -Dtfp.oreCensus.chunks=$CHUNKS -Dpaper.disableChannelLimit=true"
[ -n "$CAVE" ] && JVM="$JVM -Dtfp.oreCensus.caveFacesPerBlock=$CAVE"
EXTRA_JVM="$JVM" "$CLIENT/server.sh" "$PLATFORM" "$MC" "$DIR" "$PORT" "$TFP" "$ITEST" > "$DIR/census.log" 2>&1 || true
grep 'ore.census' "$DIR/census.log" | tail -2
cp "$DIR/ore-census-$KIND-$SEED.json" "$OUT/ore-census-$KIND-$SEED-$PLATFORM.json"
echo "report: $OUT/ore-census-$KIND-$SEED-$PLATFORM.json"
