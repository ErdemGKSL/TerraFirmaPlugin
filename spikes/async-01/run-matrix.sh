#!/usr/bin/env bash
# M0-ASYNC-01 stress scenario on all four targets with fresh worlds.
# Usage: async-01/run-matrix.sh [blocks]   -> logs/async01-<platform>-<mc>.txt
set -u
cd "$(dirname "$0")/.."
N=${1:-10000}
for t in "paper 1.21.11" "paper 26.3" "folia 1.21.11" "folia 26.3"; do
  set -- $t; DIR=run/async01/$1-$2
  mkdir -p "$DIR"; rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end" "$DIR/async01-report.txt"
  EXTRA_JVM="-Dtfp.async01.auto=true -Dtfp.async01.blocks=$N -Dtfp.spike.autostop=420" \
    timeout 900 ./client/server.sh "$1" "$2" "$DIR" 25620 harness/build/libs/harness.jar async-01/build/libs/async-01.jar \
    > "logs/async01-$1-$2.log" 2>&1 < /dev/null
  echo "== $1 $2 exit=$?" | tee "logs/async01-$1-$2.txt"
  cat "$DIR/async01-report.txt" >> "logs/async01-$1-$2.txt" 2>/dev/null || echo "NO REPORT" >> "logs/async01-$1-$2.txt"
  grep -E "Exception|ERROR|not owned|Thread failed" "logs/async01-$1-$2.log" | grep -vE "packetevents|api.github|EULA" | head -20 >> "logs/async01-$1-$2.txt"
done
