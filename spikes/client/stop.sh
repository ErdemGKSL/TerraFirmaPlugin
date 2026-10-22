#!/usr/bin/env bash
# Stops a server started by server.sh cleanly: waits until startup finished, then RCON "stop", then waits for the exit.
# Usage: stop.sh <run-dir> <port> [timeout-seconds]
# Never stop a server with a signal while it is still starting: Paper then runs the shutdown on Thread-0 and CraftEngine's
# furniture manager logs "Asynchronous Chunk getEntities call" (CraftEngine's own bug, see docs/testing/headless.md).
set -u
DIR=$1 PORT=$2 TIMEOUT=${3:-300}
HERE=$(cd "$(dirname "$0")" && pwd)
PID=$(cat "$DIR/server.pid" 2>/dev/null || true)
[ -n "$PID" ] && kill -0 "$PID" 2>/dev/null || { echo "server not running"; exit 0; }
for _ in $(seq 1 "$TIMEOUT"); do
  grep -q 'Done (' "$DIR/logs/latest.log" 2>/dev/null && break
  sleep 1
done
python3 "$HERE/rcon.py" $((PORT + 10)) stop > /dev/null || echo "rcon stop failed"
for _ in $(seq 1 "$TIMEOUT"); do
  kill -0 "$PID" 2>/dev/null || { echo "stopped"; exit 0; }
  sleep 1
done
echo "server $PID still running after ${TIMEOUT}s"; exit 1
