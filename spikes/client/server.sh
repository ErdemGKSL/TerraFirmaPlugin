#!/usr/bin/env bash
# Starts a server outside Gradle (so it can run next to a Gradle-driven run) for client checks.
# Usage: server.sh <paper|folia> <mc> <run-dir> <port> [extra plugin jars...]
# Needs the jars cached by run-paper (run any :harness:run* task once).
set -eu
PLATFORM=$1 MC=$2 DIR=$3 PORT=$4; shift 4
C=/root/.gradle/caches/run-task-jars
SERVER=$(ls -v $C/$PLATFORM/jars/$MC/*.jar | tail -1)
mkdir -p "$DIR/plugins"
for p in craftengine bettermodel betterhud2 packetevents; do cp -u $C/plugins/paper/modrinth/$p/*.jar "$DIR/plugins/"; done
for j in "$@"; do cp -u "$j" "$DIR/plugins/"; done
[ -f "$DIR/server.properties" ] || printf 'online-mode=false\nspawn-protection=0\nallow-nether=false\n' > "$DIR/server.properties"
# 26.x defaults white-list=true. RCON (port+10, password "spike") lets scripts send console commands.
for kv in "server-port=$PORT" "white-list=false" "enable-rcon=true" "rcon.port=$((PORT+10))" "rcon.password=spike" "online-mode=false"; do
  sed -i "/^${kv%%=*}=/d" "$DIR/server.properties"; echo "$kv" >> "$DIR/server.properties"
done
cd "$DIR"
echo $$ > server.pid
exec /opt/jdk/25/bin/java -Xmx3G ${EXTRA_JVM:-} -Dcom.mojang.eula.agree=true -Dtfp.assertThreads=true -jar "$SERVER" --nogui
