# PAR-FAUNA-03 verification
2026-11-20, HEAD fe8551d, Paper 1.21.11, BetterModel 3.5.1, GUI scale 2, headless client.
- BetterModel: "registered 94/94 model(s)", "models ready", no reload failure (an earlier run crashed on a face texture index; fixed in publish_models.py, validator check added).
- male_cow_info_and_male_sheep.png: PASS. `tfp debug animal info` on the spawned male cow reports model tfp_cow_m; the male sheep beside it shows horns, the deer is drawn with its TFC texture.
- duck_sheep_on_ledge.png: PASS. Sheep (wool) and a drake with the male's green head and its own texture.
- render_review.png: offline Blockbench render of cow female/male/old, duck male/baby, sheep, deer: horns, udder, antlers and the age and gender textures differ as TFC's.
- Not verified in game: a side by side cow male/female close-up (trees blocked the shots); covered by the model ids in info and the render review.
