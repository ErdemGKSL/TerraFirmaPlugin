#!/usr/bin/env bash
# PAR-WORLD-17 (spike S1): client cost of the overlay field on the headless client (docs/testing/headless.md recipe).
# Usage: run-client-cost.sh <run-dir> <port> <display> <shots-dir>
# Env: PACK_PORT (default 8191), MC (default 1.21.11), SEED (default 2), FIELDS (default "0 2000 8000 20000"),
#   SETTLE (seconds per field before the measurement, default 15), MEASURE (seconds the frame-time chart averages over,
#   default 12: the chart holds the last 240 frames, about 10-12 s at 20-24 fps).
# Steps:
#   1. TFC world (priming start + restart), Bot joins with the AFK frame cap and vsync off; noon (frozen), clear
#      weather, Bot op and in spectator (hovers, no hotbar); Bot types `/hud turn off` so the BetterHud panel does not
#      cover the first F3 line.
#   2. A stone test area at spawn (the plan's "stone test area"; spikes/client/testroom.sh builds a grass colour room,
#      not stone): a 64 x 64 stepped stone quarry, 8 terraces 2 blocks high and 3 wide down to the spawn height (at
#      least y 64, above sea level), so
#      the chunks around Bot have many exposed stone faces (tops and walls). Bot hovers south of the rim looking north
#      into it.
#   3. F3 and the frame-time chart (F3 + 2) on. For each field size: `oreoverlay field Bot <n>` (quads on real exposed
#      faces, see OreOverlaySpike), wait SETTLE + MEASURE s, screenshot (the chart's min/avg/max are the frame times of
#      the last 240 frames), `oreoverlay stats`, Paper `mspt` and `spark tps`.
#   4. Flight: the 8000 field again, then Bot flies east 5 blocks every 0.5 s (10 blocks/s) for 30 s, 20 blocks above the
#      world surface, looking ahead and down (pitch 35), so the field stays in view; screenshots at 10, 20 and 30 s.
# Prints the stats lines; the screenshots go to <shots-dir>, the RCON replies to <shots-dir>/rcon.txt.
set -eu
DIR=$1 PORT=$2 DISP=$3 SHOTS=$4
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
CLIENT=$ROOT/spikes/client
MC=${MC:-1.21.11} SEED=${SEED:-2} SETTLE=${SETTLE:-15} MEASURE=${MEASURE:-12}
export PACK_PORT=${PACK_PORT:-8191}
FIELDS=${FIELDS:-"0 2000 8000 20000"}
TFP=$(ls "$ROOT"/dist/build/libs/TerraFirmaPlugin-*.jar | head -1)
SPIKE=$HERE/build/libs/ore-overlay-spike.jar
GD=$DIR-client
RCON="python3 $CLIENT/rcon.py $((PORT + 10))"
mkdir -p "$DIR" "$SHOTS"
LOG=$SHOTS/rcon.txt
: > "$LOG"
r() { echo "> $*" >> "$LOG"; $RCON "$@" | tee -a "$LOG"; }
wait_for() { for _ in $(seq 1 "$3"); do grep -q "$2" "$1" 2>/dev/null && return 0; sleep 1; done; return 1; }
keys() { DISPLAY=$DISP xdotool "$@" || true; }

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
# inactivityFpsLimit:minimized - the default (afk) caps the frame rate at 30 after a minute without input, 10 after 10.
MC_OPTIONS="${MC_OPTIONS:-} inactivityFpsLimit:minimized enableVsync:false maxFps:260" \
  "$CLIENT/session.sh" "$MC" "$PORT" "$GD" "$DISP" || echo "session exit $?"
r "time set noon"; r "weather clear"; r "gamerule advance_time false"; r "gamerule advance_weather false"
r "op Bot"
sleep 3
# BetterHud's `hud turn off` only acts on the sender's own HUD: Bot types it in chat.
keys key t; sleep 1; keys type --delay 60 "/hud turn off"; sleep 1; keys key Return
sleep 3
r "gamemode spectator Bot"
sleep 5
# The quarry floor: Bot's spawn height, but at least 64 so the floor stays above sea level (no water flows in).
read -r X Y0 Z < <($RCON "data get entity Bot Pos" | python3 -I -c 'import re,sys,math
v=[float(t) for t in re.findall(r"(-?[0-9.]+)d", sys.stdin.read())]
print(math.floor(v[0]), max(64, math.floor(v[1])), math.floor(v[2]))')
echo "quarry centre $X $Y0 $Z" | tee -a "$LOG"
B=$((Y0 - 4)) T=$((Y0 + 16))
r "tp Bot $X $((T + 14)) $((Z + 40)) 180 35"
sleep 8
for qx in "$((X - 32)) $((X - 1))" "$X $((X + 31))"; do
  for qz in "$((Z - 32)) $((Z - 1))" "$Z $((Z + 31))"; do
    set -- $qx $qz
    r "fill $1 $B $3 $2 $T $4 stone" > /dev/null
    r "fill $1 $((T + 1)) $3 $2 $((T + 12)) $4 air" > /dev/null
  done
done
for k in 0 1 2 3 4 5 6 7; do
  hw=$((30 - 3 * k))
  r "fill $((X - hw)) $((T - 2 * k - 1)) $((Z - hw)) $((X + hw)) $T $((Z + hw)) air" > /dev/null
done
# Hover south of the rim, looking north into the quarry.
r "tp Bot $X.5 $((T + 10)) $((Z + 36)).5 180 30"
sleep 15
keys key F3
sleep 1
keys keydown F3 key 2 keyup F3
for n in $FIELDS; do
  if [ "$n" -gt 0 ]; then r "oreoverlay field Bot $n"; else r "oreoverlay clear Bot"; fi
  sleep $((SETTLE + MEASURE))
  "$CLIENT/shot.sh" "$SHOTS/field-$n.png" "$DISP"
  r "oreoverlay stats"; r "mspt"; r "spark tps"
done
r "oreoverlay field Bot 8000"
sleep $((SETTLE + MEASURE))
r "tp Bot $X.5 $((T + 10)) $((Z + 36)).5 -90 35"
sleep 5
"$CLIENT/shot.sh" "$SHOTS/field-8000-flying-0s.png" "$DISP"
for i in $(seq 1 60); do
  $RCON "execute as Bot at @s positioned over world_surface run tp @s ~5 ~20 ~" > /dev/null
  sleep 0.5
  case $i in 20|40|60) "$CLIENT/shot.sh" "$SHOTS/field-8000-flying-$((i / 2))s.png" "$DISP";; esac
done
r "oreoverlay stats"; r "mspt"; r "spark tps"
grep -a "TfpSpikeOreOverlay" "$DIR/logs/latest.log" | tail -20
kill "$(cat "$GD/client.pid")" 2>/dev/null || true
"$CLIENT/stop.sh" "$DIR" "$PORT"
