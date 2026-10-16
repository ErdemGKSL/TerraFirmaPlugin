#!/usr/bin/env python3
"""M0-VIS-02 pack (throwaway). Namespace tfpvis02.

  build_pack.py <tfc-checkout> <out-pack-dir>

rock/granite   TFC granite raw, auto_state solid (the host rock)
ore/ruby_granite  identical visual to the rock (the gem block is a plain rock cube; the gem is an overlay)
item/gem/ruby  TFC ruby overlay (RGBA) as an item model, used by item_display overlays
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

ASSETS = "src/main/resources/assets/tfc"
NS = "tfpvis02"


def first_frame(img):
    w, h = img.size
    return img.crop((0, 0, w, w)) if h > w else img


def main():
    tfc, out = Path(sys.argv[1]), Path(sys.argv[2])
    if out.exists():
        shutil.rmtree(out)
    rp = out / "resourcepack" / "assets" / NS
    (rp / "textures/block/rock").mkdir(parents=True)
    (rp / "textures/item/gem").mkdir(parents=True)
    (rp / "models/item/gem").mkdir(parents=True)
    (rp / "items/gem").mkdir(parents=True)
    src = tfc / ASSETS / "textures/block/rock/raw/granite.png"
    first_frame(Image.open(src).convert("RGBA")).save(rp / "textures/block/rock/granite.png")
    ruby = tfc / ASSETS / "textures/block/ore/ruby.png"
    Image.open(ruby).convert("RGBA").save(rp / "textures/item/gem/ruby.png")
    (rp / "models/item/gem/ruby.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/gem/ruby"}}, indent=1))
    # 1.21.4+ item definition: the item_model component names this file
    (rp / "items/gem/ruby.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"{NS}:item/gem/ruby"}}, indent=1))

    rock_state = {"auto_state": "solid", "texture": f"{NS}:block/rock/granite"}
    template = ["default:sound/stone", "default:settings/solid_1x1x1"]
    blocks = {
        f"{NS}:rock/granite": {"settings": {"template": template, "overrides": {"hardness": 1.5, "resistance": 6.0}},
                               "state": rock_state},
        f"{NS}:ore/ruby_granite": {"settings": {"template": template, "overrides": {"hardness": 1.5, "resistance": 6.0}},
                                   "state": dict(rock_state)},
    }
    items = {f"{NS}:{k.split(':', 1)[1].replace('/', '_')}": {"model": f"{NS}:block/rock/granite",
             "behavior": {"type": "block_item", "block": k}} for k in blocks}
    conf = out / "configuration"
    conf.mkdir(parents=True)
    (conf / "blocks.yml").write_text(json.dumps({"blocks": blocks}, indent=1, sort_keys=True))
    (conf / "items.yml").write_text(json.dumps({"items": items}, indent=1, sort_keys=True))
    (out / "pack.yml").write_text(f"author: tfp-spike\nversion: 1.0\ndescription: M0-VIS-02\nnamespace: {NS}\n")
    print(f"pack written to {out}")


if __name__ == "__main__":
    main()
