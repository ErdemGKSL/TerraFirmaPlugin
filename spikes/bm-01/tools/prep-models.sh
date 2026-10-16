#!/usr/bin/env bash
# Spike-local model prep (M0-BM-01). Run from anywhere. Generates the bovine example, converts the TFC deer with
# tools/models/tfc_anim/convert.py, drops the TFC-named animation.deer.* entries (the converter
# keeps them; the README says to delete them), validates, and copies the files into the plugin jar's resources.
set -eu
REPO=/home/user/TerraFirmaPlugin
OUT=/tmp/claude-0/bm01-models
RES=$REPO/spikes/bm-01/src/main/resources/models
mkdir -p "$OUT" "$RES"
cd "$REPO/tools/models"
python3 -m tfpmodels.generate species/tfp_example_bovine.json "$OUT"
# The generator writes box UVs now (fill_box_uv.py workaround no longer needed).
python3 -I tfc_anim/convert.py /opt/tfc/resources/entity_models/Deer-Bedrock.bbmodel \
  /opt/tfc/src/main/java/net/dries007/tfc/client/model/entity/DeerModel.java "$OUT/tfp_deer.bbmodel" \
  DEER_WALK=walk DEER_RUN=run
python3 - "$OUT/tfp_deer.bbmodel" <<'PY'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["animations"] = [a for a in d.get("animations", []) if not a["name"].startswith("animation.deer.")]
json.dump(d, open(p, "w", encoding="utf-8"), indent=1)
print("kept animations:", [a["name"] for a in d["animations"]])
PY
python3 -I tfpmodels/validate.py --lenient "$OUT/tfp_deer.bbmodel" || true
python3 -I tfpmodels/validate.py "$OUT/tfp_example_bovine.bbmodel" || true
cp "$OUT/tfp_example_bovine.bbmodel" "$OUT/tfp_deer.bbmodel" "$RES/"
ls -la "$RES"
