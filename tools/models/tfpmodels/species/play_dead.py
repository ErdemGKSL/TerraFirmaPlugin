# Licensed under the EUPL, Version 1.2.
# Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
# file: src/main/java/net/dries007/tfc/client/model/entity/PenguinModel.java @ c3b4d9536 (setupAnim: core.xRot = -90 deg
# while the penguin plays dead)
"""PAR-FAUNA-31: adds the BetterModel animation ``play_dead`` to the imported penguin model.

The plugin plays ``play_dead`` while an amphibian plays dead (PaperAmphibianBody.PLAY_DEAD, looped) and stops it after.
TFC's penguin turns its ``core`` bone to -90 degrees about x (it lies on its back). The model is format 4.0, where Java
rotations are stored as they are (tfc_anim axis rule), so the keyframe is x = -90. Idempotent: run after import_tfc.py
(which rewrites the imported model without it):

    python3 -I tools/models/tfpmodels/species/play_dead.py tools/models/imported/tfp_penguin.bbmodel
"""
import json
import sys
import uuid

ANIM = "play_dead"
BONE = "core"
X_DEGREES = -90.0


def add_play_dead(model):
    """Returns True when the animation was added or replaced."""
    core = next((g for g in model["outliner"] if isinstance(g, dict) and g["name"] == BONE), None)
    if core is None:
        raise SystemExit("no bone " + BONE)
    ns = uuid.UUID(core["uuid"])
    kf = {
        "channel": "rotation",
        "data_points": [{"x": str(X_DEGREES), "y": "0.0", "z": "0.0"}],
        "uuid": str(uuid.uuid5(ns, "play_dead/kf")),
        "time": 0.0,
        "color": -1,
        "interpolation": "linear",
    }
    anim = {
        "uuid": str(uuid.uuid5(ns, "play_dead")),
        "name": ANIM,
        "loop": "loop",
        "override": True,
        "length": 1.0,
        "snapping": 24,
        "selected": False,
        "anim_time_update": "",
        "blend_weight": "",
        "start_delay": "",
        "loop_delay": "",
        "animators": {core["uuid"]: {"name": BONE, "type": "bone", "keyframes": [kf]}},
    }
    anims = [a for a in model.get("animations", []) if a["name"] != ANIM]
    changed = anims + [anim] != model.get("animations", [])
    model["animations"] = anims + [anim]
    return changed


def main(argv):
    for path in argv[1:]:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        model = json.loads(text)
        if add_play_dead(model):
            indent = 1 if text.startswith('{\n ') else None
            with open(path, "w", encoding="utf-8") as f:
                json.dump(model, f, indent=indent, ensure_ascii=False)
                f.write("\n" if text.endswith("\n") else "")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
