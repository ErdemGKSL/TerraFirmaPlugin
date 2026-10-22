#!/usr/bin/env python3
# Licensed under the EUPL, Version 1.2.
"""Last step of the HUD art pipeline: indexed-colour, losslessly compressed pixel-art PNGs.

  python3 tools/images/hud_palette.py <in.png> [<out.png>]     # standalone image (the info plate, a sprite)

Used as a module by tools/images/hud_layers.py, which saves every layer of a bar with ONE fixed palette (the same
index order in the master, the background and every fill), so the layers stay pixel-perfect and consistent.

- Palette PNG (mode "P", PLTE + tRNS). Index 0 is fully transparent (0, 0, 0, 0); every other entry is fully opaque.
  No semi-transparency: pixel art needs none.
- Exact nearest-colour mapping to the palette, no dithering (a colour that is in the palette maps to itself).
- 4-bit when the palette has at most 16 entries, else 8-bit; Pillow optimize=True, compress_level=9, then oxipng or
  zopflipng if one is installed (lossless; skipped otherwise).
- Deterministic: same input, same bytes (no timestamps or text chunks).
"""
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

TRANSPARENT = (0, 0, 0, 0)
ALPHA_THRESHOLD = 128


def hex_colour(c):
    return "#%02x%02x%02x" % c[:3]


def parse_hex(s):
    s = s.lstrip("#")
    return int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16)


def opaque_colours(*images):
    """The sorted set of opaque RGB colours in the images (alpha binarized at ALPHA_THRESHOLD)."""
    out = set()
    for img in images:
        for _, p in img.convert("RGBA").getcolors(maxcolors=1 << 24):
            if p[3] >= ALPHA_THRESHOLD:
                out.add(p[:3])
    return sorted(out)


def nearest(c, palette):
    return min(range(len(palette)), key=lambda i: (sum((c[k] - palette[i][k]) ** 2 for k in range(3)), i))


def to_indexed(img, palette):
    """RGBA -> mode "P" with `palette` (opaque RGB tuples) at indices 1.., index 0 transparent. Exact nearest colour."""
    if not 1 <= len(palette) <= 255:
        sys.exit(f"palette has {len(palette)} colours, expected 1..255")
    src = img.convert("RGBA")
    w, h = src.size
    out = Image.new("P", (w, h), 0)
    flat = list(TRANSPARENT[:3])
    for c in palette:
        flat.extend(c)
    out.putpalette(flat)
    cache = {}
    sp, op = src.load(), out.load()
    for y in range(h):
        for x in range(w):
            p = sp[x, y]
            if p[3] < ALPHA_THRESHOLD:
                continue
            rgb = p[:3]
            if rgb not in cache:
                cache[rgb] = 1 + nearest(rgb, palette)
            op[x, y] = cache[rgb]
    return out


def save(img_p, path, palette_len):
    """Saves a mode-"P" image: tRNS for index 0, 4 bits when the palette fits, max lossless compression."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    entries = palette_len + 1
    kwargs = {"optimize": True, "compress_level": 9, "transparency": 0}
    if entries <= 16:
        kwargs["bits"] = 4
    img_p.save(path, format="PNG", **kwargs)
    optimise_external(path)


def optimise_external(path):
    if shutil.which("oxipng"):
        subprocess.run(["oxipng", "-o", "max", "--strip", "all", "-q", str(path)], check=True)
    elif shutil.which("zopflipng"):
        tmp = path.with_suffix(".zopfli.png")
        subprocess.run(["zopflipng", "-y", "--keepcolortype", str(path), str(tmp)], check=True,
                       stdout=subprocess.DEVNULL)
        tmp.replace(path)


def convert(src, dst):
    img = Image.open(src).convert("RGBA")
    palette = opaque_colours(img)
    if not palette:
        palette = [(0, 0, 0)]   # a fully transparent sprite still needs one PLTE colour besides index 0
    save(to_indexed(img, palette), dst, len(palette))
    print(f"wrote {dst} ({len(palette)} colours + transparent)")


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    convert(Path(sys.argv[1]), Path(sys.argv[-1]))
