#!/usr/bin/env bash
# Joins a running server with the headless client and waits until the player is in the world.
# Usage: session.sh <mc-version> <port> <game-dir> [display]
# Afterwards: shot.sh <file> for screenshots, rcon.py <port+10> <cmd> for server commands,
# xdotool (DISPLAY) for keys. Kill with: kill $(cat <game-dir>/client.pid)
set -eu
VER=$1 PORT=$2 GD=$3 DISP=${4:-:99}
HERE=$(cd "$(dirname "$0")" && pwd)
if ! xdpyinfo -display "$DISP" > /dev/null 2>&1; then
  Xvfb "$DISP" -screen 0 1280x720x24 > /dev/null 2>&1 &
  sleep 2
fi
mkdir -p "$GD"; rm -f "$GD/logs/latest.log"
python3 "$HERE/mcclient.py" launch "$VER" --server "localhost:$PORT" --game-dir "$GD" --display "$DISP" | tail -1 > "$GD/client.pid"
# Wait for the join (server side) - the resource pack is auto-accepted via servers.dat.
for i in $(seq 1 120); do
  out=$(python3 "$HERE/rcon.py" $((PORT+10)) list 2>/dev/null || true)
  case "$out" in *"Bot"*) echo "joined after ${i}x3s"; exit 0;; esac
  sleep 3
done
echo "client did not join"; tail -20 "$GD/logs/latest.log"; exit 1
