#!/usr/bin/env python3
"""M0-BM-01 spike-local workaround (NOT a production tool).

tools/models/tfpmodels/bbmodel.py writes every face as uv [0,0,0,0] for box_uv cubes. Blockbench recomputes
those on load, but BetterModel reads the stored values, so the model renders invisible (checked in-game).
This fills the per-face rectangles from uv_offset and the cube size, using the layout Blockbench itself
wrote for TFC's Deer-Bedrock.bbmodel and Bear-Bedrock.bbmodel (120/120 faces match).

Usage: fill_box_uv.py <in.bbmodel> <out.bbmodel>
"""
import json
import sys


def box_faces(frm, to, uv):
    w = to[0] - frm[0]
    h = to[1] - frm[1]
    d = to[2] - frm[2]
    u, v = uv
    return {
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
    }


def main(src, dst):
    d = json.load(open(src, encoding="utf-8"))
    filled = 0
    for e in d.get("elements", []):
        if "faces" not in e or not e.get("box_uv"):
            continue
        exp = box_faces(e["from"], e["to"], e.get("uv_offset", [0, 0]))
        for name, face in e["faces"].items():
            if face.get("uv") == [0, 0, 0, 0] and name in exp:
                face["uv"] = exp[name]
                filled += 1
    json.dump(d, open(dst, "w", encoding="utf-8"), indent=1)
    print(f"filled {filled} face UVs -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
