# Licensed under the EUPL, Version 1.2.
"""PAR-FAUNA-02: the Java layer importer reproduces TFC's own Blockbench cubes and outputs validate.
python3 -I -m unittest discover -s tools/models/tfpmodels/species -p 'test_*.py'   (TFC checkout: TFC_CHECKOUT, default /opt/tfc)
"""
import json
import os
import pathlib
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parents[1]))
import java_layer as J  # noqa: E402
from tfpmodels.validate import check  # noqa: E402

TFC = pathlib.Path(os.environ.get("TFC_CHECKOUT", "/opt/tfc"))


class JavaLayer(unittest.TestCase):
    def test_committed_outputs_validate_and_have_idle_and_walk(self):
        for key in J.SPECIES:
            data = json.loads((HERE / "out" / f"tfp_{key}.bbmodel").read_text())
            self.assertEqual([], check(data, model_id=f"tfp_{key}", strict=False), key)
            want = {"idle", "walk"} | ({"play_dead"} if key in J.PLAY_DEAD_HIDES else set())
            self.assertEqual(want, {a["name"] for a in data["animations"]}, key)

    def test_turtle_play_dead_hides_head_and_four_legs(self):
        data = json.loads((HERE / "out" / "tfp_turtle.bbmodel").read_text())
        anim = next(a for a in data["animations"] if a["name"] == "play_dead")
        self.assertTrue(anim["override"])
        bones = {v["name"]: v["keyframes"] for v in anim["animators"].values()}
        self.assertEqual(set(J.PLAY_DEAD_HIDES["turtle"]), set(bones))
        for frames in bones.values():
            self.assertEqual("scale", frames[0]["channel"])
            self.assertLess(float(frames[0]["data_points"][0]["x"]), 0.01)

    def test_penguin_play_dead_turns_core_back(self):
        import play_dead as P
        data = json.loads((HERE.parents[1] / "imported" / "tfp_penguin.bbmodel").read_text())
        anim = next(a for a in data["animations"] if a["name"] == "play_dead")
        (animator,) = anim["animators"].values()
        self.assertEqual("core", animator["name"])
        self.assertEqual("-90.0", animator["keyframes"][0]["data_points"][0]["x"])
        self.assertFalse(P.add_play_dead(data))  # idempotent

    def test_arithmetic_and_suffixes(self):
        self.assertAlmostEqual(J._eval("-0.4363F"), -0.4363)
        self.assertAlmostEqual(J._eval("(float)Math.PI / 2"), 1.5707963, places=6)

    @unittest.skipUnless((TFC / J.ENTITY_DIR).is_dir(), "no TFC checkout")
    def test_deer_matches_tfcs_own_bbmodel(self):
        J.SPECIES["deer"] = ("DeerModel", "deer.png")
        try:
            mine = J.build(TFC, "deer").to_json()
        finally:
            del J.SPECIES["deer"]
        ref = json.loads((TFC / "resources/entity_models/Deer-Bedrock.bbmodel").read_text())

        def cubes(els):
            return sorted((tuple(round(x, 2) for x in e["from"]), tuple(round(x, 2) for x in e["to"])) for e in els)
        self.assertEqual(cubes(ref["elements"]), cubes(mine["elements"]))
        mine_rot = sorted(tuple(round(v, 1) for v in g["rotation"]) for g in mine["groups"] if any(g["rotation"]))
        self.assertIn((-37.5, 0.0, 0.0), mine_rot)           # neck0: Java 0.6545 rad, Blockbench -37.5 deg

    @unittest.skipUnless((TFC / J.ENTITY_DIR).is_dir(), "no TFC checkout")
    def test_output_reproduces(self):
        for key in ("cow", "chicken"):
            built = json.dumps(J.build(TFC, key).to_json(), indent=1) + "\n"
            self.assertEqual(built, (HERE / "out" / f"tfp_{key}.bbmodel").read_text(), key)


if __name__ == "__main__":
    unittest.main()
