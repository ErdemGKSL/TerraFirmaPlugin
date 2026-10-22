#!/usr/bin/env bash
# Builds a snow-free test site for colour measurements (grass, foliage, water tint) and puts Bot in it.
# Usage: testroom.sh <rcon-port> <x> <y> <z> [player]
#   x z  centre column; pick a low site (y about 64-80, `tfp debug chunk` average 2-5 C): the season tint comes from the
#        column's climate, and high columns are cold enough to be under snow all autumn.
#   y    floor height: the surface y of that column (the room replaces the floor and clears everything above it).
# What it builds (Paper; Folia's console cannot run fill over several regions):
#   - a 13x13 area cleared of snow, plants and trees up to y+12, floor of grass_block on dirt;
#   - a 15x15 barrier roof at y+8: snow never rests on barrier (vanilla's tag, kept by SnowService), the roof also keeps
#     the column's MOTION_BLOCKING top above the floor so no layer is placed below it, and barrier lets the sky light
#     through and is invisible, so the floor keeps full daylight;
#   - test strips: oak leaves (persistent) on the west, a 3x3 water pool on the east (it may still freeze in a cold month);
#   - clear weather, noon, Bot in creative standing at the south edge looking north and down at the floor.
# Repeat it after a month change if snow fell before the roof existed. F1 (xdotool key F1) hides the HUD for a shot.
set -eu
PORT=$1 X=$2 Y=$3 Z=$4 P=${5:-Bot}
HERE=$(cd "$(dirname "$0")" && pwd)
r() { python3 "$HERE/rcon.py" "$PORT" "$@"; }
r gamemode creative "$P"
r tp "$P" "$X" $((Y + 2)) "$Z"
sleep 5   # let the chunks load around the player before filling
r fill $((X - 6)) $((Y + 1)) $((Z - 6)) $((X + 6)) $((Y + 12)) $((Z + 6)) air
r fill $((X - 6)) $((Y - 2)) $((Z - 6)) $((X + 6)) $((Y - 1)) $((Z + 6)) dirt
r fill $((X - 6)) "$Y" $((Z - 6)) $((X + 6)) "$Y" $((Z + 6)) grass_block
r fill $((X - 7)) $((Y + 8)) $((Z - 7)) $((X + 7)) $((Y + 8)) $((Z + 7)) barrier
r fill $((X - 5)) $((Y + 1)) $((Z - 1)) $((X - 3)) $((Y + 2)) $((Z + 1)) 'oak_leaves[persistent=true]'
r fill $((X + 3)) "$Y" $((Z - 1)) $((X + 5)) "$Y" $((Z + 1)) water
r weather clear
r time set noon
r tp "$P" "$X" $((Y + 1)) $((Z + 5)) 180 35
echo "test room at $X $Y $Z ready"
