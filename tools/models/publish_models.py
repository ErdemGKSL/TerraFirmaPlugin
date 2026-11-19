# Licensed under the EUPL, Version 1.2.
# Ported from TerraFirmaCraft (client/render/entity/*Renderer.java, client/model/entity/*Model.java setupAnim, assets/tfc/textures/entity/animal) @ commit c3b4d9536b82e5a9746dd793c586720a74108057
"""Publish the imported TFC models with their texture and gender variants (PAR-FAUNA-03).

python3 -I tools/models/publish_models.py <tfc-checkout>

Reads tools/models/imported/*.bbmodel (PAR-FAUNA-01) and tools/models/tfpmodels/species/out/*.bbmodel (PAR-FAUNA-02) and writes
  * integration/bettermodel/src/main/resources/bettermodel/models/<id>.bbmodel + index.txt  (what BetterModel loads from the jar)
  * core/src/main/resources/dev/tfp/core/animal/model_variants.tsv                           (species, gender, stage -> model id)
A variant is the model with its embedded texture swapped for TFC's texture of that stage/gender and, for species whose model
shows parts by gender (TFC `displayMaleCharacteristics` / `displayFemaleCharacteristics`), with the other gender's parts removed:
BetterModel has no texture swap or part-visibility call that survives a reload, so each look is its own model.
"""
from __future__ import annotations

import base64
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
TEX = "src/main/resources/assets/tfc/textures/entity/animal/"
MODELS_OUT = REPO / "integration/bettermodel/src/main/resources/bettermodel/models"
TSV_OUT = REPO / "core/src/main/resources/dev/tfp/core/animal/model_variants.tsv"

KEEP = "tfp_example_bovine"   # the M1 placeholder, still used by tests; not generated here

# species -> texture files by role (TFC names without .png). young = the default look; old = the old-age stage;
# male_young / male_old = the male's own textures; baby = the child's. A species with no entry uses <species>.png if it exists.
TEXTURES: dict[str, dict[str, str]] = {
    "alpaca": {"young": "alpaca_young", "old": "alpaca_old"},
    "bactrian_camel": {"young": "bactrian_camel_young", "old": "bactrian_camel_old"},
    "chicken": {"young": "chicken_young", "old": "chicken_old", "male_young": "rooster_young", "male_old": "rooster_old", "baby": "chick"},
    "cow": {"young": "cow_young", "old": "cow_old"},
    "duck": {"young": "duck_young", "old": "duck_old", "male_young": "drake_young", "male_old": "drake_old", "baby": "duckling"},
    "musk_ox": {"young": "musk_ox_young", "old": "musk_ox_old"},
    "quail": {"young": "quail_young", "old": "quail_old", "male_young": "quail_male_young", "male_old": "quail_male_old", "baby": "quail_chick"},
    "sheep": {"young": "sheep_young", "old": "sheep_old"},
    "yak": {"young": "yak_young", "old": "yak_old"},
    "grouse": {"young": "grouse_female", "male_young": "grouse_male"},
    "pheasant": {"young": "pheasant_female", "male_young": "pheasant_male", "baby": "pheasant_chick"},
    "peafowl": {"young": "peafowl_female", "male_young": "peafowl_male", "baby": "peafowl_chick"},
    "turkey": {"young": "turkey_female", "male_young": "turkey_male"},
    "deer": {"young": "deer", "baby": "deer_fawn"},
    "penguin": {"young": "penguin", "baby": "penguin_young"},
    "rat": {"young": "rat", "baby": "rat_young"},
    "leopard_seal": {"young": "seal"},
    "turtle": {"young": "turtle"},
}
# Parts that exist only for one gender (TFC setupAnim visibility): bone-name tokens. Female looks drop MALE_ONLY, male looks FEMALE_ONLY.
MALE_ONLY = {"cow": ("horn_",), "sheep": ("left_horn", "right_horn"), "musk_ox": ("horn_",), "deer": ("antler",), "moose": ("antler",)}
FEMALE_ONLY = {"cow": ("udder",), "yak": ("udder",)}


