# Licensed under the EUPL, Version 1.2.
"""PAR-FAUNA-01: imported TFC entity models validate, carry BetterModel animation names and reproduce from the TFC checkout.
python3 -I -m unittest discover -s tools/models/tests   (TFC checkout via TFC_CHECKOUT, default /opt/tfc)
"""
import json
import os
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import import_tfc  # noqa: E402
from tfpmodels.validate import check  # noqa: E402

IMPORTED = ROOT / "imported"
TFC = pathlib.Path(os.environ.get("TFC_CHECKOUT", "/opt/tfc"))


class ImportedModels(unittest.TestCase):
    def test_every_species_has_a_committed_output_and_validates(self):
        for key in import_tfc.SPECIES:
            f = IMPORTED / f"tfp_{key}.bbmodel"
            self.assertTrue(f.exists(), f)
            self.assertEqual([], check(json.loads(f.read_text()), model_id=f"tfp_{key}", strict=False), key)

    def test_animation_names_are_lowercase_betermodel_style(self):
        for f in IMPORTED.glob("*.bbmodel"):
            for a in json.loads(f.read_text()).get("animations", []):
                self.assertRegex(a["name"], r"^[a-z0-9_]+$", f.name)

    def test_walking_species_have_walk_and_deer_has_run(self):
        deer = {a["name"] for a in json.loads((IMPORTED / "tfp_deer.bbmodel").read_text())["animations"]}
        self.assertEqual({"walk", "run"}, deer)

    def test_no_hitbox_bones_and_no_empty_animations(self):
        for f in IMPORTED.glob("*.bbmodel"):
            for a in json.loads(f.read_text()).get("animations", []):
                self.assertTrue(a["animators"], f"{f.name}:{a['name']} has no channel left")

    @unittest.skipUnless((TFC / import_tfc.MODEL_DIR).is_dir(), "no TFC checkout")
    def test_output_reproduces_from_tfc(self):
        for key in ("deer", "crocodile", "mongoose", "bactrian_camel"):
            bb, problems, _ = import_tfc.import_species(TFC, key)
            self.assertEqual([], problems)
            self.assertEqual((json.dumps(bb, indent=1) + "\n"), (IMPORTED / f"tfp_{key}.bbmodel").read_text(), key)


if __name__ == "__main__":
    unittest.main()
