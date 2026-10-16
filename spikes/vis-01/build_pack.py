#!/usr/bin/env python3
"""M0-VIS-01: builds a tiny CraftEngine pack that exercises every visual source.

  build_pack.py <tfc-checkout> <out-pack-dir>

Blocks (namespace tfpspike):
  rock/<variant>/granite       raw (+hardened sharing raw's visual), cobble, mossy_cobble, smooth,
                               bricks, cracked_bricks, mossy_bricks, chiseled, gravel
  ore/<grade>_<ore>/granite    hematite, native_copper, native_gold x poor/normal/rich, flattened
  ore/small_native_copper      button visual (powered=false and powered=true pair)
  test/redstone_ore_twin       redstone_ore lit=false + lit=true with one model (prediction trap)
Visual sources are mixed on purpose so one in-game look covers each category:
  auto_state solid | explicit absent-in-TFC state | explicit identity duplicate | button | redstone_ore
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

TFC_ASSETS = "src/main/resources/assets/tfc"
ROCK = "granite"
ROCK_VARIANTS = ["raw", "cobble", "mossy_cobble", "smooth", "bricks", "cracked_bricks",
                 "mossy_bricks", "chiseled", "gravel"]
ORES = ["hematite", "native_copper", "native_gold"]
GRADES = ["poor", "normal", "rich"]

# id -> explicit vanilla state; anything not listed uses auto_state: solid.
EXPLICIT = {
    # absent-in-TFC (clean): the real block must be mapped away (block_state_mappings below)
    "rock/cobble/granite": "minecraft:andesite",
    "ore/rich_hematite/granite": "minecraft:copper_ore",
    "rock/chiseled/granite": "minecraft:chiseled_stone_bricks",
    # identity duplicate: a double slab renders exactly like the full block it is mapped to
    "rock/smooth/granite": "minecraft:brick_slab[type=double,waterlogged=false]",
    "rock/bricks/granite": "minecraft:mud_brick_slab[type=double,waterlogged=false]",
}
MAPPINGS = {
    "minecraft:andesite": "minecraft:stone",
    "minecraft:copper_ore": "minecraft:stone",
    "minecraft:chiseled_stone_bricks": "minecraft:stone_bricks",
    "minecraft:brick_slab[type=double,waterlogged=false]": "minecraft:bricks",
    "minecraft:mud_brick_slab[type=double,waterlogged=false]": "minecraft:mud_bricks",
    "minecraft:oak_button[face=floor,facing=north,powered=false]": "minecraft:birch_button[face=floor,facing=north,powered=false]",
    "minecraft:oak_button[face=floor,facing=north,powered=true]": "minecraft:birch_button[face=floor,facing=north,powered=true]",
    "minecraft:redstone_ore[lit=false]": "minecraft:stone",
    "minecraft:redstone_ore[lit=true]": "minecraft:stone",
}


def tex(tfc: Path, rel: str) -> Path:
    return tfc / TFC_ASSETS / "textures" / (rel + ".png")


def first_frame(img: Image.Image) -> Image.Image:
    """TFC animates a few textures as vertical strips; a block visual needs one 16x16 frame here."""
    w, h = img.size
    return img.crop((0, 0, w, w)) if h > w else img


def flatten(base: Path, overlay: Path, out: Path):
    """Ore = rock + overlay in one opaque PNG (≤1.21.11 solid-layer visuals ignore alpha)."""
    b = first_frame(Image.open(base).convert("RGBA"))
    o = first_frame(Image.open(overlay).convert("RGBA")).resize(b.size, Image.NEAREST)
    merged = Image.alpha_composite(b, o)
    merged.putalpha(255)
    out.parent.mkdir(parents=True, exist_ok=True)
    merged.save(out)


def cube(texture: str) -> dict:
    return {"parent": "minecraft:block/cube_all", "textures": {"all": texture}}


def main():
    tfc, out = Path(sys.argv[1]), Path(sys.argv[2])
    if out.exists():
        shutil.rmtree(out)
    rp = out / "resourcepack/assets/tfpspike"
    blocks, items = {}, {}

    def add_block(bid: str, texture_rel: str, extra: dict | None = None):
        texture = f"tfpspike:block/{texture_rel}"
        state = {}
        if bid in EXPLICIT:
            state["state"] = EXPLICIT[bid]
            state["model"] = {"path": f"tfpspike:block/{texture_rel}",
                              "generation": cube(texture)}
        else:
            state["auto_state"] = "solid"
            state["texture"] = texture
        entry = {"settings": {"template": ["default:sound/stone", "default:settings/solid_1x1x1"],
                              "overrides": {"hardness": 1.5, "resistance": 6.0}},
                 "state": state}
        if extra:
            entry.update(extra)
        blocks[f"tfpspike:{bid}"] = entry
        items[f"tfpspike:{bid}"] = {"model": f"tfpspike:block/{texture_rel}",
                                    "behavior": {"type": "block_item", "block": f"tfpspike:{bid}"}}

    # rocks: copy textures; hardened shares raw's visual by pointing at the same texture+state kind
    for v in ROCK_VARIANTS:
        src = tex(tfc, f"block/rock/{v}/{ROCK}")
        dst = rp / f"textures/block/rock/{v}/{ROCK}.png"
        dst.parent.mkdir(parents=True, exist_ok=True)
        first_frame(Image.open(src).convert("RGBA")).save(dst)
        add_block(f"rock/{v}/{ROCK}", f"rock/{v}/{ROCK}")
    # Hardened: same model as raw. With auto_state each block gets its own state, so to really share
    # one visual the allocator gives both the same explicit state. Here: both on stone_bricks? No -
    # we keep raw on auto_state and give hardened an explicit identity duplicate with the SAME model
    # bytes as raw, which proves byte-identical sharing is accepted.
    EXPLICIT["rock/hardened/granite"] = "minecraft:stone_brick_slab[type=double,waterlogged=false]"
    MAPPINGS["minecraft:stone_brick_slab[type=double,waterlogged=false]"] = "minecraft:stone_bricks"
    add_block(f"rock/hardened/{ROCK}", f"rock/raw/{ROCK}")

    for ore in ORES:
        for g in GRADES:
            ore_name = f"{g}_{ore}"
            overlay = tex(tfc, f"block/ore/{ore_name}")
            flatten(tex(tfc, f"block/rock/raw/{ROCK}"), overlay, rp / f"textures/block/ore/{ore_name}/{ROCK}.png")
            add_block(f"ore/{ore_name}/{ROCK}", f"ore/{ore_name}/{ROCK}")

    # small ore on a button state: the TFC groundcover model shape, our flattened texture
    gc_model = json.loads((tfc / TFC_ASSETS / "models/block/groundcover/native_copper.json").read_text())
    elements = gc_model.get("elements")
    if elements is None:  # some TFC groundcover models inherit their shape
        parent = gc_model["parent"].split(":", 1)[-1]
        elements = json.loads((tfc / TFC_ASSETS / "models" / (parent + ".json")).read_text())["elements"]
    shape = {"parent": "minecraft:block/block", "elements": elements,
             "textures": {k: f"tfpspike:block/ore/small/{Path(t).name}" for k, t in gc_model["textures"].items()}}
    shape["textures"]["particle"] = shape["textures"].get("particle", next(iter(shape["textures"].values())))
    for k, t in gc_model["textures"].items():
        d = rp / f"textures/block/ore/small/{Path(t).name}.png"
        d.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(tex(tfc, t.split(":", 1)[1]), d)
    (rp / "models/block/ore").mkdir(parents=True, exist_ok=True)
    (rp / "models/block/ore/small_native_copper.json").write_text(json.dumps(shape, indent=1))
    for powered in ("false", "true"):
        bid = "ore/small_native_copper" + ("_powered_twin" if powered == "true" else "")
        blocks[f"tfpspike:{bid}"] = {
            "settings": {"hardness": 0.1, "resistance": 0.1, "replaceable": False},
            "state": {"state": f"minecraft:oak_button[face=floor,facing=north,powered={powered}]",
                      "model": {"path": "tfpspike:block/ore/small_native_copper"}}}
        items[f"tfpspike:{bid}"] = {"model": "tfpspike:block/ore/small_native_copper",
                                    "behavior": {"type": "block_item", "block": f"tfpspike:{bid}"}}

    # redstone_ore lit=false and lit=true both show rich native gold (byte-identical model)
    for lit in ("false", "true"):
        bid = f"test/redstone_ore_{lit}"
        blocks[f"tfpspike:{bid}"] = {
            "settings": {"hardness": 1.5, "resistance": 6.0},
            "state": {"state": f"minecraft:redstone_ore[lit={lit}]",
                      "model": {"path": f"tfpspike:block/ore/rich_native_gold/{ROCK}",
                                "generation": cube(f"tfpspike:block/ore/rich_native_gold/{ROCK}")}}}
        items[f"tfpspike:{bid}"] = {"model": f"tfpspike:block/ore/rich_native_gold/{ROCK}",
                                    "behavior": {"type": "block_item", "block": f"tfpspike:{bid}"}}

    conf = out / "configuration"
    conf.mkdir(parents=True, exist_ok=True)
    # JSON is valid YAML: keeps the generator dependency-free and the output deterministic.
    (conf / "blocks.yml").write_text(json.dumps({"blocks": blocks}, indent=1, sort_keys=True))
    (conf / "items.yml").write_text(json.dumps({"items": items}, indent=1, sort_keys=True))
    (conf / "mappings.yml").write_text(json.dumps({"block_state_mappings": MAPPINGS}, indent=1, sort_keys=True))
    (out / "pack.yml").write_text("author: tfp-spike\nversion: 1.0\ndescription: M0-VIS-01\nnamespace: tfpspike\n")
    print(f"{len(blocks)} blocks, {len(MAPPINGS)} mappings -> {out}")


if __name__ == "__main__":
    main()
