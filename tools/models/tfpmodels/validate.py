# Licensed under the EUPL, Version 1.2.
"""Machine checks for .bbmodel files (ours or TFC's) before render/review. Returns a list of problems."""
from __future__ import annotations

import json
import re
import sys

BM_ID = re.compile(r"^[a-z0-9_.]+$")
BM_TAGS = {"h", "hi", "b", "ob", "p", "sp", "tag", "mtag", "ptag", "glow", "li", "pli", "ri", "pri"}
HITBOX_TAGS = {"b", "ob", "p", "sp"}


def check(model: dict, model_id: str | None = None, strict: bool = True) -> list[str]:
    """strict=True for our generated models. strict=False for imported (TFC) models: UV layout checks are skipped,
    because TFC models deliberately reuse/overlap UV space."""
    errs = []
    if model_id is not None and not BM_ID.match(model_id):
        errs.append(f"model id '{model_id}' must match [a-z0-9_.]+ (BetterModel)")
    names = [g["name"] for g in model.get("groups", [])]

    def walk(nodes):                      # format 4.x: groups live inline in the outliner
        for n in nodes:
            if isinstance(n, dict):
                if "name" in n and not model.get("groups"):
                    names.append(n["name"])
                walk(n.get("children", []))
    walk(model.get("outliner", []))
    dup = {n for n in names if names.count(n) > 1}
    if dup:
        errs.append(f"duplicate bone names (animations bind by name): {sorted(dup)}")
    for n in names:
        first = n.lower().split("_")[0]
        if first in HITBOX_TAGS or n.lower() == "hitbox":
            errs.append(f"bone '{n}' creates a BetterModel hitbox (2 server entities each): rename it")
    for item in model.get("outliner", []):
        if isinstance(item, str):
            errs.append("root-level cube outside any group (BetterModel won't render it)")
    rw, rh = model["resolution"]["width"], model["resolution"]["height"]
    regions = []
    for e in model.get("elements", []):
        if e.get("type", "cube") != "cube":
            errs.append(f"unsupported element type {e.get('type')} ({e.get('name')})")
            continue
        size = [abs(e["to"][i] - e["from"][i]) for i in range(3)]
        if e.get("box_uv"):
            u, v = e.get("uv_offset", [0, 0])
            w, h, d = (max(1, int(-(-s // 1))) for s in size)
            fw, fh = 2 * (d + w), d + h
            if strict and (u < 0 or v < 0 or u + fw > rw or v + fh > rh):
                errs.append(f"cube '{e['name']}' box UV {u},{v} {fw}x{fh} outside texture {rw}x{rh}")
            regions.append((e["name"], u, v, fw, fh, e.get("mirror_uv", False)))
        if max(size) > 32:
            errs.append(f"cube '{e['name']}' is {size}: very large bones lose precision in BetterModel")
    seen = {}
    for name, u, v, fw, fh, _m in (regions if strict else []):          # shared UV is allowed only for identical footprints (mirrored pairs)
        key = (u, v)
        if key in seen and seen[key] != (fw, fh):
            errs.append(f"cubes '{name}' and another overlap UV at {u},{v} with different sizes")
        seen[key] = (fw, fh)
    for i, a in enumerate(regions if strict else []):
        for b in regions[i + 1:]:
            if (a[1], a[2]) == (b[1], b[2]):
                continue
            if a[1] < b[1] + b[3] and b[1] < a[1] + a[3] and a[2] < b[2] + b[4] and b[2] < a[2] + a[4]:
                errs.append(f"UV overlap: '{a[0]}' and '{b[0]}'")
    bone_names = set(names)
    for an in model.get("animations", []):
        for animator in an.get("animators", {}).values():
            if animator.get("type", "bone") == "bone" and animator["name"] not in bone_names:
                errs.append(f"animation '{an['name']}' targets missing bone '{animator['name']}'")
    for t in model.get("textures", []):
        if not str(t.get("source", "")).startswith("data:image/png;base64,"):
            errs.append(f"texture '{t.get('name')}' is not embedded (BetterModel needs embedded textures)")
    return errs


if __name__ == "__main__":
    bad = 0
    args = sys.argv[1:]
    strict = "--lenient" not in args
    for path in [a for a in args if a != "--lenient"]:
        problems = check(json.load(open(path, encoding="utf-8")), strict=strict)
        print(f"{'OK  ' if not problems else 'FAIL'} {path}")
        for p in problems:
            print("     -", p)
        bad += bool(problems)
    sys.exit(1 if bad else 0)
