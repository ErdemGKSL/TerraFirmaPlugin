# Licensed under the EUPL, Version 1.2.
"""Parametric rigs with standard bone names.

Bone naming contract (BetterModel + our animation library rely on it):
  root
    body                       torso; hump/wool/saddle parts attach here
      neck -> h_head           h_ prefix: BetterModel makes the head follow look direction
        h_head: ear_l/ear_r, horn_l/horn_r, snout, ... (extras)
      leg_fl, leg_fr, leg_bl, leg_br   (quadruped)   | leg_l, leg_r, wing_l, wing_r (bird)
      tail                     optional
  fish: root -> body -> h_head, fin_*, tail -> tail_fin
Left = +x (model faces -z/north), every *_l part has a mirrored *_r twin sharing UV.
"""
from __future__ import annotations

from .bbmodel import Bone, Cube


def _box(name, center_xz, bottom_y, size, inflate=0.0, uv_key=None, mirror=False):
    (cx, cz), (w, h, d) = center_xz, size
    return Cube(name, (cx - w / 2, bottom_y, cz - d / 2), (cx + w / 2, bottom_y + h, cz + d / 2),
                inflate=inflate, uv_key=uv_key, mirror_uv=mirror)


def _pair(name, x, *, make):
    """Mirrored left/right parts: make(side_name, x, mirrored) -> Bone."""
    return [make(f"{name}_l", +x, False), make(f"{name}_r", -x, True)]


def _extras(parent_name: str, parent_pivot, extras: list[dict]) -> list[Bone]:
    """Species extras: {"attach": "h_head"|"body", "name": "horn", "mirror": true, "offset": [x,y,z], "size": [w,h,d], "rotation": [x,y,z]}
    offset = position of the part's bottom-centre relative to the attach bone's pivot (x = distance from the centre line)."""
    bones = []
    for e in extras:
        if e.get("attach", "body") != parent_name:
            continue
        ox, oy, oz = (e["offset"][i] + (parent_pivot[i] if i else 0) for i in range(3))
        w, h, d = e["size"]

        def make(n, x, mirrored, e=e, oy=oy, oz=oz, w=w, h=h, d=d):
            return Bone(n, (x, oy, oz), tuple(e.get("rotation", (0, 0, 0))) if not mirrored else
                        (e.get("rotation", (0, 0, 0))[0], -e.get("rotation", (0, 0, 0))[1], -e.get("rotation", (0, 0, 0))[2]),
                        cubes=[_box(n, (x, oz), oy, (w, h, d), uv_key=e["name"], mirror=mirrored)])
        bones += _pair(e["name"], ox, make=make) if e.get("mirror") else [make(e["name"], ox, False)]
    return bones


def quadruped(p: dict) -> Bone:
    """p: body{w,h,d,y}, neck{w,h,d,angle}, head{w,h,d}, snout?{w,h,d}, leg{w,h,d,inset_x,inset_z}, tail?{w,h,d,angle}, ear?{...}, extras[]"""
    leg, body = p["leg"], p["body"]
    body_bottom = leg["h"]
    by = body_bottom + body["h"] / 2
    half_d = body["d"] / 2

    def leg_bone(name, x, z, mirrored, key):
        return Bone(name, (x, body_bottom, z), cubes=[_box(name, (x, z), 0, (leg["w"], leg["h"], leg["d"]), uv_key=key, mirror=mirrored)])

    lx = body["w"] / 2 - leg["inset_x"] - leg["w"] / 2
    fz, bz = -half_d + leg["inset_z"] + leg["d"] / 2, half_d - leg["inset_z"] - leg["d"] / 2
    legs = [leg_bone("leg_fl", +lx, fz, False, "leg_f"), leg_bone("leg_fr", -lx, fz, True, "leg_f"),
            leg_bone("leg_bl", +lx, bz, False, "leg_b"), leg_bone("leg_br", -lx, bz, True, "leg_b")]

    head = p["head"]
    neck = p.get("neck", {"w": 0, "h": 0, "d": 0, "angle": 0})
    neck_pivot = (0, body_bottom + body["h"] * 0.8, -half_d)
    head_pivot = (0, neck_pivot[1] + neck["h"], neck_pivot[2] - neck["d"] / 2)
    head_cubes = [_box("head", (0, head_pivot[2] - head["d"] / 2), head_pivot[1] - head["h"] / 2, (head["w"], head["h"], head["d"]))]
    if "snout" in p:
        s = p["snout"]
        head_cubes.append(_box("snout", (0, head_pivot[2] - head["d"] - s["d"] / 2), head_pivot[1] - head["h"] / 2, (s["w"], s["h"], s["d"])))
    head_children = []
    if "ear" in p:
        e = p["ear"]
        head_children += _pair("ear", head["w"] / 2, make=lambda n, x, m: Bone(
            n, (x, head_pivot[1] + head["h"] / 2, head_pivot[2] - head["d"] / 2),
            cubes=[_box(n, (x + (e["w"] / 2 if x > 0 else -e["w"] / 2), head_pivot[2] - head["d"] / 2), head_pivot[1] + head["h"] / 2 - 1,
                        (e["w"], e["h"], e["d"]), uv_key="ear", mirror=m)]))
    head_children += _extras("h_head", head_pivot, p.get("extras", []))
    h_head = Bone("h_head", head_pivot, cubes=head_cubes, children=head_children)
    neck_bone = Bone("neck", neck_pivot, (neck.get("angle", 0), 0, 0),
                     cubes=[_box("neck", (0, neck_pivot[2] - neck["d"] / 2), neck_pivot[1], (neck["w"], neck["h"], neck["d"]))] if neck["h"] else [],
                     children=[h_head])

    body_children = [neck_bone, *legs]
    if "tail" in p:
        t = p["tail"]
        body_children.append(Bone("tail", (0, by + body["h"] / 2 - 1, half_d), (t.get("angle", 30), 0, 0),
                                  cubes=[_box("tail", (0, half_d + t["d"] / 2), by + body["h"] / 2 - 1 - t["h"], (t["w"], t["h"], t["d"]))]))
    body_children += _extras("body", (0, by, 0), p.get("extras", []))
    body_bone = Bone("body", (0, by, 0), cubes=[_box("body", (0, 0), body_bottom, (body["w"], body["h"], body["d"]))], children=body_children)
    return Bone("root", (0, 0, 0), children=[body_bone])


RIGS = {"quadruped": quadruped}
