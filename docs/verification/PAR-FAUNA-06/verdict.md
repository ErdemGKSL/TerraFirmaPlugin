# PAR-FAUNA-06 verification
2026-11-20, HEAD fce1191, Paper 1.21.11, headless client.
- Milk (milk.png): an empty wooden bucket right-clicked on the female cow (familiarity 0.35) became a bucket with tfp:fluid minecraft:milk 1000; a second empty bucket: "It has nothing to give yet." (cooldown). PASS.
- Shears: bronze shears on a sheep (familiarity 0.18) dropped tfp:wool and the model changed from woolly (sheep_before.png) to the bare sheared look (sheep_sheared.png). PASS.
- Wool regrowth: after `tfp time add days 5` the woolly model returned (sheep_regrown.png). PASS.
- Eggs: a hen with familiarity 0.18 laid a minecraft:egg item entity after one day, animal_produced tick advanced (confirmed by entity data, no screenshot). PASS.
- Observation: one chicken pinned with NoAI vanished twice (cause not found; an unpinned hen was fine) and the HUD date and `time query day` disagreed after `tfp time add`; both are outside this item.
