#!/usr/bin/env bash
# M0-JAR-01 reproduction: build the jar once, run the SAME jar on the four targets, collect results.
# Usage: spikes/jar-01/run.sh   (needs spikes/run/jar01 to be absent or empty: fresh run dirs)
# Output: spikes/run/jar01/<platform>-<mc>/jar01-report.txt, console.out, and spikes/run/jar01/summary.txt
set -euo pipefail
export JAVA_HOME=/opt/jdk/25
ROOT=$(cd "$(dirname "$0")/.." && pwd)     # spikes/
HERE="$ROOT/jar-01"
OUT=${OUT:-$ROOT/run/jar01}
PORT=${PORT:-25800}                         # RCON is PORT+10; other agents use other ports

if [ -e "$OUT" ] && [ -n "$(ls -A "$OUT" 2>/dev/null)" ]; then
  echo "$OUT is not empty: remove it first so every run gets fresh run dirs" >&2
  exit 1
fi
mkdir -p "$OUT"

cd "$ROOT"
./gradlew -q :harness:jar :jar-01:jar
JAR="$HERE/build/libs/jar-01.jar"
HARNESS="$ROOT/harness/build/libs/harness.jar"

# Static evidence: NMS/CraftBukkit references, and the Paper/Adventure method references of the jar.
# Method resolution against each target's classpath: see scripts/api-refs-resolve.py (run by hand, slow).
CLASSES="$OUT/static-classes"; mkdir -p "$CLASSES"; unzip -q -o "$JAR" -d "$CLASSES"
if grep -a -r -l -E 'net/minecraft|craftbukkit' "$CLASSES" > /dev/null; then
  echo "NMS/CraftBukkit reference found in $JAR" | tee "$OUT/static-nms.txt"
else
  echo "no net/minecraft or craftbukkit reference in $JAR" | tee "$OUT/static-nms.txt"
fi
python3 "$HERE/scripts/api-refs-scan.py" "$CLASSES" "$OUT/static-refs.json"

for target in "paper 1.21.11" "paper 26.3" "folia 1.21.11" "folia 26.3"; do
  set -- $target
  platform=$1; mc=$2
  dir="$OUT/$platform-$mc"
  mkdir -p "$dir"
  echo "== $platform $mc"
  EXTRA_JVM="-Dtfp.spike.autostop=40" timeout 600 "$ROOT/client/server.sh" "$platform" "$mc" "$dir" "$PORT" "$JAR" "$HARNESS" \
    > "$dir/console.out" 2>&1 < /dev/null || echo "server exited with $? ($platform $mc)"
  echo "   report: $(grep -c PASS "$dir/jar01-report.txt" 2>/dev/null || echo 0) PASS / $(grep -c FAIL "$dir/jar01-report.txt" 2>/dev/null || echo 0) FAIL"
done

# Error lines that mention our plugin, from each server log.
for name in paper-1.21.11 paper-26.3 folia-1.21.11 folia-26.3; do
  dir="$OUT/$name"
  grep -n -E 'Exception|Error|NoSuchMethod|NoClassDef|IncompatibleClassChange' "$dir/console.out" \
    | grep -E 'jar01|Jar01|dev\.tfp\.spike\.jar01|TfpSpikeJar01' > "$dir/plugin-errors.txt" || true
  echo "$name: plugin-related error lines: $(wc -l < "$dir/plugin-errors.txt")"
done
