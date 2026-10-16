#!/usr/bin/env python3
"""M0-BH-01: writes the spike content into a server run dir.

Usage: setup_run.py <run-dir>
The run dir must have been started once already, so BetterHud and CraftEngine created their
default folders (BetterHud copies its defaults only when its data folder does not exist yet).

Writes:
  plugins/BetterHud/packs/tfp/{images,layouts,huds,texts}/  copied from pack/betterhud/
  plugins/BetterHud/packs/tfp/assets/tfp/*.png              bar art (generated flat colours, spike only)
  plugins/CraftEngine/resources/tfphud/                      transparent vanilla HUD sprites (hearts, food, air)
  config edits: BetterHud default-hud -> [], CraftEngine self-host ip -> 127.0.0.1
"""
import io
import re
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
JARS = {
    "1.21.11": Path("/opt/mcclient/versions/1.21.11/1.21.11.jar"),
    "26.3": Path("/opt/mcclient/versions/26.3/26.3.jar"),
}
SPRITE_DIR = "assets/minecraft/textures/gui/sprites/hud/"
HIDE_PREFIXES = ("heart/", "food_", "air")  # hearts (all 56 incl. absorbing/withered), food, air bubbles


def hide_sprites(jar: Path) -> dict:
    """{path: (w, h)} for every vanilla sprite we replace with a transparent one."""
    out = {}
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if not name.startswith(SPRITE_DIR) or not name.endswith(".png"):
                continue
            rel = name[len(SPRITE_DIR):]
            if rel.startswith(HIDE_PREFIXES):
                out[name] = Image.open(io.BytesIO(z.read(name))).size
    return out


def bar(fill, edge, size=(81, 9)):
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    px = im.load()
    w, h = size
    for x in range(w):
        for y in range(h):
            px[x, y] = edge if x in (0, w - 1) or y in (0, h - 1) else fill
    return im


def sub(path: Path, text: str, new: str, count: int = 1):
    s = path.read_text()
    found = len(re.findall(text, s, flags=re.M))
    if found != count:
        sys.exit(f"{path}: expected {count} match(es) for {text!r}, found {found}")
    path.write_text(re.sub(text, new, s, flags=re.M))


def main():
    run = Path(sys.argv[1]).resolve()
    bh = run / "plugins/BetterHud"
    ce = run / "plugins/CraftEngine"
    if not bh.is_dir() or not (ce / "resources").is_dir():
        sys.exit("run dir was not started once (BetterHud/CraftEngine folders missing)")

    # 1. Vanilla sprites: the set must be identical in both client jars.
    a, b = hide_sprites(JARS["1.21.11"]), hide_sprites(JARS["26.3"])
    if a != b:
        sys.exit(f"sprite sets differ: only 1.21.11={sorted(set(a) - set(b))} only 26.3={sorted(set(b) - set(a))}")
    res = ce / "resources/tfphud"
    if res.exists():
        shutil.rmtree(res)
    (res / "resourcepack").mkdir(parents=True)
    (res / "pack.yml").write_text("author: tfp-spike\nversion: 1.0\ndescription: M0-BH-01 transparent vanilla HUD sprites\nnamespace: tfphud\n")
    for name, size in a.items():
        dst = res / "resourcepack" / name
        dst.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGBA", size, (0, 0, 0, 0)).save(dst, optimize=True)
    print(f"CE layer: {len(a)} transparent sprites -> {res / 'resourcepack'}")

    # 2. BetterHud pack folder.
    pack = bh / "packs/tfp"
    if pack.exists():
        shutil.rmtree(pack)
    for folder in ("images", "layouts", "huds", "texts"):
        shutil.copytree(HERE / "pack/betterhud" / folder, pack / folder)
    art = pack / "assets/tfp"
    art.mkdir(parents=True)
    bar((40, 40, 40, 255), (10, 10, 10, 255)).save(art / "health_bg.png")
    bar((200, 40, 40, 255), (120, 20, 20, 255)).save(art / "health_fill.png")
    bar((40, 40, 40, 255), (10, 10, 10, 255)).save(art / "thirst_bg.png")
    bar((46, 134, 222, 255), (20, 70, 130, 255)).save(art / "thirst_fill.png")
    print(f"BetterHud pack -> {pack}")

    # 3. Config edits (BetterHud re-saves config.yml keeping user values).
    # BetterHud writes list items at column 0 ("- test_hud"). Both edits are idempotent.
    bh_cfg = bh / "config.yml"
    if "default-hud: []" not in bh_cfg.read_text():
        sub(bh_cfg, r"^default-hud:\n(?:[ ]*- test_hud\n)", "default-hud: []\n")
    ce_cfg = ce / "config.yml"
    if '"127.0.0.1"' not in ce_cfg.read_text():
        sub(ce_cfg, r'^(  self-host:\n    ip: )"auto"', r'\1"127.0.0.1"')
    print("config: BetterHud default-hud=[], CraftEngine self-host.ip=127.0.0.1")


if __name__ == "__main__":
    main()
