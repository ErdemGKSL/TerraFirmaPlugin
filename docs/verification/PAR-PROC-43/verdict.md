# PAR-PROC-43 / PAR-PROC-45 verification: newer vanilla recipes removed in game

- Date: 2026-11-25
- Plugin jar: built from commit 6e783d9 (`:dist:shadowJar` in a clean worktree; datagen at the branch tip was broken by another
  session's WIP, `RenderedSound` "no SoundType volume/pitch" for the grass sounds), which contains `NewerVanillaRecipes` and `CraftListener`.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0
  (`spikes/client/server.sh`, priming start then restart, TFC world). Client: vanilla 1.21.11 under Xvfb, GUI scale 2, pack applied.
- Run by the worker session itself (no cheap-model agent available); every screenshot was looked at and judged here.

## Steps and results

Bot (survival, `tfp debug thirst Bot 100`), crafting table placed with `setblock`, items moved by xdotool clicks.

| step | result | evidence |
|---|---|---|
| 3 `minecraft:copper_ingot` over the top row + 2 sticks (copper pickaxe layout) | no result slot | `gui2-copper-pickaxe-no-result.png` |
| 8 copper ingots in the chestplate layout (`X.X / XXX / XXX`) | no result | `gui2-copper-chestplate-no-result.png` |
| vanilla planks top-right + 2 sticks on the diagonal (wooden spear layout) | no result | `gui2-wooden-spear-no-result.png` |
| control: two vanilla planks stacked vertically | result 4 sticks | `gui2-planks-make-sticks-control.png` |

| Criterion | Result |
|---|---|
| Copper tools/armour and spears do not craft on 1.21.11; kept vanilla recipes still craft | **Pass** (pickaxe, chestplate, wooden spear: empty result; planks to sticks: 4 sticks) |

Notes: the recipe book still shows "New Recipe(s) Unlocked" toasts for copper armour on joining (vanilla advancement
unlock, no crafting effect). Wooden-spear layout is vanilla 1.21.11's `  X / # / #  `-style diagonal (planks top-right, sticks middle and bottom-left).
