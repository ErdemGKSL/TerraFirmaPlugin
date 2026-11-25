# PAR-PROC-46 verdict: panning in game

Date 2026-11-25, tree HEAD 4022cf3 (jar built from it), Paper 1.21.11 build 132, vanilla 1.21.11 client (Xvfb, 1280x720), CraftEngine 26.9.2,
BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0. TFC world (`generator: TerraFirmaPlugin`), Bot in creative.
Setup: `ce item give Bot tfp:pan/empty`, `/tfp debug place tfp:deposit/native_copper/granite` in a water pool, screenshots by `spikes/client/shot.sh`.

| Step / criterion | Result | Evidence |
|---|---|---|
| Right-click the deposit with the empty pan: the block goes, a filled pan is in the inventory | PASS (slot 8 = `tfp:pan/filled`, the deposit block gone, empty pan kept: creative does not use it up) | `deposit-picked-up.png` |
| Filled pan on dry land: action bar "You need to stand in water to be able to pan." | PASS | `hint-dry-land.png` |
| In water, hold right-click: pan finishes after 6 s (still full at 4.8 s, empty at 6.3 s) | PASS | `t4.8s-filled.png`, `t6.3s-empty.png`, `panning-start.png` |
| Result: small native copper in the inventory (`tfp:ore/small_native_copper`, 2 after two pans) | PASS (copper only seen; no loose granite / topaz in two runs, the loot is random) | `t6.3s-empty.png` |
| Server log: no unknown item warnings from the deposit loot tables | PASS (no match in the server log) | |
| Brush sound every 0.8 s | NOT CHECKED: the headless client has no sound system ("Error starting SoundSystem") | |
| "no use animation" on dry land, off-hand use does nothing | NOT CHECKED (the hand pose on dry land looked tilted while the button was held; inconclusive in a still frame) | |

Verdict: PASS for the pickup, the water hint, the 6 s duration and the small ore result. Sound and off-hand are not verifiable here.
