#!/usr/bin/env bash
# PAR-HUD-10 unblock: snow room, Bot in survival with a copper ingot, cold.
set -u
R=$(git rev-parse --show-toplevel)/spikes/client/rcon.py
P=25680
r() { echo "> $*"; python3 "$R" "$P" "$@"; }
r difficulty peaceful
r gamerule advance_time false
r weather clear
r time set noon
r gamemode survival Bot
r effect give Bot minecraft:saturation 1000 255 true
r clear Bot
r fill -8 99 -8 8 99 8 minecraft:snow_block
r fill -8 100 -8 8 106 8 minecraft:air
r setblock -7 100 -7 'minecraft:light[level=15]'
r setblock 7 100 -7 'minecraft:light[level=15]'
r setblock -7 100 7 'minecraft:light[level=15]'
r setblock 7 100 7 'minecraft:light[level=15]'
r tp Bot 0 100 5 180 35
r ce item give Bot tfp:metal/ingot/copper
r tfp debug heat Bot 0
echo "room ready"
