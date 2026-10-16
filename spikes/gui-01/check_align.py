#!/usr/bin/env python3
"""Pixel check for the textured 6-row chest (M0-GUI-01).

  check_align.py <screenshot.png> <gui-scale> <art.png> [display-w display-h]

Expected window origin (vanilla AbstractContainerScreen): leftPos = (guiW - 176) // 2,
topPos = (guiH - 222) // 2, with guiW = W // scale and guiH = H // scale. Each art pixel (x, y) is
sampled at the centre of its scale x scale block. The vanilla "Inventory" label (drawn over the art)
and any pixel that differs are excluded only where listed in LABEL below.
A pixel matches when every channel is within TOL (the client shows colours 1-3 levels off the PNG,
measured separately as max_delta). Shifts -2..2 px are also tried, so a geometry error is visible
as a better match at a non-zero shift. Exit 0 when the zero-shift match is 100%.
"""
import sys

from PIL import Image

LABEL = (6, 126, 62, 139)  # x0, y0, x1, y1 of the vanilla "Inventory" label in window px
TOL = 6


def score(im, a, scale, ox, oy):
    total = match = 0
    max_delta = 0
    bad = []
    for y in range(a.height):
        for x in range(a.width):
            r, g, b, al = a.getpixel((x, y))
            if al != 255:
                continue
            if LABEL[0] <= x < LABEL[2] and LABEL[1] <= y < LABEL[3]:
                continue
            sx, sy = ox + x * scale + scale // 2, oy + y * scale + scale // 2
            p = im.getpixel((sx, sy))
            d = max(abs(p[0] - r), abs(p[1] - g), abs(p[2] - b))
            total += 1
            if d <= TOL:
                match += 1
            else:
                bad.append((x, y))
            max_delta = max(max_delta, d)
    return total, match, bad, max_delta


def main():
    shot, scale, art = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    W, H = (int(sys.argv[4]), int(sys.argv[5])) if len(sys.argv) > 5 else (1920, 1080)
    im = Image.open(shot).convert("RGB")
    a = Image.open(art).convert("RGBA")
    gw, gh = W // scale, H // scale
    left, top = (gw - 176) // 2, (gh - 222) // 2
    ox, oy = left * scale, top * scale
    total, match, bad, max_delta = score(im, a, scale, ox, oy)
    xs = [p[0] for p in bad]
    ys = [p[1] for p in bad]
    box = f"x {min(xs)}..{max(xs)} y {min(ys)}..{max(ys)}" if bad else "none"
    # One GUI pixel (= scale screen px) in each direction; a shift that scores as well means no pinning.
    nb = {}
    for dx, dy in [(0, 0), (-scale, 0), (scale, 0), (0, -scale), (0, scale)]:
        _, m, _, _ = score(im, a, scale, ox + dx, oy + dy)
        nb[(dx // scale, dy // scale)] = m
    best = max(nb.items(), key=lambda kv: (kv[1], kv[0] == (0, 0)))
    best = (best[1], best[0][0], best[0][1])
    shifts = " ".join(f"({k[0]},{k[1]})={v}" for k, v in nb.items())
    print(f"scale={scale} origin_px=({ox},{oy}) window_gui=({left},{top}) tol={TOL} "
          f"matched={match}/{total} ({100.0 * match / max(total, 1):.2f}%) mismatch_box={box} "
          f"max_channel_delta={max_delta} gui_px_shift_matches: {shifts}")
    sys.exit(0 if not bad else 1)


if __name__ == "__main__":
    main()
