#!/usr/bin/env bash
# M0-VIS-01 client check for one MC version. Usage: vis-01/run-client-check.sh <26.3|1.21.11> <port>
# Output: run/vis01/<ver>/vis01-report.txt (pools) + /tmp/claude-0/vis01-<ver>-*.png (screenshots)
set -eu
cd "$(dirname "$0")/.."
VER=$1 PORT=$2 DIR=run/vis01/paper-$1 RCON=$(($2+10)) OUT=${OUT:-/tmp/claude-0}
mkdir -p "$DIR" "$OUT"
start_server() { (./client/server.sh paper "$VER" "$DIR" "$PORT" vis-01/build/libs/vis-01.jar > "logs/vis01-$VER-server.log" 2>&1 < /dev/null &);
  timeout 300 bash -c "until grep -q 'Done (' logs/vis01-$VER-server.log; do sleep 2; done"; }
# 1st start: CE extracts its default packs. It skips that if plugins/CraftEngine/resources already
# exists, so our pack goes in only afterwards. Self-host on 127.0.0.1 for the local client.
if [ ! -d "$DIR/plugins/CraftEngine/resources/internal" ]; then
  start_server; python3 client/rcon.py $RCON stop
  while kill -0 "$(cat "$DIR/server.pid")" 2>/dev/null; do sleep 1; done  # world lock released
fi
sed -i -E 's/^(\s*ip:) "auto"/\1 "127.0.0.1"/' "$DIR/plugins/CraftEngine/config.yml"
python3 vis-01/build_pack.py /opt/tfc "$DIR/plugins/CraftEngine/resources/tfpspike"
# 2nd start; then a full CE reload builds the pack (CE never builds it at startup).
start_server
python3 client/rcon.py $RCON "ce reload all" ; sleep 20
grep -E "Found [0-9]+ issue|tfpspike|vis01" "logs/vis01-$VER-server.log" | tail -60 > "$OUT/vis01-$VER-ce-issues.txt" || true
./client/session.sh "$VER" "$PORT" "$OUT/mc-$VER"
sleep 20
python3 client/rcon.py $RCON "time set noon"
python3 client/rcon.py $RCON "vis01 grid 0 150 0"; sleep 3
python3 client/rcon.py $RCON "gamemode creative Bot"
# view 1: from the front-south, looking north at both rows
python3 client/rcon.py $RCON "tp Bot 12 150 12 180 30"; sleep 12; ./client/shot.sh "$OUT/vis01-$VER-overview.png"
python3 client/rcon.py $RCON "tp Bot 4 150 6 180 25"; sleep 8; ./client/shot.sh "$OUT/vis01-$VER-left.png"
python3 client/rcon.py $RCON "tp Bot 20 150 6 180 25"; sleep 8; ./client/shot.sh "$OUT/vis01-$VER-right.png"
python3 client/rcon.py $RCON "tp Bot 36 150 6 180 25"; sleep 8; ./client/shot.sh "$OUT/vis01-$VER-far.png"
python3 client/rcon.py $RCON "time set midnight"; sleep 6; ./client/shot.sh "$OUT/vis01-$VER-night.png"
kill "$(cat "$OUT/mc-$VER/client.pid")" || true
python3 client/rcon.py $RCON stop || true
cp "$DIR/vis01-report.txt" "$OUT/vis01-$VER-pools.txt" 2>/dev/null || true
echo done
