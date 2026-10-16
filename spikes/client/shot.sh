#!/usr/bin/env bash
# shot.sh <out.png> [display] - screenshot of the X display.
DISPLAY=${2:-:99} import -window root "$1"
