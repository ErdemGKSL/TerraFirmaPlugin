# PAR-PROC-19 verification (item PAR-PROC-23): material items in game

- Date: 2026-11-05. Base commit 0df779c (no code change; dist jar built from it after `tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0,
  vanilla world (`spikes/client/server.sh`, priming start then restart, `docs/testing/headless.md`).
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, survival, pack applied 3 s after join.
  First session at GUI scale 2, second session at GUI scale 3.
- Run by the worker session itself: no cheap-model agent could be started from this session (PROTOCOL §6.1); the main
  session judged every screenshot.

## Steps

1. `ce item give Bot tfp:<id>` over RCON for the 22 ids of the item's `in_game_steps`: `ore/rich_hematite gem/ruby
   powder/salt ore/halite powder/flux jute_fiber burlap_cloth silk_cloth wool_cloth papyrus unrefined_paper glue soot
   mortar sandpaper straw stick_bunch blubber wool_yarn spawn_egg/cow handstone gem_saw`.
2. Open the inventory, hover each item (xdotool), screenshot, crop the tooltip: `names-gui2.png`, `names-gui3.png`
   (21 crops each, in the order above).
3. 8x crops of three icons next to TFC's own texture at 8x: `icons-x8-vs-tfc.png` (in game rich hematite, TFC
   `item/ore/rich_hematite.png`, in game cut ruby, TFC `item/gem/ruby.png`, in game handstone).
4. `clear Bot`, `ce item give Bot tfp:glue 64`, `ce item give Bot tfp:ore/halite 64`; `data get entity Bot
   Inventory[i].count` / `.components.minecraft:max_stack_size`; hotbar crop at 3x: `stacks-glue32-halite16.png`.
5. Durability: `ce item give` handstone and sandpaper; `item modify ... minecraft:set_damage` (no effect, see below):
   `durability-no-bar-after-set_damage.png`; then `item modify ... set_components {"minecraft:damage": 100 / 12}` (the
   component our crafting remainder hook writes through `Damageable#setDamage`): `durability-bar.png`.

## Results

| Criterion | Result |
|---|---|
| `ce item give` works for the sample | **Pass for 21 of 22.** `tfp:gem_saw` fails: `An internal error occurred`, server log `Cannot parse component minecraft:tool ... Missing tag: 'tfp:mineable/glass_saw' in 'minecraft:block'`. Known and owned by PAR-PROC-24 (its done-criterion is `ce item give` of the gem saw); the gem saw's name, texture and durability bar move to PAR-PROC-26 |
| TFC textures, no missing-texture cube | **Pass** (21/21): every icon is TFC's texture; rich hematite and cut ruby match TFC's PNGs pixel for pixel by eye at 8x; the handstone is TFC's 3D handstone model (stone disc with the stick handle) |
| TFC English names | **Pass** (21/21) at GUI scale 2 and 3: Rich Hematite, Cut Ruby, Salt, Halite, Flux, Jute Fiber, Burlap Cloth, Silk Cloth, Wool Cloth, Papyrus, Unrefined Paper, Glue, Soot, Mortar, Sandpaper, Straw, Stick Bunch, Blubber, Wool Yarn, Cow Spawn Egg, Handstone |
| Stack sizes follow TFC item_size (glue 32, halite 16) | **Pass**: 64 glue arrive as 2 x 32, 64 halite as 4 x 16 (`max_stack_size` 32 and 16) |
| Durability bar on handstone and sandpaper after use | **Pass with a finding.** `max_damage` is 250 (handstone) and 40 (sandpaper), as TFC. With a damage value the vanilla bar draws (`durability-bar.png`). Finding: a freshly given item has `minecraft:max_damage` but no `minecraft:damage` component, so vanilla's `ItemStack.isDamageableItem()` (needs `MAX_DAMAGE` and `DAMAGE`) is false: `set_damage` did nothing, and vanilla damage paths (`hurtAndBreak`, Paper `ItemStack#damage`) would not wear these items. TFC's `Properties.durability(n)` sets both. Fix tracked in PAR-PROC-26; our crafting remainder hook (`ItemModifierHooks.damageCraftingRemainder`, `Damageable#setDamage`) is not affected |

Verdict: **pass** for names, textures and stack sizes of the 21 buildable items; gem saw pending PAR-PROC-24 / PAR-PROC-26;
missing `minecraft:damage` default recorded as a divergence and fixed in PAR-PROC-26.
