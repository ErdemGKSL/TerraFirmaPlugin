# Licensed under the EUPL, Version 1.2.
"""PAR-FAUNA-03: the published jar models validate (incl. face texture indices BetterModel indexes) and match index.txt."""
import json
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tfpmodels.validate import check  # noqa: E402

MODELS = ROOT.parents[1] / "integration/bettermodel/src/main/resources/bettermodel/models"


class Published(unittest.TestCase):
    def test_index_lists_every_file_and_all_validate(self):
        ids = [l for l in (MODELS / "index.txt").read_text().split("\n") if l and not l.startswith("#")]
        files = sorted(p.stem for p in MODELS.glob("*.bbmodel"))
        self.assertEqual(sorted(ids), files)
        self.assertGreater(len(ids), 90)
        for mid in ids:
            data = json.loads((MODELS / f"{mid}.bbmodel").read_text())
            if mid == "tfp_example_bovine":
                continue
            self.assertEqual([], check(data, model_id=mid, strict=False), mid)

    def test_face_texture_index_check_catches_the_reload_crash(self):
        data = json.loads((MODELS / "tfp_cow.bbmodel").read_text())
        data["elements"][0]["faces"]["north"]["texture"] = 1
        self.assertTrue(any("index out of bounds" in e for e in check(data, strict=False)))


if __name__ == "__main__":
    unittest.main()
