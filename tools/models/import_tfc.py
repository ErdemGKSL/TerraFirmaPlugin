# Licensed under the EUPL, Version 1.2.
# Ported from TerraFirmaCraft (entity_models/*.bbmodel, client/model/entity/*.java) @ commit c3b4d9536b82e5a9746dd793c586720a74108057
"""Import TFC's entity .bbmodels as BetterModel models (PAR-FAUNA-01).

python3 -I tools/models/import_tfc.py <tfc-checkout> [--out tools/models/imported] [--also content/generated/content/bettermodel]

For every species in SPECIES: load TFC's .bbmodel, replace its animations by TFC's Java AnimationDefinitions (verified axis
rule, see tfc_anim/convert.py) under BetterModel names (idle, walk, run, attack, sleep, swim, eat, ...), drop the stale
Blockbench animations that Java does not replace unless they classify to a free BetterModel name, drop channels that target
missing bones, set a `tfp_<species>` name and write <out>/tfp_<species>.bbmodel. Every output is validated with `--lenient`
rules and the run fails on any problem. Output is deterministic (same inputs, same bytes).
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "tfc_anim"))
sys.path.insert(0, str(HERE))
from convert import bone_ids, convert  # noqa: E402
from parse_java_anims import parse  # noqa: E402
from tfpmodels.validate import check  # noqa: E402

ENTITY_DIR = "src/main/java/net/dries007/tfc/client/model/entity"
ANIM_DIR = "src/main/java/net/dries007/tfc/client/animation"
MODEL_DIR = "resources/entity_models"

# species key -> (bbmodel file, Java model class (None: no Java definitions), extra Java files)
SPECIES: dict[str, tuple[str, str | None, tuple[str, ...]]] = {
    "bactrian_camel": ("Bactrian_camel.bbmodel", "BactrianCamelModel", (f"{ANIM_DIR}/BactrianCamelAnimation.java",)),
    "black_bear": ("Bear-Bedrock.bbmodel", "BlackBearModel", ()),
    "grizzly_bear": ("Bear-Bedrock.bbmodel", "GrizzlyBearModel", ()),
    "polar_bear": ("Bear-Bedrock.bbmodel", "PolarBearModel", ()),
    "bluegill": ("Bluegill.bbmodel", None, ()),
    "boar": ("Boar-Bedrock.bbmodel", "BoarModel", ()),
    "bongo": ("ModelBongoTFC-Bedrock.bbmodel", None, ()),
    "caribou": ("Caribou.bbmodel", None, ()),
    "cougar": ("ModelCougarTFC-Bedrock.bbmodel", "CougarModel", ()),
    "crocodile": ("CrocodileModel.bbmodel", "CrocodileModel", ()),
    "deer": ("Deer-Bedrock.bbmodel", "DeerModel", ()),
    "direwolf": ("Direwolf-Bedrock.bbmodel", "DirewolfModel", ()),
    "gazelle": ("ModelGazelleTFC-Bedrock.bbmodel", None, ()),
    "horseshoe_crab": ("Horseshoe crab.bbmodel", "HorseshoeCrabModel", ()),
    "hyena": ("ModelHyenaTFC.bbmodel", "HyenaModel", ()),
    "isopod": ("Isopod.bbmodel", "IsopodModel", ()),
    "jellyfish": ("Jellyfish.bbmodel", None, ()),
    "leopard_seal": ("seal_leopard.bbmodel", "LeopardSealModel", ()),
    "lion": ("ModelLionTFC-Bedrock.bbmodel", "LionModel", ()),
    "lobster": ("Lobster.bbmodel", "LobsterModel", ()),
    "manatee": ("Manatee.bbmodel", None, ()),
    "mongoose": ("Mongoose.bbmodel", "MongooseModel", ()),
    "moose": ("Moose-Bedrock.bbmodel", "MooseModel", ()),
    "orca": ("Orca.bbmodel", "OrcaModel", ()),
    "peafowl": ("ModelPeacockTFC-Bedrock.bbmodel", None, ()),
    "penguin": ("Penguin-Bedrock.bbmodel", "PenguinModel", ()),
    "rat": ("Rat-Bedrock.bbmodel", "RatModel", ()),
    "sabertooth": ("ModelSabertoothTFC-Bedrock.bbmodel", "SabertoothModel", ()),
    "tiger": ("ModelTigerTFC-Bedrock.bbmodel", "TigerModel", ()),
    "wildebeest": ("ModelWildebeestTFC-Bedrock.bbmodel", "WildebeestModel", ()),
    "arctic_char": ("arctic_char.bbmodel", "ArcticCharModel", ()),
    "burbot": ("burbot.bbmodel", "BurbotModel", ()),
    "muksun": ("muksun.bbmodel", "MuksunModel", ()),
    "northern_pike": ("northern_pike.bbmodel", "NorthernPikeModel", ()),
    "pacu": ("pacu.bbmodel", "PacuModel", ()),
    "peacock_bass": ("peacock_bass.bbmodel", "PeacockBassModel", ()),
    "red_piranha": ("red_piranha.bbmodel", "RedPiranhaModel", ()),
    "spotted_gudgeon": ("spotted_gudgeon.bbmodel", "SpottedGudgeonModel", ()),
    "tilapia": ("tilapia.bbmodel", "TilapiaModel", ()),
}

# Ordered: the first matching token class wins. BetterModel's own names first (idle, walk), then ours.
CLASSES = [
    ("walk", re.compile(r"WALK|CRAWL|MOVEMENT|SCURRY|STROLL", re.I)),
    ("run", re.compile(r"RUN|SPRINT|DASH|GALLOP|JUMP", re.I)),
    ("sleep", re.compile(r"SLEEP", re.I)),
    ("attack", re.compile(r"ATTACK|BITE|HEADBUT|CHARGE", re.I)),
    ("swim", re.compile(r"SWIM", re.I)),
    ("eat", re.compile(r"EAT|GRASS|GRAZ", re.I)),
    ("idle", re.compile(r"IDLE|CALM", re.I)),
]
# A Java constant that looks like several classes needs an exact rule (the class is searched on the whole name).
EXACT = {"BITE_ROLL": "bite_roll", "PREPARE_CHARGE": "prepare_charge", "BOAR_PREPARE_CHARGE": "prepare_charge",
         "CAMEL_SIT_POSE": "sit_pose", "CAMEL_STANDUP": "stand_up", "CAMEL_SIT": "sit_down", "FLOP": "flop",
         "GAPE": "gape", "CROUCH": "crouch", "DAMAGE": "damage", "WOLF_SLEEPING": "sleep"}


def classify(raw: str) -> str | None:
    key = raw.upper()
    if key in EXACT:
        return EXACT[key]
    for name, rx in CLASSES:
        if rx.search(raw):
            return name
    return None


def bm_name(const: str, prefixes: list[str]) -> str:
    """Java const -> BetterModel animation name: classified when it matches one of BetterModel's names, else lower snake."""
    base = const
    for p in prefixes:
        if base.upper().startswith(p + "_"):
            base = base[len(p) + 1:]
    exact = EXACT.get(const.upper()) or EXACT.get(base.upper())
    if exact:
        return exact
    if re.search(r"STRUT|SNIFF|SEARCH|DRAG|ITCH|BURY|HIDE", base, re.I):
        return base.lower()
    return classify(base) or base.lower()


