# PAR-FAUNA-21 verification
Date 2026-11-19, HEAD bcfcfae, Paper 1.21.11, CE 26.9.2, BetterModel 3.5.1, headless client GUI scale 2.
Steps: `tfp debug animal spawn deer child` and `... adult` (typed in client chat, player-only), screenshot, `bm reload`, screenshot, `tfp debug animal info`.
- 01_both.png: PASS. Adult (large) and child (about half size) deer models drawn over the cow carriers (night, dark).
- 02_after_bm_reload.png: PASS. Model re-created after reload, still scaled. Info: tfc:deer on minecraft:cow, child, scale 0.5, model tfp_deer (1 attached).
- Found: after bm reload the re-apply hook ran on BetterModel's thread (ThreadAssertionException, rescale). Fixed in BetterModelIntegration.onTrackerCreated (hops to the entity thread); unit tests green, not re-run in game.
- itest animal.spawn_persist: PASS. Other failing cases in that run (ce.pack_issues, firepit.cook, grill.cook, worldgen.terrain_generates, CE issues log) are outside this item.
