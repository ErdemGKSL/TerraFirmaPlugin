# Licensed under the EUPL, Version 1.2.
"""python3 -m tfpmodels.generate species/<id>.json [out_dir]

Builds <id>.bbmodel (+ <id>_uv_template.png) from a species parameter file:
  {"id": "tfp_example_bovine", "rig": "quadruped", ...rig params..., "anim": {...}, "extras": [...]}
"""
from __future__ import annotations

import json
import pathlib
import sys

from .anims import LIBRARY
from .bbmodel import Model
from .rigs import RIGS
from .uv import pack_box_uv, template_png
from .validate import check


def build(params: dict) -> Model:
    root = RIGS[params["rig"]](params)
    model = Model(params["id"], root)
    pack_box_uv(model)
    model.texture_png = template_png(model)          # placeholder texture = UV template until the real one arrives
    model.animations = [make(params) for make in LIBRARY.get(params["rig"], [])]
    return model


def main(argv: list[str]) -> int:
    src = pathlib.Path(argv[1])
    out = pathlib.Path(argv[2] if len(argv) > 2 else "out")
    out.mkdir(parents=True, exist_ok=True)
    params = json.loads(src.read_text(encoding="utf-8"))
    model = build(params)
    target = out / f"{params['id']}.bbmodel"
    model.write(str(target))
    (out / f"{params['id']}_uv_template.png").write_bytes(template_png(model))
    problems = check(json.loads(target.read_text(encoding="utf-8")), params["id"])
    print(f"wrote {target} ({len(model.bones())} bones, texture {model.texture_size[0]}x{model.texture_size[1]})")
    for p in problems:
        print("  -", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
