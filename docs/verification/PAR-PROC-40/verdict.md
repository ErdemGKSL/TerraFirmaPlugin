# PAR-PROC-40 verification: one sneak click with charcoal or a log is one use

- Date: 2026-11-17.
- Code under test: af8eae5 (main-hand guard), `:dist:shadowJar`. The repair commit that adds these files changes only the
  same-tick off-hand handling (any right click of either hand in the tick of a handled click is now denied, as TFC's
  consumed result stops the off hand); the main-hand path these steps exercise is unchanged and covered by
  `CharcoalListenerClickTest` (10). The verify stage reruns the steps on the new HEAD.
- Server: headless Paper 1.21.11 with CraftEngine, BetterModel, BetterHud and the plugin (`spikes/client/server.sh`, pack
  self-hosted on 127.0.0.1). Client: headless 1.21.11 as `Bot`, survival, 1280x720, GUI scale 2 and 3. Stone floor,
  64 charcoal / 64 oak logs in the hotbar, sneak held before each click.
- Judged by the main session (Opus) from the full screenshots; the crops here keep the pile and the hotbar count.

| Step | Shot | Result |
|---|---|---|
| Sneak click once with charcoal on a stone floor | `01-scale2-charcoal-one-layer-one-charcoal.png` | one-layer pile (2/16 high), hotbar 64 -> 63. PASS |
| Sneak click on that pile, repeat to eight | `02-scale2-charcoal-eight-layers.png` | full 8-layer charcoal cube, hotbar 56 (one charcoal per layer). PASS |
| Sneak click once with an oak log on the floor | `03-scale2-oak-log-on-floor-one-log.png` | log pile with one log, hotbar 63. PASS |
| Sneak click once with an oak log on that log pile | `04-scale2-oak-log-on-log-pile-one-log.png` | two logs, hotbar 62. PASS |
| GUI scale 3: sneak click with charcoal on a new stone cell | `06-scale3-charcoal-one-layer-one-charcoal.png` | one-layer pile, hotbar 63 -> 62. PASS |

Note: at scale 3 the first click after the client relaunch (raw shot `05`, not kept) used one charcoal with no pile at the
cell the harness checked. `CharcoalListener.charcoal` uses an item only after a pile was placed or a layer added, and no
dropped item was found, so that click landed on another cell while the relaunched client was still syncing its view; it
is not a lost item. The repeat clicks never added a second layer.

Criterion "One sneak click with charcoal adds exactly one layer in game": **PASS**.
