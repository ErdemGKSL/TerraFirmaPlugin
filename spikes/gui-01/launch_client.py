#!/usr/bin/env python3
"""Launches the shared headless vanilla client (client/mcclient.py) with a 1920x1080 window.

mcclient.launch() hard-codes 1280x720, which is too short for a 6-row chest (222 px) at GUI scale 4.
Usage: launch_client.py <mc> <host:port> <game-dir> <display> <gui-scale>   (prints the client PID)
"""
import contextlib
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "client"))
import mcclient  # noqa: E402


def main():
    version, server, game_dir, display, scale = sys.argv[1:6]
    with contextlib.redirect_stdout(sys.stderr):  # prepare() prints; stdout must hold only the PID
        v = mcclient.prepare(version)
    vdir = mcclient.CACHE / "versions" / version
    gd = Path(game_dir)
    gd.mkdir(parents=True, exist_ok=True)
    mcclient.nbt_servers_dat(gd / "servers.dat", server)
    opts = {"onboardAccessibility": "false", "skipMultiplayerWarning": "true", "joinedFirstServer": "true",
            "tutorialStep": "none", "narrator": "0", "pauseOnLostFocus": "false", "renderDistance": "6",
            "simulationDistance": "5", "maxFps": "30", "guiScale": scale, "fullscreen": "false",
            "soundCategory_master": "0.0"}
    (gd / "options.txt").write_text("".join(f"{k}:{val}\n" for k, val in opts.items()))
    natives = vdir / "natives"
    natives.mkdir(exist_ok=True)
    cp = (vdir / "classpath.txt").read_text()
    cmd = ["/opt/jdk/25/bin/java", "-Xmx2G", f"-Djava.library.path={natives}", "-cp", cp, v["mainClass"],
           "--username", "Bot", "--version", version, "--gameDir", str(gd),
           "--assetsDir", str(mcclient.CACHE / "assets"), "--assetIndex", v["assetIndex"]["id"],
           "--uuid", "00000000000000000000000000000000", "--accessToken", "0", "--userType", "legacy",
           "--versionType", "release", "--width", "1920", "--height", "1080", "--quickPlayMultiplayer", server]
    env = dict(os.environ, DISPLAY=display, LIBGL_ALWAYS_SOFTWARE="1", GALLIUM_DRIVER="llvmpipe",
               MESA_GL_VERSION_OVERRIDE="4.5", MESA_GLSL_VERSION_OVERRIDE="450", SDL_VIDEO_X11_FORCE_EGL="1")
    log = open(gd / "client.log", "w")
    proc = subprocess.Popen(cmd, env=env, stdout=log, stderr=subprocess.STDOUT, cwd=gd)
    print(proc.pid)


if __name__ == "__main__":
    main()
