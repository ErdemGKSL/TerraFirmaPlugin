#!/usr/bin/env bash
# M0-CE-02 on all four targets: boot 1 places + saves, boot 2 loads + verifies + destructive tests.
# Results: logs/ce02-<platform>-<mc>.txt
set -u
cd "$(dirname "$0")/.."
for t in ${TARGETS:-"paper:1.21.11" "paper:26.3" "folia:1.21.11" "folia:26.3"}; do
  P=${t%%:*} V=${t##*:} DIR=run/ce02/$P-$V
  rm -rf "$DIR"; mkdir -p "$DIR"
  for boot in 1 2; do
    EXTRA_JVM="-Dtfp.spike.autostop=$([ $boot = 1 ] && echo 150 || echo 200)" \
      timeout 900 ./client/server.sh "$P" "$V" "$DIR" 25630 harness/build/libs/harness.jar ce-02/build/libs/ce-02.jar \
      > "logs/ce02-$P-$V-boot$boot.log" 2>&1 < /dev/null
    echo "$P $V boot$boot exit=$?"
  done
  { echo "== $P $V"; cat "$DIR/ce02-report.txt" 2>/dev/null || echo "NO REPORT";
    grep -hE "Exception|ERROR|not owned|Thread failed|issue" logs/ce02-$P-$V-boot*.log | grep -vE "packetevents|api.github|EULA" | head -20; } > "logs/ce02-$P-$V.txt"
done
