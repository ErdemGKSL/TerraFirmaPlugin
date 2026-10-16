# Licensed under the EUPL, Version 1.2.
"""Minimal, deterministic Blockbench .bbmodel writer for generated entity models.

Coordinates are Blockbench model space in pixels: +y up, the model faces north (-z).
Cubes are absolute (from/to); bones (groups) have an absolute pivot (origin).
Box UV is laid out automatically by `pack_box_uv` (see uv.py).
"""
from __future__ import annotations

import base64
import json
import uuid
from dataclasses import dataclass, field
from typing import Iterable

FORMAT_VERSION = "5.0"          # Blockbench 5 axis conventions (see tfc_anim/convert.py for the sign rule)
_NS = uuid.UUID("6f2a3c1e-7b7d-4c55-9d7e-3c1a0b8e4f21")


def stable_uuid(*parts: str) -> str:
    """Deterministic UUIDs so regenerated files diff cleanly."""
    return str(uuid.uuid5(_NS, "/".join(parts)))


@dataclass
class Cube:
    name: str
    frm: tuple[float, float, float]
    to: tuple[float, float, float]
    inflate: float = 0.0
    mirror_uv: bool = False
    uv_offset: tuple[int, int] | None = None   # filled by the UV packer
    uv_key: str | None = None                  # cubes sharing a key share UV space (mirrored pairs)

    @property
    def size(self) -> tuple[float, float, float]:
        return tuple(abs(self.to[i] - self.frm[i]) for i in range(3))


@dataclass
class Bone:
    name: str
    pivot: tuple[float, float, float]
    rotation: tuple[float, float, float] = (0.0, 0.0, 0.0)
    cubes: list[Cube] = field(default_factory=list)
    children: list["Bone"] = field(default_factory=list)

    def walk(self) -> Iterable["Bone"]:
        yield self
        for c in self.children:
            yield from c.walk()


@dataclass
class Keyframe:
    time: float
    channel: str                         # rotation | position | scale
    value: tuple[str | float, str | float, str | float]   # numbers or Molang strings
    interpolation: str = "linear"


@dataclass
class Animation:
    name: str                            # BetterModel names: idle, walk, run, eat, sleep, attack, swim, ...
    length: float
    loop: str = "loop"                   # loop | once | hold
    override: bool = False
    tracks: dict[str, list[Keyframe]] = field(default_factory=dict)   # bone name -> keyframes


@dataclass
class Model:
    name: str
    root: Bone
    texture_size: tuple[int, int] = (64, 64)
    texture_png: bytes | None = None
    animations: list[Animation] = field(default_factory=list)

    def bones(self) -> list[Bone]:
        return list(self.root.walk())

    def to_json(self) -> dict:
        name = self.name
        elements, groups = [], []

        def cube_json(bone: Bone, i: int, c: Cube) -> dict:
            return {
                "name": c.name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                "allow_mirror_modeling": True, "from": list(c.frm), "to": list(c.to), "autouv": 0, "color": 0,
                "inflate": c.inflate, "origin": list(bone.pivot), "uv_offset": list(c.uv_offset or (0, 0)),
                "mirror_uv": c.mirror_uv,
                "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0} for f in ("north", "east", "south", "west", "up", "down")},
                "type": "cube", "uuid": stable_uuid(name, "cube", bone.name, str(i), c.name),
            }

        def outline(bone: Bone) -> dict:
            gid = stable_uuid(name, "bone", bone.name)
            groups.append({"name": bone.name, "origin": list(bone.pivot), "color": 0, "rotation": list(bone.rotation),
                           "uuid": gid, "export": True, "mirror_uv": False, "isOpen": False, "locked": False,
                           "visibility": True, "autouv": 0})
            children = []
            for i, c in enumerate(bone.cubes):
                cj = cube_json(bone, i, c)
                elements.append(cj)
                children.append(cj["uuid"])
            children += [outline(ch) for ch in bone.children]
            return {"uuid": gid, "isOpen": False, "children": children}

        root = outline(self.root)
        bone_ids = {g["name"]: g["uuid"] for g in groups}
        anims = []
        for a in self.animations:
            animators = {}
            for bone, kfs in a.tracks.items():
                animators[bone_ids[bone]] = {"name": bone, "type": "bone", "keyframes": [
                    {"channel": k.channel, "data_points": [{"x": str(k.value[0]), "y": str(k.value[1]), "z": str(k.value[2])}],
                     "uuid": stable_uuid(name, a.name, bone, k.channel, f"{k.time:.4f}"), "time": round(k.time, 4),
                     "color": -1, "interpolation": k.interpolation} for k in kfs]}
            anims.append({"uuid": stable_uuid(name, "anim", a.name), "name": a.name, "loop": a.loop, "override": a.override,
                          "length": a.length, "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "",
                          "start_delay": "", "loop_delay": "", "animators": animators})
        w, h = self.texture_size
        textures = []
        if self.texture_png:
            textures.append({"path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0", "width": w, "height": h,
                             "uv_width": w, "uv_height": h, "particle": False, "use_as_default": False, "layers_enabled": False,
                             "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                             "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                             "uuid": stable_uuid(name, "texture"),
                             "source": "data:image/png;base64," + base64.b64encode(self.texture_png).decode()})
        return {"meta": {"format_version": FORMAT_VERSION, "model_format": "free", "box_uv": True},
                "name": name, "model_identifier": "", "visible_box": [1, 1, 0], "variable_placeholders": "",
                "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
                "resolution": {"width": w, "height": h}, "elements": elements, "groups": groups, "outliner": [root],
                "textures": textures, "animations": anims}

    def write(self, path: str) -> None:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.to_json(), f, indent=1, sort_keys=False)
            f.write("\n")
