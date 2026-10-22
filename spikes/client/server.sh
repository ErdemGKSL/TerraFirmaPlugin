#!/usr/bin/env bash
# Starts a server outside Gradle (so it can run next to a Gradle-driven run) for client checks.
# Usage: server.sh <paper|folia> <mc> <run-dir> <port> [extra plugin jars...]
# Needs the jars cached by run-paper (run any :harness:run* task once).
# Env:
#   EXTRA_JVM="-Dk=v ..."  extra JVM flags
#   PACK_PORT=8163         CraftEngine self-host port (give each parallel server its own)
#   TFP_WORLD=1            make the default world a TFC world (bukkit.yml generator: TerraFirmaPlugin)
# Stop the server with `rcon.py <port+10> stop`, not a signal: a signal shuts down on Thread-0 and CraftEngine then logs
# "Asynchronous Chunk getEntities call" (docs/testing/headless.md).
set -eu
PLATFORM=$1 MC=$2 DIR=$3 PORT=$4; shift 4
PACK_PORT=${PACK_PORT:-8163}
C=/root/.gradle/caches/run-task-jars
# Other agents run servers on this machine: fail fast on a taken port instead of a crash report after startup.
for p in "$PORT" $((PORT + 10)) "$PACK_PORT"; do
  python3 -c 'import socket,sys; s=socket.socket(); s.bind(("0.0.0.0", int(sys.argv[1])))' "$p" 2>/dev/null \
    || { echo "server.sh: port $p is in use (server port, RCON port+10 or PACK_PORT)" >&2; exit 1; }
done
SERVER=$(ls -v $C/$PLATFORM/jars/$MC/*.jar | tail -1)
mkdir -p "$DIR/plugins"
for p in craftengine bettermodel betterhud2 packetevents; do cp -u $C/plugins/paper/modrinth/$p/*.jar "$DIR/plugins/"; done
for j in "$@"; do cp -u "$j" "$DIR/plugins/"; done
[ -f "$DIR/server.properties" ] || printf 'online-mode=false\nspawn-protection=0\nallow-nether=false\n' > "$DIR/server.properties"
# 26.x defaults white-list=true. RCON (port+10, password "spike") lets scripts send console commands.
for kv in "server-port=$PORT" "white-list=false" "enable-rcon=true" "rcon.port=$((PORT+10))" "rcon.password=spike" "online-mode=false"; do
  sed -i "/^${kv%%=*}=/d" "$DIR/server.properties"; echo "$kv" >> "$DIR/server.properties"
done
if [ "${TFP_WORLD:-0}" = 1 ] && [ ! -f "$DIR/bukkit.yml" ]; then
  # Bukkit merges its defaults into an existing bukkit.yml, so this section is enough before the first start.
  printf 'worlds:\n  world:\n    generator: TerraFirmaPlugin\n' > "$DIR/bukkit.yml"
fi
# CraftEngine self-hosts the pack on "auto" (the public address), which the headless client cannot reach: use loopback.
# CraftEngine writes config.yml only on its first start, so seed it from the jar's default before that start: then the
# very first join already gets a reachable URL. one_time_token=false: a token is spent by the first download attempt,
# so a client that retries (or rejoins) gets HTTP 403 with it on.
CE_CONFIG="$DIR/plugins/CraftEngine/config.yml"
if [ ! -f "$CE_CONFIG" ]; then
  mkdir -p "$(dirname "$CE_CONFIG")"
  unzip -p "$(ls $C/plugins/paper/modrinth/craftengine/*.jar | head -1)" config.yml > "$CE_CONFIG"
fi
sed -i "/^  self-host:/,/^  [a-z]/{s/^    ip: .*/    ip: \"127.0.0.1\"/;s/^    port: .*/    port: $PACK_PORT/;s/^    one_time_token: .*/    one_time_token: false/}" "$CE_CONFIG"
grep -q '^    ip: "127.0.0.1"' "$CE_CONFIG" || { echo "server.sh: could not set the CraftEngine self-host ip in $CE_CONFIG" >&2; exit 1; }
cd "$DIR"
echo $$ > server.pid
exec /opt/jdk/25/bin/java -Xmx3G ${EXTRA_JVM:-} -Dcom.mojang.eula.agree=true -Dtfp.assertThreads=true -jar "$SERVER" --nogui
