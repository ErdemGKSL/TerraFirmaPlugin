# PAR-HEAT-28 verification

- Date: 2026-11-17 (repair round 3; replaces the r2 run on `36bcc70` and the rejected `35ab107` evidence).
- Code under test: HEAD `a4afe7b` (`TorchService.LightRefresh`: one chunk light resend 5 region ticks after each burnout
  the commit queue applies; no code change since `36bcc70`). Jar built from that tree (`:dist:shadowJar`).
- Server / client: Paper 1.21.11 build 132, CraftEngine/BetterModel/BetterHud from the run-paper cache, headless vanilla
  1.21.11 client, bot `Bot`, fresh TFC world (`TFP_WORLD=1`, primed and restarted per `docs/testing/headless.md`).
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (5 tests). `./gradlew check` green
  on this tree (4931 tests, 0 failures).
- Run and judged by the main session (no Haiku agent tool in this worker); every frame below was looked at.
- Verdict: **pass**.

## Setup

`debug-profile.json` in the client game dir (`light_levels`, `looking_at_block`, `player_position` = `alwaysOn`), so F3
shows `Client Light: N (S sky, B block)`. Test site next to spawn (land, not ocean): stone floor `fill 2300 70 -1780 2340
73 -1756 stone`, air above to y 86, a 3-high stone wall at z -1760..-1756. `weather clear 1000000`, `gamemode survival
Bot`, `tfp debug thirst Bot 100`, `tfp debug hunger Bot 20`, `ce item give Bot tfp:torch 4`.

Every torch was placed by the bot (right click) into the cell it stands in: floor torches by looking straight down
(pitch 90), wall torches by looking at the wall at pitch 60 from the cell in front of it. So each lit and dead reading is
the F3 light of the torch's own cell (`Block:` line = the torch position).

Burnout on chunk load: for each pair, `gamemode creative Bot`, tp 300+ blocks east (x 2620 / 2640), wait until
`execute if loaded <torch>` reports **Test failed** (chunk unloaded, about 8 s), `tfp time add days 4` while the chunk is
unloaded (so the torch cannot burn out in a loaded chunk: the skip lands on the chunk-load catch-up), tp back, wait 8 s,
`gamemode survival Bot`, then F3 in each torch cell. No relog between the lit and the dead reading of a pair. (The steps'
order "skip, then tp away" would let the skip burn the torch out while its chunk is still loaded; tp away first makes the
burnout happen on chunk load, which is the case this item fixes. One 4-day skip per pair.)

## Results

| torch (cell) | GUI | lit, F3 in the torch cell | dead after one skip + round trip, no relog | evidence |
|---|---|---|---|---|
| floor 2305 74 -1770 | 2 | `15 sky, 14 block`, June 1, targets `minecraft:torch` 2305 74 -1770 | `15 sky, 0 block`, June 5, targets `minecraft:torchflower_crop[age=1]` (dead torch carrier) at the same cell | `01-gui2-lit-floor-f3-block14.png`, `03-gui2-dead-floor-f3-block0-no-relog.png` |
| wall 2313 74 -1761 (facing north) | 2 | `15 sky, 14 block`, June 1, targets `minecraft:wall_torch` 2313 74 -1761 | `15 sky, 0 block`, June 5, dead wall torch model in frame below the crosshair | `02-gui2-lit-wall-f3-block14.png`, `04-gui2-dead-wall-f3-block0-no-relog.png`, `05-gui2-dead-wall-torch-view.png` |
| floor 2325 74 -1770 | 3 | `13 sky, 14 block`, June 5, targets `minecraft:torch` 2325 74 -1770 | `13 sky, 0 block`, July 1, targets `minecraft:torchflower_crop[age=1]` at the same cell | `06-gui3-lit-floor-f3-block14.png`, `08-gui3-dead-floor-f3-block0-no-relog.png` |
| wall 2333 74 -1761 (facing north) | 3 | `13 sky, 14 block`, June 5, targets `minecraft:wall_torch` 2333 74 -1761 | `13 sky, 0 block`, July 1, dead wall torch model in frame | `07-gui3-lit-wall-f3-block14.png`, `09-gui3-dead-wall-f3-block0-no-relog.png`, `10-gui3-dead-wall-torch-view.png` |

The calendar has 8-day months here, so one 4-day skip moves June 1 to June 5 and June 5 to July 1. The torches of a pair
are 17 blocks apart (Manhattan), so neither adds light to the other's cell. The GUI 3 pair was placed fresh after the
relaunch with `guiScale:3` and its lit and dead readings come from that one session. In the lit GUI 3 frames the GUI 2
torches 20 blocks west were already dead; they add nothing either way.

`05` and `10` view the wall from 6 blocks away after the burnout: the wall torch shows the dead model (charred stick, no
flame). The dead floor torches are the dark `torchflower_crop` squares under the crosshair in `03` and `08`.

Crops: the PNGs are the full 1280x720 frames (05 and 10 cropped to the wall, palette-reduced to stay under 200 KB).
Lossless WebP frames: session scratchpad `ui/PAR-HEAT-28/r3-*.webp`. The BetterHud temperature panel overlaps the F3 date
line; the dates are readable in the WebP frames and match the `tfp time add` replies (June 5, then July 1).

Fidelity: TFC (`TFCTorchBlock` / `DeadTorchBlock`) needs no resend because the NeoForge client relights on the block
change; the plugin-side resend is a platform workaround with no gameplay effect, so no divergence row.
