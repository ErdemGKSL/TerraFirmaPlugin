# Licensed under the EUPL, Version 1.2.
"""Merge TFC Java `AnimationDefinition`s into a .bbmodel under BetterModel animation names.

python3 -I tools/models/tfc_anim/convert.py <in.bbmodel> <Model.java>[,<More.java>] <out.bbmodel> JAVA_CONST=bm_name [...]

Axis rule (verified 2026-10-16 against TFC models that ship both forms; see the model-authoring skill):
  * .bbmodel format_version < 5  : Java values == stored Blockbench values (Deer, Lion, Crocodile: 233 comparisons, 0 conflicts)
  * .bbmodel format_version >= 5 : negate rotation x, rotation y, position x (Mongoose: 310 comparisons, 0 conflicts)
Scale is never flipped. Bone names in Java must equal group names in the .bbmodel (reported if not).
"""
from __future__ import annotations

import json
import pathlib
import sys
import uuid

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from parse_java_anims import parse  # noqa: E402

INTERP = {"linear": "linear", "catmullrom": "catmullrom"}


def flips_for(format_version: str) -> dict[str, tuple[int, int, int]]:
    major = int(str(format_version).split(".")[0] or 4)
    if major >= 5:
        return {"rotation": (-1, -1, 1), "position": (-1, 1, 1), "scale": (1, 1, 1)}
    return {"rotation": (1, 1, 1), "position": (1, 1, 1), "scale": (1, 1, 1)}


def bone_ids(bb: dict) -> dict[str, str]:
    """Group name -> uuid. Format 5 keeps groups top-level; format 4.x stores them inline in the outliner."""
    ids = {g["name"]: g["uuid"] for g in bb.get("groups", [])}

    def walk(nodes):
        for n in nodes:
            if isinstance(n, dict):
                if "name" in n and "uuid" in n:
                    ids.setdefault(n["name"], n["uuid"])
                walk(n.get("children", []))
    walk(bb.get("outliner", []))
    return ids


def convert(bb: dict, java_anims: dict, mapping: dict[str, str]) -> tuple[dict, list[str]]:
    warnings = []
    flips = flips_for(bb["meta"].get("format_version", "4.0"))
    group_ids = bone_ids(bb)
    by_name = {a["name"]: a for a in bb.get("animations", [])}
    for const, bm_name in mapping.items():
        if const not in java_anims:
            warnings.append(f"{const}: not found in Java sources")
            continue
        ja = java_anims[const]
        animators = {}
        for bone, channels in ja["bones"].items():
            if bone not in group_ids:
                warnings.append(f"{const}: bone '{bone}' not in .bbmodel groups, skipped")
                continue
            kfs = []
            for channel, frames in channels.items():
                fx, fy, fz = flips[channel]
                for t, (x, y, z), interp in frames:
                    kfs.append({"channel": channel, "data_points": [{"x": str(round(fx * x, 4)), "y": str(round(fy * y, 4)), "z": str(round(fz * z, 4))}],
                                "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{bm_name}/{bone}/{channel}/{t}")), "time": t,
                                "color": -1, "interpolation": INTERP.get(interp, "linear")})
            animators[group_ids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
        anim = {"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"anim/{bm_name}")), "name": bm_name,
                "loop": "loop" if ja["loop"] else "once", "override": False, "length": ja["length"], "snapping": 24,
                "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": animators}
        by_name[bm_name] = anim                       # replaces an existing animation of the same name
    bb["animations"] = list(by_name.values())
    return bb, warnings


def main(argv: list[str]) -> int:
    src, javas, out, *pairs = argv[1:]
    java_anims = {}
    for j in javas.split(","):
        java_anims.update(parse(pathlib.Path(j).read_text(encoding="utf-8")))
    mapping = dict(p.split("=", 1) for p in pairs)
    bb = json.loads(pathlib.Path(src).read_text(encoding="utf-8"))
    bb, warnings = convert(bb, java_anims, mapping)
    pathlib.Path(out).write_text(json.dumps(bb, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {out}: animations = {[a['name'] for a in bb['animations']]}")
    for w in warnings:
        print("  !", w)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
