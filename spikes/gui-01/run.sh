#!/usr/bin/env bash
# M0-GUI-01 helper. Port 26200 (RCON 26210), display :103, runs in /tmp/claude-0/gui01/.
#   run.sh prepare <mc>        first start (CE defaults), pack into resources/tfpgui, real start, ce reload all
#   run.sh stop <mc>           RCON stop + wait for PID
#   run.sh client <mc> <scale> headless client on :103 at 1920x1080, joins as Bot (scale = GUI scale)
set -u
HERE=$(cd "$(dirname "$0")" && pwd); SP=$(cd "$HERE/.." && pwd); cd "$SP"
PORT=26200; RC="python3 client/rcon.py 26210"; DISP=:103; BASE=/tmp/claude-0/gui01
JAR=$HERE/build/libs/gui-01.jar
run() { echo "$BASE/paper-$1"; }
wait_done() { timeout 300 bash -c "until grep -q 'Done (' '$1'; do sleep 2; done" || { echo "no Done"; tail -30 "$1"; exit 1; }; }
start() {  # $1 = mc
  R=$(run "$1"); mkdir -p "$R"
  ( EXTRA_JVM="-Xmx2G" ./client/server.sh paper "$1" "$R" $PORT "$JAR" > "$R/console.log" 2>&1 < /dev/null & )
  wait_done "$R/console.log"; echo "up: $(grep -m1 'Done (' "$R/console.log")"
}
stop() {  # $1 = mc
  R=$(run "$1"); $RC stop >/dev/null 2>&1
  for _ in $(seq 1 60); do kill -0 "$(cat "$R/server.pid")" 2>/dev/null || break; sleep 2; done; echo stopped
}
case "$1" in
prepare)
  MC=$2; R=$(run "$MC")
  if [ ! -d "$R/plugins/CraftEngine/resources/internal" ]; then
    echo "== first start (CE extracts its default packs)"; start "$MC"; stop "$MC"
    while kill -0 "$(cat "$R/server.pid")" 2>/dev/null; do sleep 1; done
  fi
  sed -i -E 's/^(\s*ip:) "auto"/\1 "127.0.0.1"/' "$R/plugins/CraftEngine/config.yml"
  python3 "$HERE/build_pack.py" "$R/plugins/CraftEngine/resources/tfpgui"
  echo "== real start"; start "$MC"
  echo "== ce reload all"; $RC "ce reload all"; sleep 25
  grep -E "Found [0-9]+ issue|tfpgui|gui01|WARN" "$R/console.log" | tail -40
  ;;
stop) stop "$2";;
client)
  MC=$2; SCALE=$3; G=$BASE/game-$MC
  mkdir -p "$G"; [ -f "$G/client.pid" ] && kill "$(cat "$G/client.pid")" 2>/dev/null
  if ! xdpyinfo -display $DISP >/dev/null 2>&1; then Xvfb $DISP -screen 0 1920x1080x24 >"$BASE/xvfb.log" 2>&1 & sleep 2; fi
  # Bot's data is cleared while offline so an earlier death doesn't stick (the spawn is teleported in creative).
  rm -f "$(run "$MC")"/world/playerdata/*.dat*
  python3 "$HERE/launch_client.py" "$MC" "localhost:$PORT" "$G" $DISP "$SCALE" > "$G/client.pid"
  for i in $(seq 1 120); do case "$($RC list 2>/dev/null)" in *Bot*)
    # Reset player data spawns the bot high and it can fall to death before any command: teleport in creative first.
    echo "joined after ~$((i*3))s"; $RC "gamemode creative Bot" >/dev/null; $RC "tp Bot 0.5 150 0.5 0 0" >/dev/null
    sleep 25; exit 0;;   # stays in creative: alignment checks need no survival, and creative takes no fall damage
    esac; sleep 3; done
  echo "no join"; tail -20 "$G/client.log"; exit 1
  ;;
chest)  # chest <mc> <shot-name> [items]: teleport, open /gui01 chest, screenshot, pixel check (scale in name)
  MC=$2; NAME=$3; export DISPLAY=$DISP; SHOTS=/tmp/claude-0/gui01-shots; mkdir -p "$SHOTS"
  $RC "gamemode creative Bot" >/dev/null; $RC "tp Bot 0.5 150 0.5 0 0" >/dev/null; sleep 20
  # close any screen left open by the last run: a click outside a container closes it (empty cursor)
  xdotool mousemove 300 300 click 1; sleep 2
  xdotool key t; sleep 2; xdotool type --delay 80 "/gui01 chest ${4:-}"; sleep 1
  xdotool key Return; sleep 4; xdotool mousemove 300 300; sleep 1
  import -window root "$SHOTS/$NAME.png" && echo "shot $SHOTS/$NAME.png"
  SCALE=$(echo "$NAME" | sed -E 's/.*gui([0-9]).*/\1/')
  [ -n "${4:-}" ] || python3 "$HERE/check_align.py" "$SHOTS/$NAME.png" "$SCALE" \
    /tmp/claude-0/gui01/paper-$MC/plugins/CraftEngine/resources/tfpgui/resourcepack/assets/tfpgui/textures/font/gui/chest6.png
  ;;
*) echo "usage: run.sh prepare|stop|client|chest ..";;
esac
