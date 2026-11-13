# PAR-PROC-26 verification: durability material items are damageable; gem saw in game

- Date: 2026-11-13
- Base commit: b3b05cc plus the PAR-PROC-26 change (datagen regenerated, `:dist:shadowJar`)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449
  (`spikes/client/server.sh`, priming start then restart; vanilla world)
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, survival; GUI scale 2, then a second
  session with `MC_OPTIONS="guiScale:3"`. Pack applied 3 s after join both times.
- Run by the worker session itself: no cheap-model agent could be started from this session (PROTOCOL §6.1); the main
  session judged every screenshot.

## Steps and results

1. `ce item give Bot tfp:handstone`, `tfp:sandpaper`, `tfp:spindle`, `tfp:bone_needle`, `tfp:gem_saw`; then
   `data get entity Bot Inventory[{Slot:Nb}].components."minecraft:damage"` / `"minecraft:max_damage"`:
   handstone 0 / 250, sandpaper 0 / 40, spindle 0 / 40, bone needle 0 / 64. The gem saw (`iron_pickaxe` base) shows
   `max_damage` 1300 and no `damage` entry: vanilla's iron pickaxe prototype already holds `damage` 0, and `data get` lists
   only components that differ from the prototype.
2. `item modify entity Bot hotbar.0 {"function":"minecraft:set_damage","damage":0.5}` (handstone), `hotbar.1 ... 0.25`
   (sandpaper), `hotbar.4 ... 0.6` (gem saw): damage 125, 30 and 519 (the modifier worked: vanilla treats them as
   damageable). The bars show on all three, none on the untouched spindle and bone needle: `hotbar_bars_x4.png`.
3. Real use, no `set_components`: sandpaper + `tfp:ore/ruby` in the 2x2 inventory grid (TFC's
   `tfc:crafting/gem/ruby`, advanced shapeless with `tfc:damage_crafting_remainder`). The result is a cut ruby; the
   sandpaper stays in the grid with a durability bar (`craft1_x4.png`); a second craft leaves it at damage 2 (data get after
   closing the inventory; `sandpaper_after_two_crafts_gui2.png`). Two rubies in the inventory.
4. Gem saw on glass: `setblock` glass in front of Bot, hold attack with the gem saw: the glass breaks, drops glass (picked up)
   and the saw takes 1 damage, its bar appears (`gem_saw_after_glass_hotbar_x4.png`). TFC's glassworking use of the saw is
   PAR-PROC-16's.
5. `ce item give Bot` each of the 36 metal knives, hammers, scythes and saws: 36 "Gave" lines; every icon is its TFC metal
   texture (`tools_gui2.png`); hovered sample names "Copper Saw", "Steel Scythe", "Red Steel Knife", "Bismuth Bronze Hammer"
   (`tips_sheet.png`).
6. Hover the gem saw and the mortar with the cursor on the slot, GUI scale 2 and 3: "Gem Saw" and "Mortar", tooltip right
   next to the hovered hotbar slot, none over the recipe-book button (`gui2_*_tooltip.png`, `gui3_*_tooltip.png`). 8x icon
   crops (`gui2_saw_icon_x8.png`, `gui3_saw_icon_x8.png`, `gui2_mortar_icon_x8.png`) match TFC's `textures/item/gem_saw.png`
   and `mortar.png` pixel for pixel by eye.
7. `ce item give Bot tfp:glue 64` and `tfp:ore/halite 64`: 2 x 32 glue, 4 x 16 halite. By hand: right-click halves (16 glue,
   8 halite) clicked onto a full stack of the same item leave it at 32 / 16 and keep the rest on the cursor; placed into an
   empty slot (`stacks_gui3.png`). No stack above 32 glue or 16 halite.

| Criterion | Result |
|---|---|
| A freshly given handstone/sandpaper is damageable for vanilla (set_damage works, bar shows) | **Pass** (steps 1, 2) |
| `ce item give tfp:gem_saw` with TFC texture and name, GUI scale 2 and 3, slot next to tooltip; durability bar after use | **Pass** (steps 4, 6) |
| Real use without set_components: sandpaper as damaged remainder (and the gem saw on glass) shows and lowers the bar | **Pass** (steps 3, 4) |
