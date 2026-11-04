#!/usr/bin/env bash
# PAR-WORLD-17 (spike S1): the "before" reference of an exposed vein on a run dir that run-client-cost.sh prepared
# (TFC world, pack built). Starts the server, joins Bot, and takes spectator shots of the ore at x y z from the north at
# 64, 48, 32, 16 and 4 blocks (farthest first, so the chunks are loaded), pitch 20 deg, noon, waiting WAIT s per shot.
# Usage: run-vein-shots.sh <run-dir> <port> <display> <shots-dir> <x> <y> <z>
# Env: DISTS (default "64 48 32 16 4"), RISE (height gain per block of distance, default 0.364 = pitch 20 deg; 0 looks
#   level, e.g. for an ore on a lake bed seen from under water), TAG (file name suffix).
set -eu
DIR=$1 PORT=$2 DISP=$3 SHOTS=$4 X=$5 Y=$6 Z=$7
HERE=$(cd "$(dirname "$0")" && pwd)
CLIENT=$(cd "$HERE/../client" && pwd)
export PACK_PORT=${PACK_PORT:-8191}
MC=${MC:-1.21.11} WAIT=${WAIT:-20} DISTS=${DISTS:-"64 48 32 16 4"} RISE=${RISE:-0.364} TAG=${TAG:-}
GD=$DIR-client
RCON="python3 $CLIENT/rcon.py $((PORT + 10))"
mkdir -p "$SHOTS"
"$CLIENT/server.sh" paper "$MC" "$DIR" "$PORT" > "$DIR/shots.log" 2>&1 &
for _ in $(seq 1 600); do grep -q 'Done (' "$DIR/shots.log" && break; sleep 1; done
sleep 10
"$CLIENT/session.sh" "$MC" "$PORT" "$GD" "$DISP" || echo "session exit $?"
$RCON "time set noon"; $RCON "weather clear"; $RCON "gamemode spectator Bot"
$RCON "tp Bot $X.5 $((Y + 3)) $((Z - 4)).5"
sleep "$WAIT"
for n in north south west east up; do
  case $n in north) c="$X $Y $((Z - 1))";; south) c="$X $Y $((Z + 1))";; west) c="$((X - 1)) $Y $Z";;
    east) c="$((X + 1)) $Y $Z";; up) c="$X $((Y + 1)) $Z";; esac
  echo "neighbour $n: $($RCON "execute if block $c #minecraft:air")$($RCON "execute if block $c minecraft:water")"
done
for dist in $DISTS; do
  y=$(python3 -c "print(round($Y + 0.5 + $dist * $RISE, 2))")
  $RCON "tp Bot $X.5 $y $((Z - dist)).5 facing $X.5 $Y.5 $Z.5"
  sleep "$WAIT"
  $RCON "time set noon" > /dev/null
  "$CLIENT/shot.sh" "$SHOTS/vein$TAG-$dist.png" "$DISP"
done
kill "$(cat "$GD/client.pid")" 2>/dev/null || true
"$CLIENT/stop.sh" "$DIR" "$PORT"