def species_mapping(key: str, consts: list[str]) -> dict[str, str]:
    prefixes = [key.upper(), key.upper().split("_")[-1]]
    out, used = {}, set()
    for c in consts:
        n = "crawl" if key.endswith("_bear") and c.endswith("_CRAWL") else bm_name(c, prefixes)
        if n in used:                        # two constants of one species classify alike: keep the second distinct
            n = c.lower()
        used.add(n)
        out[c] = n
    return out


def import_species(tfc: pathlib.Path, key: str) -> tuple[dict, list[str], dict]:
    bbfile, cls, extra = SPECIES[key]
    bb = json.loads((tfc / MODEL_DIR / bbfile).read_text(encoding="utf-8"))
    java: dict = {}
    sources = ([f"{ENTITY_DIR}/{cls}.java"] if cls else []) + list(extra)
    for s in sources:
        java.update(parse((tfc / s).read_text(encoding="utf-8")))
    mapping = species_mapping(key, list(java))
    old = bb.get("animations", [])
    bb["animations"] = []                    # Java is the source of truth; stale Blockbench copies are dropped below
    bb, warns = convert(bb, java, mapping)
    taken = {a["name"] for a in bb["animations"]}
    kept = []
    # Java rigs that differ from the .bbmodel's groups (TFC bedrock models share one file across species) lose their channels;
    # an animation with no channel left is dropped, and the model's own Blockbench animation takes the name when there is one.
    for a in list(bb["animations"]):
        ids0 = set(bone_ids(bb).values())
        if not any(u in ids0 for u in a.get("animators", {})):
            bb["animations"].remove(a)
            taken.discard(a["name"])
    if True:                                 # fall back to the model's own animations under free BetterModel names
        for a in old:
            n = classify(re.split(r"[._]", a["name"].replace("animation_", ""))[-1] if False else a["name"]) or None
            if n and n not in taken:
                a = dict(a, name=n)
                taken.add(n)
                kept.append(n)
                bb["animations"].append(a)
    ids = set(bone_ids(bb).values())
    for a in bb["animations"]:               # stale channels: animators whose bone no longer exists
        a["animators"] = {u: v for u, v in a.get("animators", {}).items() if u in ids}
    bb["animations"] = [a for a in bb["animations"] if a["animators"]]
    taken = {a["name"] for a in bb["animations"]}
    bb["name"] = f"tfp_{key}"
    meta = bb.setdefault("meta", {})
    meta["model_name"] = f"tfp_{key}"
    report = {"java": len(java), "kept_blockbench": kept, "animations": sorted(taken), "warnings": warns}
    return bb, check(bb, model_id=f"tfp_{key}", strict=False), report


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("tfc")
    ap.add_argument("--out", default=str(HERE / "imported"))
    ap.add_argument("--also", help="second output directory (content/generated/content/bettermodel)")
    ap.add_argument("--only", help="comma separated species keys")
    ap.add_argument("--report", help="write a Markdown report here")
    a = ap.parse_args(argv)
    tfc = pathlib.Path(a.tfc)
    outs = [pathlib.Path(a.out)] + ([pathlib.Path(a.also)] if a.also else [])
    for o in outs:
        o.mkdir(parents=True, exist_ok=True)
    keys = a.only.split(",") if a.only else sorted(SPECIES)
    bad, rows = 0, []
    for key in keys:
        bb, problems, rep = import_species(tfc, key)
        text = json.dumps(bb, indent=1, sort_keys=False) + "\n"
        for o in outs:
            (o / f"tfp_{key}.bbmodel").write_text(text, encoding="utf-8")
        status = "OK" if not problems else "FAIL"
        bad += bool(problems)
        print(f"{status:4} tfp_{key}: {rep['animations']}")
        for p in problems + [f"warn: {w}" for w in rep["warnings"]]:
            print("     -", p)
        rows.append((key, SPECIES[key][0], rep, problems))
    if a.report:
        lines = ["| Species | TFC bbmodel | Java defs | Our animations | Stale channels dropped | Validation |", "|---|---|---:|---|---|---|"]
        for key, f, rep, problems in rows:
            dropped = sum(1 for w in rep["warnings"] if "skipped" in w)
            lines.append(f"| `tfp_{key}` | `{f}` | {rep['java']} | {', '.join(rep['animations']) or '-'} | {dropped} | {'ok' if not problems else '; '.join(problems)} |")
        pathlib.Path(a.report).write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
