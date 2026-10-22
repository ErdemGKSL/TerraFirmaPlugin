#!/usr/bin/env bash
# Joins a running server with the headless client and waits until the player is in the world.
# Usage: session.sh <mc-version> <port> <game-dir> [display]
# Afterwards: shot.sh <file> for screenshots, rcon.py <port+10> <cmd> for server commands,
# xdotool (DISPLAY) for keys. Kill with: kill $(cat <game-dir>/client.pid)
# After the join it waits (PACK_WAIT seconds, default 120; 0 skips) until the client log shows the server resource pack
# applied. Exit codes: 0 joined (and pack applied), 1 no join, 2 pack failed or not applied in time.
set -eu
VER=$1 PORT=$2 GD=$3 DISP=${4:-:99}
HERE=$(cd "$(dirname "$0")" && pwd)
if ! xdpyinfo -display "$DISP" > /dev/null 2>&1; then
  Xvfb "$DISP" -screen 0 1280x720x24 > /dev/null 2>&1 &
  sleep 2
fi
mkdir -p "$GD"; rm -f "$GD/logs/latest.log"
# MC_OPTIONS="lang:tr_tr fov:90" sets options.txt keys (launch rewrites the file, so a hand-made options.txt is lost).
OPTS=()
for o in ${MC_OPTIONS:-}; do OPTS+=(--option "$o"); done
python3 "$HERE/mcclient.py" launch "$VER" --server "localhost:$PORT" --game-dir "$GD" --display "$DISP" ${OPTS[@]+"${OPTS[@]}"} | tail -1 > "$GD/client.pid"
# Wait for the join (server side) - the resource pack is auto-accepted via servers.dat.
for i in $(seq 1 120); do
  out=$(python3 "$HERE/rcon.py" $((PORT+10)) list 2>/dev/null || true)
  case "$out" in *"Bot"*) echo "joined after ${i}x3s"; break;; esac
  sleep 3
done
case "$out" in *"Bot"*) ;; *) echo "client did not join"; tail -20 "$GD/logs/latest.log"; exit 1;; esac
# acceptTextures in servers.dat accepts the pack without a prompt. If a prompt shows anyway (an older client, or the
# entry was not matched), Tab focuses its first button (Proceed) and Return presses it; in game both keys are harmless.
WAIT=${PACK_WAIT:-120}
[ "$WAIT" -gt 0 ] || exit 0
for i in $(seq 1 $(( (WAIT + 2) / 3 ))); do
  if grep -q "Reloading ResourceManager: vanilla, server" "$GD/logs/latest.log" 2>/dev/null; then
    echo "pack applied after ${i}x3s"; exit 0
  fi
  if grep -qi "failed to download" "$GD/logs/latest.log" 2>/dev/null; then
    echo "pack download failed:"; grep -i "failed to download" "$GD/logs/latest.log" | tail -3; exit 2
  fi
  case $i in 5|10|20) DISPLAY="$DISP" xdotool key Tab Return 2>/dev/null || true;; esac
  sleep 3
done
echo "pack not applied after ${WAIT}s (is CraftEngine's self-host ip 127.0.0.1? was the pack built?)"; exit 2
