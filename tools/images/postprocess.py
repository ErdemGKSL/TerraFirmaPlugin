#!/usr/bin/env python3
# Licensed under the EUPL, Version 1.2.
"""Turn a raw AI-generated image into a clean Minecraft pixel-art asset.

Steps: crop the transparent border (optional), downscale to the exact target size
(box filter when shrinking, nearest when growing), binarize alpha, and optionally snap
colors to a palette sampled from reference textures or to N colors.

Example:
  python3 tools/images/postprocess.py art/raw/pack-icon/1.png content/art/pack/pack.png \
      --size 32x32 --upscale 4 --palette-from path/to/tfc/textures/block/rock/raw/granite.png --colors 24
"""
import argparse
import sys
from pathlib import Path

from PIL import Image


def parse_size(text: str) -> tuple[int, int]:
    w, h = text.lower().split("x")
    return int(w), int(h)


def palette_image(refs: list[Path], max_colors: int) -> Image.Image:
    colors: dict[tuple[int, int, int], int] = {}
    for ref in refs:
        for count, rgba in Image.open(ref).convert("RGBA").getcolors(1 << 24) or []:
            if rgba[3] >= 128:
                colors[rgba[:3]] = colors.get(rgba[:3], 0) + count
    if not colors:
        sys.exit("no opaque colors found in --palette-from images")
    sample = Image.new("RGB", (len(colors), 1))
    sample.putdata(list(colors.keys()))
    return sample.quantize(colors=min(max_colors, len(colors), 256), method=Image.Quantize.MEDIANCUT)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("input", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--size", required=True, type=parse_size, help="pixel-art size, e.g. 16x16")
    p.add_argument("--upscale", type=int, default=1, help="integer nearest-neighbour upscale after processing")
    p.add_argument("--trim", action="store_true", help="crop fully transparent borders before resizing")
    p.add_argument("--alpha-threshold", type=int, default=128, help="alpha below this becomes 0, else 255")
    p.add_argument("--translucent", action="store_true", help="keep alpha as-is (skip binarization)")
    p.add_argument("--palette-from", type=Path, nargs="*", default=[], help="reference PNGs to sample a palette from")
    p.add_argument("--colors", type=int, default=0, help="limit to N colors (with or without --palette-from)")
    args = p.parse_args()

    img = Image.open(args.input).convert("RGBA")
    if args.trim and (bbox := img.getbbox()):
        img = img.crop(bbox)

    w, h = args.size
    shrinking = img.width >= w and img.height >= h
    img = img.resize((w, h), Image.Resampling.BOX if shrinking else Image.Resampling.NEAREST)

    alpha = img.getchannel("A")
    if not args.translucent:
        alpha = alpha.point(lambda a: 255 if a >= args.alpha_threshold else 0)

    rgb = img.convert("RGB")
    if args.palette_from:
        rgb = rgb.quantize(palette=palette_image(args.palette_from, args.colors or 256), dither=Image.Dither.NONE).convert("RGB")
    elif args.colors:
        rgb = rgb.quantize(colors=args.colors, dither=Image.Dither.NONE).convert("RGB")

    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    if args.upscale > 1:
        out = out.resize((w * args.upscale, h * args.upscale), Image.Resampling.NEAREST)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    out.save(args.output)
    print(f"wrote {args.output} ({out.width}x{out.height})")


if __name__ == "__main__":
    main()
