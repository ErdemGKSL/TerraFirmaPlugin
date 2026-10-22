#!/usr/bin/env python3
# Licensed under the EUPL, Version 1.2.
"""Derive the two pixel-aligned layers of a HUD bar from ONE master image (frame + full fill).

  python3 tools/images/hud_layers.py <master.png> <spec.layers.json> <out-dir> [--check-dir DIR]

The spec (JSON) names the bar and its fill mask:
  {
    "name": "m3_surv_06_health_bar",          # output prefix
    "canvas": [81, 10],                        # must equal the master's size
    "interior": [[1, 1, 80, 9]],              # fill mask: rectangles [x0, y0, x1, y1), canvas pixels
    "ring": "shade",                           # optional: "shade" darkens light frame pixels (thin bars whose
                                               #   downscaled outline blended with the fill); "keep" (default)
    "variants": {"low": {...}, "critical": {...}}   # optional extra fills, see VARIANT_OPS
  }

Outputs, all the same canvas size:
  <name>_bg.png              the master with every interior pixel replaced by its "empty" version
                             (desaturated and darkened, fully opaque): the empty bar
  <name>_fill.png            transparent canvas with only the master's interior pixels: the fill, no frame
  <name>_fill_<variant>.png  the fill with a colour transform (gauge low/critical states)
and, with --check-dir, <name>_overlay_check.png: bg + fill clipped at 0/25/50/75/100 %, 8x, for a human look.

Deterministic (no randomness, no timestamps). Fails if an interior pixel of the master is transparent (a hole would
show through the fill), if the frame ring is empty, or if the master is not the canvas size.
"""
import argparse
import colorsys
import json
import sys
from pathlib import Path

from PIL import Image

EMPTY_SATURATION = 0.25   # share of the original saturation kept in the empty interior
EMPTY_VALUE = 0.32        # brightness multiplier of the empty interior
RING_SHADE_LIGHTNESS = 0.45   # with "ring": "shade", frame pixels lighter than this (0..1) ...
RING_SHADE_VALUE = 0.30       # ... are darkened to this share of their brightness


def interior_mask(spec, w, h):
    mask = [[False] * w for _ in range(h)]
    for x0, y0, x1, y1 in spec["interior"]:
        if not (0 <= x0 < x1 <= w and 0 <= y0 < y1 <= h):
            sys.exit(f"interior rectangle {[x0, y0, x1, y1]} outside the {w}x{h} canvas")
        for y in range(y0, y1):
            for x in range(x0, x1):
                mask[y][x] = True
    return mask


def hsv_map(rgba, fn):
    r, g, b, a = rgba
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    h, s, v = fn(h, s, v)
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, min(max(s, 0), 1), min(max(v, 0), 1))
    return round(r * 255), round(g * 255), round(b * 255), a


def lerp(c, t, k):
    return tuple(round(c[i] + (t[i] - c[i]) * k) for i in range(3))


def variant_op(op):
    """A colour transform from the spec: {"saturation": f, "value": f, "tint": [r, g, b], "tint_amount": f,
    "tint_by_value": bool}."""
    def apply(p):
        q = hsv_map(p, lambda h, s, v: (h, s * op.get("saturation", 1.0), v * op.get("value", 1.0)))
        if "tint" in op:
            k = op.get("tint_amount", 0.5)
            if op.get("tint_by_value"):
                k *= max(q[:3]) / 255   # dark pixels (separators, shading) keep their darkness
            q = lerp(q[:3], op["tint"], k) + (q[3],)
        return q
    return apply


def derive(master, spec):
    w, h = spec["canvas"]
    if master.size != (w, h):
        sys.exit(f"master is {master.size[0]}x{master.size[1]}, spec canvas is {w}x{h}")
    mask = interior_mask(spec, w, h)
    src = master.load()
    bg = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fill = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    b, f = bg.load(), fill.load()
    ring = 0
    for y in range(h):
        for x in range(w):
            p = src[x, y]
            if mask[y][x]:
                if p[3] != 255:
                    sys.exit(f"master pixel {x},{y} inside the interior is not opaque (alpha {p[3]})")
                f[x, y] = p
                b[x, y] = hsv_map(p, lambda hh, s, v: (hh, s * EMPTY_SATURATION, v * EMPTY_VALUE))
            elif p[3]:
                ring += 1
                if spec.get("ring", "keep") == "shade" and sum(p[:3]) / 765 > RING_SHADE_LIGHTNESS:
                    p = hsv_map(p, lambda hh, s, v: (hh, s, v * RING_SHADE_VALUE))
                b[x, y] = p
    if ring == 0:
        sys.exit("the master has no frame pixels outside the interior")
    out = {"bg": bg, "fill": fill}
    for name, op in sorted(spec.get("variants", {}).items()):
        fn = variant_op(op)
        v = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        vp = v.load()
        for y in range(h):
            for x in range(w):
                if f[x, y][3]:
                    vp[x, y] = fn(f[x, y])
        out["fill_" + name] = v
    return out


def overlay_check(bg, fill, spec, scale=8):
    w, h = spec["canvas"]
    xs = sorted({x for x0, _, x1, _ in spec["interior"] for x in range(x0, x1)})
    rows = []
    for pct in (0, 25, 50, 75, 100):
        cut = xs[0] + round((xs[-1] + 1 - xs[0]) * pct / 100)
        frame = Image.new("RGBA", (w, h), (255, 0, 255, 255))
        frame.alpha_composite(bg)
        clipped = fill.crop((0, 0, max(cut, 1), h))
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        layer.paste(clipped, (0, 0))
        frame.alpha_composite(layer)
        rows.append(frame.resize((w * scale, h * scale), Image.Resampling.NEAREST))
    sheet = Image.new("RGBA", (w * scale, sum(r.height + scale for r in rows)), (40, 40, 40, 255))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height + scale
    return sheet


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("master", type=Path)
    p.add_argument("spec", type=Path)
    p.add_argument("out_dir", type=Path)
    p.add_argument("--check-dir", type=Path)
    args = p.parse_args()
    spec = json.loads(args.spec.read_text(encoding="utf-8"))
    master = Image.open(args.master).convert("RGBA")
    layers = derive(master, spec)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    for key, img in layers.items():
        path = args.out_dir / f"{spec['name']}_{key}.png"
        img.save(path, optimize=False)
        print(f"wrote {path} ({img.width}x{img.height})")
    if args.check_dir:
        args.check_dir.mkdir(parents=True, exist_ok=True)
        path = args.check_dir / f"{spec['name']}_overlay_check.png"
        overlay_check(layers["bg"], layers["fill"], spec).save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
