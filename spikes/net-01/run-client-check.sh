#!/usr/bin/env bash
# M0-NET-01 client check. Usage: net-01/run-client-check.sh <26.3|1.21.11> <port> [display, default :99]
# Output: /tmp/claude-0/net01-<ver>-{summer,autumn}.png, /tmp/claude-0/net01-<ver>-report.txt
set -eu
cd "$(dirname "$0")/.."
VER=$1 PORT=$2 DISP=${3:-:99} RCON=$(($2+10)) DIR=run/net01/paper-$1 OUT=${OUT:-/tmp/claude-0}
rm -rf "$DIR"; mkdir -p "$DIR/world/datapacks"
SRC=$(ls -d run/*/paper-$VER run/paper-$VER 2>/dev/null | head -1)   # any earlier run dir has the vanilla jar
python3 net-01/build_datapack.py "$SRC" "$DIR/world/datapacks/tfpnet"
printf 'worlds:\n  world:\n    generator: TfpSpikeNet01\n' > "$DIR/bukkit.yml"
(./client/server.sh paper "$VER" "$DIR" "$PORT" harness/build/libs/harness.jar net-01/build/libs/net-01.jar > "logs/net01-$VER-server.log" 2>&1 < /dev/null &)
timeout 900 bash -c "until grep -q 'Done (' logs/net01-$VER-server.log; do sleep 2; done"
r() { python3 client/rcon.py $RCON "$@"; }
./client/session.sh "$VER" "$PORT" "$OUT/mc-net-$VER" "$DISP"
sleep 15
r "time set noon"; r "gamemode creative Bot"; r "tp Bot 0 70 0 45 35"; sleep 12
./client/shot.sh "$OUT/net01-$VER-summer.png" "$DISP"
r "net01 give Bot"; sleep 2
r "net01 creativetest Bot"; sleep 3
r "net01 season autumn"; sleep 15
./client/shot.sh "$OUT/net01-$VER-autumn.png" "$DISP"
r "net01 season summer"; sleep 15
./client/shot.sh "$OUT/net01-$VER-summer-again.png" "$DISP"
r "net01 pipeline Bot"
r "net01 report" > "$OUT/net01-$VER-report.txt"
cp "$DIR/net01-report.txt" "$OUT/net01-$VER-report.txt" 2>/dev/null || true
kill "$(cat "$OUT/mc-net-$VER/client.pid")" || true
r stop || true
echo done
