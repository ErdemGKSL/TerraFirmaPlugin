# Licensed under the EUPL, Version 1.2.
# Ported from TerraFirmaCraft (client/model/entity/*Model.java createBodyLayer) @ commit c3b4d9536b82e5a9746dd793c586720a74108057
"""Java `createBodyLayer` -> BetterModel .bbmodel (PAR-FAUNA-02), for the TFC models that ship no Blockbench file.

python3 -I tools/models/tfpmodels/species/java_layer.py <tfc-checkout> [--out DIR] [--only a,b]

Reads each model's `PartDefinition.addOrReplaceChild(name, CubeListBuilder.create().texOffs().addBox().mirror(), PartPose...)`
tree and writes the same rig as groups and box-UV cubes. Coordinates: the client renders models with scale(-1,-1,1) about
y = 24, so a Java value (x, y, z) becomes Blockbench (-x, 24-y, z); group rotation (rx, ry, rz) radians becomes
(-rx, -ry, rz) degrees; a part's pivot is the sum of its ancestors' offsets (Blockbench keeps child origins absolute and
unrotated, the same composition as Java). Box UV needs no change: Java's `right` face (-x) lands on Blockbench's east.
Then it adds `idle` and a diagonal-gait `walk` on the leg bones found by name, renames `head` to `h_head` (look direction).
"""
from __future__ import annotations

import argparse
import ast
import json
import math
import pathlib
import re
import sys

MODELS = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(MODELS))
from tfpmodels.bbmodel import Animation, Bone, Cube, Keyframe, Model  # noqa: E402
from tfpmodels.validate import check  # noqa: E402

ENTITY_DIR = "src/main/java/net/dries007/tfc/client/model/entity"
TEX_DIR = "src/main/resources/assets/tfc/textures/entity/animal"

# species key -> (Java class, texture file). Texture variants are PAR-FAUNA-03; this is the default one.
SPECIES: dict[str, tuple[str, str]] = {
    "alpaca": ("AlpacaModel", "alpaca_old.png"),
    "bison": ("BisonModel", "bison.png"),
    "chicken": ("TFCChickenModel", "chicken_old.png"),
    "cow": ("TFCCowModel", "cow_old.png"),
    "duck": ("DuckModel", "duck_old.png"),
    "grouse": ("GrouseModel", "grouse_male.png"),
    "jerboa": ("JerboaModel", "jerboa.png"),
    "lemming": ("LemmingModel", "lemming.png"),
    "musk_ox": ("MuskOxModel", "musk_ox_old.png"),
    "pheasant": ("PheasantModel", "pheasant_male.png"),
    "quail": ("QuailModel", "quail_old.png"),
    "sheep": ("TFCSheepModel", "sheep_old.png"),
    "turkey": ("TurkeyModel", "turkey_male.png"),
    "turtle": ("TFCTurtleModel", "turtle.png"),
    "wolf": ("TFCWolfModel", "wolf.png"),
    "yak": ("YakModel", "yak_old.png"),
}

_CHILD = re.compile(
    r"(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\(\s*\"([^\"]+)\"\s*,\s*CubeListBuilder\.create\(\)(.*?),\s*PartPose\.(offsetAndRotation|offset)\((.*?)\)\s*\)\s*;",
    re.S)
_CALL = re.compile(r"\.(texOffs|addBox|mirror)\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)")
_LAYER = re.compile(r"LayerDefinition\.create\(\s*\w+\s*,\s*(\d+)\s*,\s*(\d+)\s*\)")
_NUM = re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)[fFdD]\b")


def _eval(expr: str) -> float:
    e = _NUM.sub(r"\1", expr.strip())
    e = re.sub(r"\(\s*float\s*\)", "", e)
    e = e.replace("Math.PI", "pi").replace("Mth.PI", "pi").replace("(float)", "")
    e = re.sub(r"(?:Math|Mth)\.toRadians", "rad", e)
    tree = ast.parse(e, mode="eval")
    env = {"pi": math.pi, "rad": math.radians}

    def ev(n):
        if isinstance(n, ast.Expression):
            return ev(n.body)
        if isinstance(n, ast.Constant):
            return float(n.value)
        if isinstance(n, ast.UnaryOp):
            v = ev(n.operand)
            return -v if isinstance(n.op, ast.USub) else v
        if isinstance(n, ast.BinOp):
            a, b = ev(n.left), ev(n.right)
            return {ast.Add: a + b, ast.Sub: a - b, ast.Mult: a * b, ast.Div: a / b if b else 0.0}[type(n.op)]
        if isinstance(n, ast.Name):
            return env[n.id]
        if isinstance(n, ast.Call) and isinstance(n.func, ast.Name):
            return env[n.func.id](*[ev(a) for a in n.args])
        raise ValueError(f"unsupported expression: {expr}")
    return ev(tree)


def _args(text: str) -> list[str]:
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
            continue
        depth += ch == "("
        depth -= ch == ")"
        cur += ch
    if cur.strip():
        out.append(cur)
    return [a.strip() for a in out]


def snake(name: str) -> str:
    s = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name).lower()
    return re.sub(r"[^a-z0-9_]", "_", s)


class Part:
    def __init__(self, name, parent, pose, cubes):
        self.name, self.parent, self.pose, self.cubes, self.children = name, parent, pose, cubes, []