def drop_bones(bb: dict, tokens: tuple[str, ...]) -> None:
    """Removes every group whose name contains a token, with its cubes and animation channels (format 4 inline and 5 flat)."""
    doomed_groups: set[str] = set()
    doomed_cubes: set[str] = set()

    def walk(nodes, inside):
        out = []
        for n in nodes:
            if isinstance(n, str):
                if inside:
                    doomed_cubes.add(n)
                else:
                    out.append(n)
                continue
            name = n.get("name") or next((g["name"] for g in bb.get("groups", []) if g.get("uuid") == n.get("uuid")), "")
            hit = inside or any(t in name for t in tokens)
            if hit:
                doomed_groups.add(n["uuid"])
            kids = walk(n.get("children", []), hit)
            if not hit:
                n = dict(n, children=kids)
                out.append(n)
        return out
    bb["outliner"] = walk(bb["outliner"], False)
    bb["elements"] = [e for e in bb["elements"] if e["uuid"] not in doomed_cubes]
    if "groups" in bb:
        bb["groups"] = [g for g in bb["groups"] if g["uuid"] not in doomed_groups]
    for a in bb.get("animations", []):
        a["animators"] = {u: v for u, v in a.get("animators", {}).items() if u not in doomed_groups}
    bb["animations"] = [a for a in bb.get("animations", []) if a["animators"]]


def png_size(data: bytes) -> tuple[int, int]:
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def with_texture(bb: dict, png: bytes | None) -> dict:
    out = json.loads(json.dumps(bb))
    if png is not None and out.get("textures"):
        t = out["textures"][0]
        t["source"] = "data:image/png;base64," + base64.b64encode(png).decode()
        w, h = png_size(png)
        t["width"], t["height"], t["uv_width"], t["uv_height"] = w, h, w, h
        # other textures (TFC's bedrock models list two) stay, so every face's texture index keeps pointing at a texture
    return out


def species_models() -> dict[str, pathlib.Path]:
    out = {}
    for d in (HERE / "imported", HERE / "tfpmodels/species/out"):
        for p in sorted(d.glob("tfp_*.bbmodel")):
            out[p.stem[4:]] = p
    return out


def main(argv: list[str]) -> int:
    tfc = pathlib.Path(argv[0])
    MODELS_OUT.mkdir(parents=True, exist_ok=True)
    rows = ["# species\tgender\tstage\tmodel (PAR-FAUNA-03; generated by tools/models/publish_models.py, do not edit)"]
    ids: list[str] = []
    written: dict[str, bytes] = {}
    for species, path in sorted(species_models().items()):
        base = json.loads(path.read_text(encoding="utf-8"))
        roles = dict(TEXTURES.get(species, {}))
        if not roles and (tfc / TEX / f"{species}.png").exists():
            roles = {"young": species}
        pngs = {r: (tfc / TEX / f"{n}.png").read_bytes() for r, n in roles.items() if (tfc / TEX / f"{n}.png").exists()}
        gendered = species in MALE_ONLY or species in FEMALE_ONLY
        for male in (False, True):
            for stage in ("child", "adult", "old"):
                if stage == "child" and "baby" in pngs:
                    key = "baby"
                elif stage == "old":
                    key = ("male_old" if male and "male_old" in pngs else "old") if ("old" in pngs or (male and "male_old" in pngs)) else "young"
                else:
                    key = "male_young" if male and "male_young" in pngs else "young"
                if key == "male_young" or (key == "male_old"):
                    tex_suffix = "_male" if key == "male_young" else "_male_old"
                else:
                    tex_suffix = "" if key == "young" else f"_{key}"
                png = pngs.get(key) or pngs.get("young")
                shape = "_m" if (gendered and male) else ""
                mid = f"tfp_{species}{tex_suffix}{shape}"
                if mid not in written:
                    bb = with_texture(base, png)
                    if gendered:
                        drop_bones(bb, (FEMALE_ONLY.get(species, ()) if male else MALE_ONLY.get(species, ())))
                    bb["name"] = mid
                    bb.setdefault("meta", {})["model_name"] = mid
                    data = (json.dumps(bb, indent=1) + "\n").encode()
                    written[mid] = data
                    ids.append(mid)
                rows.append(f"{species}\t{'male' if male else 'female'}\t{stage}\t{mid}")
    for old in MODELS_OUT.glob("tfp_*.bbmodel"):
        if old.stem not in written and old.stem != KEEP:
            old.unlink()
    for mid, data in written.items():
        (MODELS_OUT / f"{mid}.bbmodel").write_bytes(data)
    (MODELS_OUT / "index.txt").write_text("# generated by tools/models/publish_models.py (PAR-FAUNA-03)\n" + "\n".join(sorted(ids + [KEEP])) + "\n", encoding="utf-8")
    TSV_OUT.parent.mkdir(parents=True, exist_ok=True)
    TSV_OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(ids)} models, {len(rows) - 1} variant rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
