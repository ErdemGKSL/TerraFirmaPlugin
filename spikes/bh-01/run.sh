#!/usr/bin/env bash
# M0-BH-01 reproduction script. Everything in the spike note comes from this script's output.
#
#   run.sh prepare <paper|folia> <mc>          bootstrap start (creates BetterHud/CE folders), setup_run.py,
#                                              real start, `ce reload all`. Leaves the server running on :25700.
#   run.sh shots <mc> <gui-scale>...           one headless client per GUI scale: land + underwater screenshot.
#                                              Needs a running server (prepare). Screens: /tmp/claude-0/bh01-shots/
#   run.sh soak <mc>                           join one client, wait 60 s, screenshot, grep the server log.
#   run.sh stop <paper|folia> <mc>             stop the server (RCON stop) and the client.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
SP=$(cd "$HERE/.." && pwd)
JAR=$HERE/build/libs/bh-01.jar
RCON="python3 $SP/client/rcon.py 25710"
PORT=25700
SHOTS=/tmp/claude-0/bh01-shots
mkdir -p "$SHOTS"
cmd=$1; shift

run_dir() { echo "/tmp/claude-0/bh01-$1-$2"; }
game_dir() { if [ "$1" = folia ]; then echo "/tmp/claude-0/bh01-game-folia-$2"; else echo "/tmp/claude-0/bh01-game-$2"; fi; }

ensure_xvfb() {
  if ! xdpyinfo -display :101 >/dev/null 2>&1; then
    Xvfb :101 -screen 0 1280x720x24 >/tmp/claude-0/bh01-xvfb.log 2>&1 &
    sleep 2
  fi
}

start_server() {  # $1 = run dir, $2 = platform, $3 = mc
  # EXTRA_JVM comes after server.sh's -Xmx3G, so -Xmx2G wins (the box is shared; 3G servers got OOM-killed).
  ( cd "$SP" && EXTRA_JVM="-Xmx2G" ./client/server.sh "$2" "$3" "$1" $PORT "$JAR" > "$1/console.log" 2>&1 < /dev/null & )
  timeout 300 bash -c "until grep -q 'Done (' '$1/console.log' 2>/dev/null; do sleep 2; done" \
    || { echo "server did not finish starting"; tail -30 "$1/console.log"; exit 1; }
  echo "server up: $(grep -m1 'Done (' "$1/console.log")"
}

stop_server() {  # $1 = run dir
  [ -f "$1/server.pid" ] || return 0
  $RCON stop >/dev/null 2>&1
  for _ in $(seq 1 60); do kill -0 "$(cat "$1/server.pid")" 2>/dev/null || break; sleep 2; done
  echo "server stopped"
}

server_check() {  # grep the server log for CE/BH/thread problems
  grep -E "Found [0-9]+ issue|WARN.*(tfp|BetterHud|CraftEngine)|Successfully merged|ERROR|SEVERE|not owned|Thread failed main thread check|IllegalStateException|Exception" "$1/console.log" \
    | grep -vE "Failed to parse packetevents version|api.github.com" | sort | uniq -c | head -60
}

kill_client() {  # $1 = game dir
  if [ -f "$1/client.pid" ]; then
    kill "$(cat "$1/client.pid")" 2>/dev/null
    for _ in $(seq 1 20); do kill -0 "$(cat "$1/client.pid")" 2>/dev/null || break; sleep 1; done
  fi
}

wait_join() {  # waits until Bot is in the player list
  for _ in $(seq 1 120); do
    case "$($RCON list 2>/dev/null)" in *Bot*) echo "Bot joined"; return 0;; esac
    sleep 3
  done
  echo "Bot did not join"; return 1
}

launch_client() {  # $1 = mc, $2 = game dir, $3 = gui scale
  ensure_xvfb
  mkdir -p "$2"; kill_client "$2"
  rm -f "$2/logs/latest.log"
  python3 "$SP/client/mcclient.py" launch "$1" --server "localhost:$PORT" --game-dir "$2" \
    --display :101 --option "guiScale:$3" | tail -1 > "$2/client.pid"
}

shot() { DISPLAY=:101 import -window root "$1" && echo "screenshot $1"; }

case "$cmd" in
prepare)
  plat=$1; mc=$2; RUN=$(run_dir "$plat" "$mc")
  if [ ! -d "$RUN/plugins/BetterHud" ]; then
    echo "== bootstrap start (creates BetterHud and CraftEngine folders)"
    mkdir -p "$RUN"; start_server "$RUN" "$plat" "$mc"; stop_server "$RUN"
  fi
  echo "== setup_run.py"; python3 "$HERE/setup_run.py" "$RUN" || exit 1
  echo "== real start"; start_server "$RUN" "$plat" "$mc"
  echo "== ce reload all"; $RCON "ce reload all"; sleep 30
  server_check "$RUN"
  echo "== platform for shots"
  $RCON "time set day" >/dev/null
  $RCON "fill -10 150 -10 10 150 10 stone" >/dev/null
  $RCON "fill 2 151 2 6 154 6 water" >/dev/null
  ;;
shots)
  mc=$1; shift; GAME=$(game_dir bh "$mc")
  for scale in "$@"; do
    echo "== $mc gui $scale"
    launch_client "$mc" "$GAME" "$scale"
    wait_join || { tail -20 "$GAME/logs/latest.log"; continue; }
    sleep 40   # resource pack download + first frames (software GL is slow)
    $RCON "gamemode survival Bot" >/dev/null
    $RCON "tp Bot 0.5 151 0.5 0 0" >/dev/null; sleep 12
    shot "$SHOTS/$mc-gui$scale-land.png"
    if [ "$scale" = 2 ] && [ "$mc" = 26.3 ]; then
      sleep 6; shot "$SHOTS/$mc-gui$scale-land-6s-later.png"
    fi
    $RCON "tp Bot 4.5 151.5 4.5 0 0" >/dev/null; sleep 12
    shot "$SHOTS/$mc-gui$scale-underwater.png"
    $RCON "tp Bot 0.5 151 0.5 0 0" >/dev/null
    grep -iE "shader|failed to|resource ?pack" "$GAME/logs/latest.log" 2>/dev/null | head -5
    kill_client "$GAME"
  done
  ;;
soak)
  mc=$1; GAME=$(game_dir folia "$mc")
  launch_client "$mc" "$GAME" 2
  wait_join || exit 1
  sleep 60; shot "$SHOTS/folia-$mc-gui2-soak60.png"
  kill_client "$GAME"
  ;;
stop)
  RUN=$(run_dir "$1" "$2"); stop_server "$RUN"
  ;;
*) echo "unknown command: $cmd"; exit 2;;
esac
