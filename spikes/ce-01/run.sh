#!/usr/bin/env bash
# M0-CE-01 phases for one target (e.g. Paper26_3). Results: run/ce01/<target>/ce01-report.txt
set -u
cd "$(dirname "$0")/.."
T="$1"; DIR="run/ce01/$(echo "$T" | sed -E 's/^(Paper|Folia)/\L\1-/; s/_/./g')"
JAR="ce-01/build/libs/ce-01.jar"
boot() {  # $1 = label
  echo "### phase $1" >> "$DIR/ce01-report.txt"
  timeout 900 ./gradlew -q ":harness:run$T" -PrunName=ce01 -Pautostop=110 -PspikeJars="$JAR" < /dev/null > "logs/ce01-$T-$1.log" 2>&1
  echo "$T phase $1 exit=$?"
}
cfg() { sed -i -E "s/^(\s*$1:).*/\1 $2/" "$DIR/plugins/CraftEngine/config.yml"; }
wipe() { rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end"; }
mkdir -p "$DIR" logs; rm -f "$DIR/ce01-report.txt"
printf 'settings:\n  allow-end: false\nworlds:\n  world:\n    generator: TfpSpikeCe01\n' > "$DIR/bukkit.yml"
printf 'allow-nether=false\nonline-mode=false\nspawn-protection=0\n' > "$DIR/server.properties"
wipe; boot A-default-delay-true
cfg delay-configuration-load false; cfg cache-system true; wipe; boot B-gen
boot C-restart
cfg cache-system false; wipe; boot D-gen-nocache
boot E-restart-nocache
cfg cache-system true
