#!/usr/bin/env bash
# Runs the spike harness on the M0 targets and summarises each run.
# Usage: ./run-matrix.sh [autostop-seconds] [extra gradle args...]
# Example: ./run-matrix.sh 60 -PspikeJars=ce-01/build/libs/ce-01.jar
set -u
cd "$(dirname "$0")"
STOP="${1:-60}"; shift || true
TARGETS="${TARGETS:-Paper1_21_11 Paper26_3 Folia1_21_11 Folia26_3}"
mkdir -p logs
for t in $TARGETS; do
  dir=$(echo "$t" | sed -E 's/^(Paper|Folia)/\L\1-/; s/_/./g')
  rm -f "run/$dir/spike-report.txt"
  timeout 1200 ./gradlew -q ":harness:run$t" -Pautostop="$STOP" "$@" < /dev/null > "logs/$t.log" 2>&1
  code=$?
  # Anything that looks like a real problem; known-harmless lines are filtered out.
  errors=$(grep -E "ERROR|Exception|SEVERE|not owned by|Thread failed main thread check" "logs/$t.log" \
    | grep -vE "Failed to parse packetevents version|api.github.com|Failed to load eula|EULA" \
    | grep -vE "Thread-0 failed main thread check: Chunk getEntities call|Asynchronous Chunk getEntities call|Could not pass event ServerPreShutdownEvent to CraftEngine" | wc -l)
  # CraftEngine's furniture manager calls World.getEntities on Thread-0 when the JVM shuts down before startup finished
  # (a signal or the timeout above during startup). Reproduced with no TFP jar: CraftEngine's bug, counted on its own.
  ce_early_stop=$(grep -cE "Asynchronous Chunk getEntities call" "logs/$t.log")
  result=$(grep '^result=' "run/$dir/spike-report.txt" 2>/dev/null || echo "result=NO_REPORT")
  echo "$t exit=$code $result error_lines=$errors ce_early_stop=$ce_early_stop"
done
