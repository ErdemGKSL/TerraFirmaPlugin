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
    | grep -vE "Failed to parse packetevents version|api.github.com|Failed to load eula|EULA" | wc -l)
  result=$(grep '^result=' "run/$dir/spike-report.txt" 2>/dev/null || echo "result=NO_REPORT")
  echo "$t exit=$code $result error_lines=$errors"
done
