#!/usr/bin/env python3
"""Headless vanilla Minecraft client for spike/visual checks (offline-mode servers only).

Downloads the official client (Mojang piston-meta), libraries and non-sound assets into a cache,
then launches it under an X display (Xvfb + Mesa llvmpipe) and joins a server via quick play.
No Microsoft login: offline username only, so the server must run with online-mode=false.

  mcclient.py prepare 26.3
  mcclient.py launch 26.3 --server localhost:25565 --name Bot --display :99 --game-dir /tmp/mc
"""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

CACHE = Path(os.environ.get("MCCLIENT_CACHE", "/opt/mcclient"))
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def fetch(url, dest: Path, sha1=None):
    if dest.exists() and (sha1 is None or hashlib.sha1(dest.read_bytes()).hexdigest() == sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                data = r.read()
            if sha1 and hashlib.sha1(data).hexdigest() != sha1:
                raise IOError(f"sha1 mismatch {url}")
            tmp = dest.with_suffix(dest.suffix + ".part")
            tmp.write_bytes(data)
            tmp.replace(dest)
            return dest
        except Exception as e:  # noqa: BLE001 - retry any network error
            if attempt == 3:
                raise
            print(f"retry {url}: {e}", file=sys.stderr)


def rules_ok(rules):
    if not rules:
        return True
    allowed = False
    for r in rules:
        os_ok = "os" not in r or r["os"].get("name") in (None, "linux")
        if "features" in r:
            os_ok = False  # demo/custom resolution/quick play feature rules: handled by us
        if os_ok:
            allowed = r["action"] == "allow"
    return allowed


def version_json(version):
    vdir = CACHE / "versions" / version
    vj = vdir / f"{version}.json"
    if not vj.exists():
        m = json.loads(fetch(MANIFEST, CACHE / "manifest.json").read_text())
        url = next(v["url"] for v in m["versions"] if v["id"] == version)
        fetch(url, vj)
    return json.loads(vj.read_text())


def prepare(version):
    v = version_json(version)
    vdir = CACHE / "versions" / version
    client = v["downloads"]["client"]
    jobs = [(client["url"], vdir / f"{version}.jar", client["sha1"])]
    classpath = []
    for lib in v["libraries"]:
        if not rules_ok(lib.get("rules")):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if art:
            p = CACHE / "libraries" / art["path"]
            jobs.append((art["url"], p, art["sha1"]))
            classpath.append(str(p))
    idx = v["assetIndex"]
    index_path = CACHE / "assets" / "indexes" / f"{idx['id']}.json"
    fetch(idx["url"], index_path, idx["sha1"])
    objects = json.loads(index_path.read_text())["objects"]
    for name, o in objects.items():
        if name.endswith(".ogg"):
            continue  # sounds are not needed for visual checks
        h = o["hash"]
        jobs.append((f"https://resources.download.minecraft.net/{h[:2]}/{h}",
                     CACHE / "assets" / "objects" / h[:2] / h, h))
    with ThreadPoolExecutor(16) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))
    classpath.append(str(vdir / f"{version}.jar"))
    (vdir / "classpath.txt").write_text(":".join(classpath))
    print(f"prepared {version}: {len(jobs)} files")
    return v


def nbt_servers_dat(path: Path, ip: str):
    """servers.dat with one entry that auto-accepts the server resource pack (acceptTextures=1)."""
    def tag_string(name, value):
        nb, vb = name.encode(), value.encode()
        return b"\x08" + struct.pack(">H", len(nb)) + nb + struct.pack(">H", len(vb)) + vb

    def tag_byte(name, value):
        nb = name.encode()
        return b"\x01" + struct.pack(">H", len(nb)) + nb + struct.pack(">b", value)

    entry = tag_string("name", "spike") + tag_string("ip", ip) + tag_byte("acceptTextures", 1) + b"\x00"
    servers = b"\x09" + struct.pack(">H", 7) + b"servers" + b"\x0a" + struct.pack(">i", 1) + entry
    path.write_bytes(b"\x0a\x00\x00" + servers + b"\x00")


def launch(version, server, name, display, game_dir, java, extra_options):
    v = prepare(version)
    vdir = CACHE / "versions" / version
    gd = Path(game_dir)
    gd.mkdir(parents=True, exist_ok=True)
    nbt_servers_dat(gd / "servers.dat", server)
    opts = {
        "onboardAccessibility": "false", "skipMultiplayerWarning": "true", "joinedFirstServer": "true",
        "tutorialStep": "none", "narrator": "0", "pauseOnLostFocus": "false", "renderDistance": "6",
        "simulationDistance": "5", "maxFps": "30", "guiScale": "2", "fullscreen": "false",
        "soundCategory_master": "0.0", "overrideWidth": "1280", "overrideHeight": "720",
    }
    opts.update(extra_options)
    (gd / "options.txt").write_text("".join(f"{k}:{val}\n" for k, val in opts.items()))
    cp = (vdir / "classpath.txt").read_text()
    natives = vdir / "natives"
    natives.mkdir(exist_ok=True)
    cmd = [java, "-Xmx2G", f"-Djava.library.path={natives}", "-Dorg.lwjgl.util.Debug=false",
           "-cp", cp, v["mainClass"],
           "--username", name, "--version", version, "--gameDir", str(gd),
           "--assetsDir", str(CACHE / "assets"), "--assetIndex", v["assetIndex"]["id"],
           "--uuid", str(uuid.uuid3(uuid.NAMESPACE_OID, "OfflinePlayer:" + name)).replace("-", ""),
           "--accessToken", "0", "--userType", "legacy", "--versionType", "release",
           "--width", "1280", "--height", "720", "--quickPlayMultiplayer", server]
    env = dict(os.environ, DISPLAY=display, LIBGL_ALWAYS_SOFTWARE="1", GALLIUM_DRIVER="llvmpipe",
               MESA_GL_VERSION_OVERRIDE="4.5", MESA_GLSL_VERSION_OVERRIDE="450",
               SDL_VIDEO_X11_FORCE_EGL="1")
    log = open(gd / "client.log", "w")
    proc = subprocess.Popen(cmd, env=env, stdout=log, stderr=subprocess.STDOUT, cwd=gd)
    print(proc.pid)
    return proc


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("version")
    lp = sub.add_parser("launch")
    lp.add_argument("version")
    lp.add_argument("--server", default="localhost:25565")
    lp.add_argument("--name", default="Bot")
    lp.add_argument("--display", default=":99")
    lp.add_argument("--game-dir", required=True)
    lp.add_argument("--java", default="/opt/jdk/25/bin/java")
    lp.add_argument("--option", action="append", default=[], help="key:value for options.txt")
    a = ap.parse_args()
    if a.cmd == "prepare":
        prepare(a.version)
    else:
        launch(a.version, a.server, a.name, a.display, a.game_dir, a.java,
               dict(o.split(":", 1) for o in a.option))


if __name__ == "__main__":
    main()
