#!/usr/bin/env bash
# Records the screen (wall-clock timestamps) while running a vanilla control (title) and the heat steps.
# Usage: [OUT=dir] measure.sh <tag> <display>   (RCON port 25680, player Bot; run room.sh first)
set -u
TAG=$1 DISP=$2
D=${OUT:-/tmp/par-hud-10}/$TAG
R=$(git rev-parse --show-toplevel)/spikes/client/rcon.py
mkdir -p "$D"; rm -f "$D"/*
LOG="$D/rcon.log"
r() {
  local out
  echo "$(date +%s.%N) send: $*" >> "$LOG"
  out=$(python3 "$R" 25680 "$@" | tr '\n' ' ')
  echo "$(date +%s.%N) reply: $* | $out" >> "$LOG"
}
ffmpeg -loglevel error -f x11grab -framerate 30 -video_size 1280x720 -use_wallclock_as_timestamps 1 -i "$DISP" \
  -c:v libx264 -preset ultrafast -qp 0 -copyts -vsync passthrough "$D/rec.mkv" &
FF=$!
sleep 3
r title Bot times 0 40 0
for i in 1 2 3; do
  r title Bot title '{"text":"XXXX","color":"red"}'
  sleep 2.5
done
r title Bot clear
sleep 2
for t in 900 600 0 900 0; do
  r tfp debug heat Bot $t
  sleep 2.5
done
kill -INT $FF; wait $FF 2>/dev/null
echo done
