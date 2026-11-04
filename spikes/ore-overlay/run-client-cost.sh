#!/usr/bin/env bash
# PAR-WORLD-17 (spike S1): client cost of the overlay field on the headless client (docs/testing/headless.md recipe).
# Usage: run-client-cost.sh <run-dir> <port> <display> <shots-dir>
# Env: PACK_PORT (default 8191), MC (default 1.21.11), SEED (default 1), FIELDS (default "0 2000 8000 20000"),
#   REF="x y z" (an exposed ore from the census' exposed_example): spectator shots of it from the north at 4, 16, 32, 48
#   and 64 blocks, pitch 20 deg, before the field steps (the "before" reference).
# Steps: TFC world seed 1 (priming start + restart), Bot joins, noon, clear weather, creative, flying 20 blocks above
# spawn; for each field size: `oreoverlay field Bot <n>`, settle 10 s, F3 screenshot, `oreoverlay stats`; then the
# 8000 field again while Bot is teleported 5 blocks every 0.5 s (10 blocks/s) for 30 s, then stats.
# Prints the stats lines; the screenshots (F3 visible) go to <shots-dir>.
set -eu
DIR=$1 PORT=$2 DISP=$3 SHOTS=$4
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
CLIENT=$ROOT/spikes/client
MC=${MC:-1.21.11} SEED=${SEED:-1}
export PACK_PORT=${PACK_PORT:-8191}
FIELDS=${FIELDS:-"0 2000 8000 20000"}
TFP=$(ls "$ROOT"/dist/build/libs/TerraFirmaPlugin-*.jar | head -1)
SPIKE=$HERE/build/libs/ore-overlay-spike.jar
GD=$DIR-client
RCON="python3 $CLIENT/rcon.py $((PORT + 10))"
mkdir -p "$DIR" "$SHOTS"
wait_for() { for _ in $(seq 1 "$3"); do grep -q "$2" "$1" 2>/dev/null && return 0; sleep 1; done; return 1; }

printf 'online-mode=false\nspawn-protection=0\nallow-nether=false\nlevel-seed=%s\nview-distance=10\n' "$SEED" > "$DIR/server.properties"
"$HERE/build-pack.sh" "$DIR"
TFP_WORLD=1 "$CLIENT/server.sh" paper "$MC" "$DIR" "$PORT" "$TFP" "$SPIKE" > "$DIR/prime.log" 2>&1 &
wait_for "$DIR/prime.log" 'Done (' 600
sleep 40
"$CLIENT/stop.sh" "$DIR" "$PORT"
rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end"
"$CLIENT/server.sh" paper "$MC" "$DIR" "$PORT" > "$DIR/run.log" 2>&1 &
wait_for "$DIR/run.log" 'Done (' 600
sleep 10
"$CLIENT/session.sh" "$MC" "$PORT" "$GD" "$DISP" || echo "session exit $?"
$RCON "time set noon"; $RCON "weather clear"; $RCON "gamerule doDaylightCycle false"; $RCON "gamemode creative Bot"
$RCON "execute as Bot at @s run tp @s ~ ~20 ~ 0 20"
sleep 15
if [ -n "${REF:-}" ]; then
  set -- $REF
  $RCON "gamemode spectator Bot"
  for dist in 4 16 32 48 64; do
    y=$(python3 -c "print(round($2 + 0.5 + $dist * 0.364, 2))")
    $RCON "tp Bot $(($1)).5 $y $(($3 - dist)).5 facing $1.5 $2.5 $3.5"
    sleep 6
    "$CLIENT/shot.sh" "$SHOTS/vein-$dist.png" "$DISP"
  done
  $RCON "gamemode creative Bot"
  $RCON "execute in minecraft:overworld run tp Bot $1 $(($2 + 40)) $3 0 20"
  sleep 10
fi
DISPLAY=$DISP xdotool key F3 || true
for n in $FIELDS; do
  if [ "$n" -gt 0 ]; then $RCON "oreoverlay field Bot $n"; else $RCON "oreoverlay clear Bot"; fi
  sleep 12
  "$CLIENT/shot.sh" "$SHOTS/field-$n.png" "$DISP"
  $RCON "oreoverlay stats"
done
$RCON "oreoverlay field Bot 8000"
sleep 10
for _ in $(seq 1 60); do $RCON "execute as Bot at @s run tp @s ~5 ~ ~" > /dev/null; sleep 0.5; done
"$CLIENT/shot.sh" "$SHOTS/field-8000-flying.png" "$DISP"
$RCON "oreoverlay stats"
grep -a "TfpSpikeOreOverlay" "$DIR/logs/latest.log" | tail -20
kill "$(cat "$GD/client.pid")" 2>/dev/null || true
"$CLIENT/stop.sh" "$DIR" "$PORT"
