#!/usr/bin/env python3
"""build_datapack.py <server-run-dir> <out-datapack-dir>: two colour variants of vanilla plains
(tfpspike:plains_summer / plains_autumn), copied from the server's own vanilla data so the biome JSON
format matches the MC version."""
import json, sys, zipfile
from pathlib import Path
run, out = Path(sys.argv[1]), Path(sys.argv[2])
jar = next(run.glob("versions/*/*.jar"))
with zipfile.ZipFile(jar) as z:
    plains = json.loads(z.read("data/minecraft/worldgen/biome/plains.json"))
    pack_format = json.loads(z.read("version.json"))["pack_version"]
if isinstance(pack_format, dict):  # 1.21.9+: {data_major, data_minor} or {data: n}
    pf = pack_format.get("data_major", pack_format.get("data"))
    minor = pack_format.get("data_minor", 0)
else:
    pf, minor = pack_format, 0
for name, grass, foliage in (("plains_summer", "#4fbf2a", "#3a9a1c"), ("plains_autumn", "#d8a23a", "#c0501c")):
    b = json.loads(json.dumps(plains))
    b.setdefault("effects", {})["grass_color"] = grass
    b["effects"]["foliage_color"] = foliage
    p = out / f"data/tfpspike/worldgen/biome/{name}.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(b, indent=1))
meta = {"pack": {"description": "tfp spike biomes", "pack_format": pf}}
if pf >= 82:  # new pack.mcmeta format (1.21.9+): min/max as [major, minor]
    meta["pack"]["min_format"] = [pf, minor]; meta["pack"]["max_format"] = [pf, minor]
(out / "pack.mcmeta").write_text(json.dumps(meta))
print("datapack format", pf, "->", out)