def parse_layer(source: str) -> tuple[Part, tuple[int, int]]:
    """The part tree of the first body layer in a Java model class and its texture size."""
    start = source.index("addOrReplaceChild")
    start = source.rfind("\n", 0, start)
    m = _LAYER.search(source, start)
    body = source[start:m.start()]
    size = (int(m.group(1)), int(m.group(2)))
    root = Part("root", None, (0, 0, 0, 0, 0, 0), [])
    vars_: dict[str, Part] = {}
    for mm in _CHILD.finditer(body):
        var, parent_var, name, chain, kind, pose_args = mm.groups()
        parent = vars_.get(parent_var, root)
        vals = [_eval(a) for a in _args(pose_args)]
        pose = tuple(vals + [0.0] * (6 - len(vals))) if kind == "offsetAndRotation" else tuple(vals + [0.0, 0.0, 0.0])
        cubes, tex, mirror = [], (0, 0), False
        for call, args in _CALL.findall(chain):
            a = _args(args)
            if call == "texOffs":
                tex = (int(_eval(a[0])), int(_eval(a[1])))
            elif call == "mirror":
                mirror = not a or a[0] != "false"
            else:
                nums = [_eval(x) for x in a[:6]]
                grow = 0.0
                rest = " ".join(a[6:])
                g = re.search(r"CubeDeformation\(([^)]*)\)", rest)
                if g:
                    grow = _eval(_args(g.group(1))[0])
                cubes.append((tex, nums, grow, mirror))
        part = Part(name, parent, pose, cubes)
        parent.children.append(part)
        vars_[var or name] = part
        vars_[name] = part
    return root, size


def to_bone(part: Part, parent_pivot: tuple[float, float, float], used: set[str]) -> Bone:
    px, py, pz = (parent_pivot[0] + part.pose[0], parent_pivot[1] + part.pose[1], parent_pivot[2] + part.pose[2])   # Java space
    pivot = (-px, 24 - py, pz)
    name = snake(part.name)
    name = "h_head" if name == "head" else name
    while name in used:
        name += "_"
    used.add(name)
    rot = tuple(round(v, 4) + 0.0 for v in (-math.degrees(part.pose[3]), -math.degrees(part.pose[4]), math.degrees(part.pose[5])))
    bone = Bone(name, tuple(round(v, 4) for v in pivot), rot)
    for i, ((u, v), (x, y, z, w, h, d), grow, mirror) in enumerate(part.cubes):
        frm = (pivot[0] - (x + w), pivot[1] - (y + h), pivot[2] + z)
        c = Cube(f"{name}_{i}", tuple(round(a, 4) for a in frm), tuple(round(frm[k] + (w, h, d)[k], 4) for k in range(3)),
                 inflate=grow, mirror_uv=mirror)
        c.uv_offset = (u, v)
        bone.cubes.append(c)
    bone.children = [to_bone(ch, (px, py, pz), used) for ch in part.children]
    return bone


# ---- animations ---------------------------------------------------------------------------------------------------

def _swing(length, amp, phase, steps=4):
    return [Keyframe(length * i / steps, "rotation", (round(amp * math.sin(2 * math.pi * (i / steps + phase)), 3), 0, 0), "catmullrom")
            for i in range(steps + 1)]


def classify_legs(names: list[str]) -> dict[str, float]:
    """bone -> gait phase. Diagonal pairs (front-left with hind-right) for four legs, alternating for two."""
    legs = [n for n in names if re.search(r"leg|foot|paw", n) and not re.search(r"upper|lower|2$|3$|toe|claw", n)]
    out: dict[str, float] = {}
    for n in legs:
        left = bool(re.search(r"(^|_)(l|left)(_|$)|left", n))
        front = bool(re.search(r"front|fore|^f", n))
        out[n] = 0.0 if (left == front) else 0.5
    return out


def animations(bone_names: list[str]) -> list[Animation]:
    legs = classify_legs(bone_names)
    walk = {n: _swing(1.0, 25.0, ph) for n, ph in legs.items()}
    idle = {}
    if "h_head" in bone_names:
        idle["h_head"] = _swing(4.0, 3.0, 0.0)
        walk["h_head"] = _swing(1.0, 3.0, 0.25)
    for n in bone_names:
        if n.startswith("tail") and "2" not in n:
            idle[n] = _swing(2.0, 6.0, 0.0)
    out = []
    if idle:
        out.append(Animation("idle", 4.0, tracks=idle))
    if walk:
        out.append(Animation("walk", 1.0, tracks=walk))
    return out


def build(tfc: pathlib.Path, key: str) -> Model:
    cls, tex = SPECIES[key]
    root, size = parse_layer((tfc / ENTITY_DIR / f"{cls}.java").read_text(encoding="utf-8"))
    used: set[str] = set()
    top = Bone("root", (0, 0, 0))
    used.add("root")
    top.children = [to_bone(c, (0, 0, 0), used) for c in root.children]
    png = (tfc / TEX_DIR / tex).read_bytes()
    model = Model(f"tfp_{key}", top, size, png)
    names = [b.name for b in model.bones()]
    model.animations = animations(names)
    return model


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("tfc")
    ap.add_argument("--out", default=str(pathlib.Path(__file__).resolve().parent / "out"))
    ap.add_argument("--only")
    a = ap.parse_args(argv)
    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    bad = 0
    for key in (a.only.split(",") if a.only else sorted(SPECIES)):
        model = build(pathlib.Path(a.tfc), key)
        data = model.to_json()
        problems = check(data, model_id=f"tfp_{key}", strict=False)
        (out / f"tfp_{key}.bbmodel").write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        print(("OK  " if not problems else "FAIL"), f"tfp_{key}", [x["name"] for x in data["animations"]], len(model.bones()), "bones")
        for p in problems:
            print("   -", p)
        bad += bool(problems)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
