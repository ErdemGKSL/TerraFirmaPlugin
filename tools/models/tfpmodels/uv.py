# Licensed under the EUPL, Version 1.2.
"""Automatic box-UV layout and UV-template rendering.

Box UV footprint of a cube (w, h, d): width 2*(d+w), height d+h (Minecraft/Blockbench convention).
Mirrored pairs share one footprint (same uv_key): left/right legs, ears, horns.
"""
from __future__ import annotations

import math
import struct
import zlib

from .bbmodel import Model


def footprint(size: tuple[float, float, float]) -> tuple[int, int]:
    w, h, d = (max(1, math.ceil(v)) for v in size)
    return 2 * (d + w), d + h


def pack_box_uv(model: Model, max_width: int = 256) -> tuple[int, int]:
    """Shelf-pack every unique UV footprint, set cube.uv_offset, return (tex_w, tex_h) as powers of two."""
    cubes = [c for b in model.bones() for c in b.cubes]
    slots: dict[str, tuple[int, int]] = {}
    for c in cubes:
        key = c.uv_key or f"{id(c)}"
        fw, fh = footprint(c.size)
        prev = slots.get(key)
        slots[key] = (max(fw, prev[0]), max(fh, prev[1])) if prev else (fw, fh)
    order = sorted(slots.items(), key=lambda kv: (-kv[1][1], -kv[1][0], kv[0]))
    width = 16
    while width < max_width and width < max(fw for fw, _ in slots.values()):
        width *= 2
    for _ in range(6):                                   # grow width until the height fits within 2x width
        x = y = shelf_h = 0
        placed = {}
        for key, (fw, fh) in order:
            if x + fw > width:
                x, y, shelf_h = 0, y + shelf_h, 0
            placed[key] = (x, y)
            x += fw
            shelf_h = max(shelf_h, fh)
        height = y + shelf_h
        if height <= width * 2 or width >= max_width:
            break
        width *= 2
    tex_h = 16
    while tex_h < height:
        tex_h *= 2
    for c in cubes:
        c.uv_offset = placed[c.uv_key or f"{id(c)}"]
    model.texture_size = (width, tex_h)
    return width, tex_h


FACE_COLORS = {"up": (220, 220, 220), "down": (90, 90, 90), "north": (200, 80, 80), "south": (80, 160, 80),
               "east": (80, 110, 200), "west": (200, 170, 60)}


def box_faces(uv: tuple[int, int], size: tuple[float, float, float]) -> dict[str, tuple[int, int, int, int]]:
    """Face rectangles (x, y, w, h) inside a box-UV footprint."""
    w, h, d = (max(1, math.ceil(v)) for v in size)
    u, v = uv
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


def template_png(model: Model) -> bytes:
    """Colour-coded UV template (one flat colour per face direction) to hand to the image generator."""
    tw, th = model.texture_size
    px = [[(0, 0, 0, 0)] * tw for _ in range(th)]
    for b in model.bones():
        for c in b.cubes:
            for face, (x, y, w, h) in box_faces(c.uv_offset, c.size).items():
                r, g, bl = FACE_COLORS[face]
                for yy in range(y, min(y + h, th)):
                    for xx in range(x, min(x + w, tw)):
                        edge = xx in (x, x + w - 1) or yy in (y, y + h - 1)
                        px[yy][xx] = (r // 2, g // 2, bl // 2, 255) if edge else (r, g, bl, 255)
    return encode_png(px)


def encode_png(px: list[list[tuple[int, int, int, int]]]) -> bytes:
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(t: bytes, d: bytes) -> bytes:
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
