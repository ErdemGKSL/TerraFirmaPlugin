#!/usr/bin/env bash
# M0-VIS-02 helper. Port 26300 (RCON 26310), display :104, runs in /tmp/claude-0/vis02/.
#   run.sh prepare <mc>        first start (CE defaults), pack into resources/tfpvis02, real start, ce reload all
#   run.sh stop <mc>           RCON stop + wait for PID
#   run.sh client <mc> <scale> headless client on :104 (1920x1080), joins as Bot; teleports in creative
set -u
HERE=$(cd "$(dirname "$0")" && pwd); SP=$(cd "$HERE/.." && pwd); cd "$SP"
PORT=26300; RC="python3 client/rcon.py 26310"; DISP=:104; BASE=/tmp/claude-0/vis02
JAR=$HERE/build/libs/vis-02.jar
run() { echo "$BASE/paper-$1"; }
wait_done() { timeout 300 bash -c "until grep -q 'Done (' '$1'; do sleep 2; done" || { echo "no Done"; tail -30 "$1"; exit 1; }; }
start() {
  R=$(run "$1"); mkdir -p "$R"
  ( EXTRA_JVM="-Xmx2G" ./client/server.sh paper "$1" "$R" $PORT "$JAR" > "$R/console.log" 2>&1 < /dev/null & )
  wait_done "$R/console.log"; echo "up: $(grep -m1 'Done (' "$R/console.log")"
}
stop() {
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
  python3 "$HERE/build_pack.py" /opt/tfc "$R/plugins/CraftEngine/resources/tfpvis02"
  echo "== real start"; start "$MC"
  echo "== ce reload all"; $RC "ce reload all"; sleep 25
  grep -E "Found [0-9]+ issue|tfpvis02|vis02|WARN" "$R/console.log" | tail -40
  ;;
stop) stop "$2";;
client)
  MC=$2; SCALE=${3:-2}; G=$BASE/game-$MC
  mkdir -p "$G"; [ -f "$G/client.pid" ] && kill "$(cat "$G/client.pid")" 2>/dev/null
  if ! xdpyinfo -display $DISP >/dev/null 2>&1; then Xvfb $DISP -screen 0 1920x1080x24 >"$BASE/xvfb.log" 2>&1 & sleep 2; fi
  rm -f "$(run "$MC")"/world/playerdata/*.dat*
  python3 "$SP/gui-01/launch_client.py" "$MC" "localhost:$PORT" "$G" $DISP "$SCALE" > "$G/client.pid"
  for i in $(seq 1 120); do case "$($RC list 2>/dev/null)" in *Bot*)
    echo "joined after ~$((i*3))s"; $RC "gamemode creative Bot" >/dev/null; $RC "tp Bot 7.5 154 -8 0 20" >/dev/null
    sleep 25; exit 0;;
    esac; sleep 3; done
  echo "no join"; tail -20 "$G/client.log"; exit 1
  ;;
*) echo "usage: run.sh prepare|stop|client ..";;
esac
