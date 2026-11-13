# PAR-SURV-05: Health and damage: verdict: pass

- Date: 2026-11-13. Code: the PAR-SURV-05 commit on top of `f576c77` (jar from `:dist:shadowJar` of the working tree).
- Server: Paper 1.21.11, `TFP_WORLD=1`, priming start, world deleted, second start. CraftEngine 26.9.2, BetterModel 3.5.1,
  BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb, 1280x720, GUI scale 2, pack applied 3 s after the join.
- Setup: stone floor at y 99 inside a hollow barrier box (y 100-104), Bot at 0 101 0, `difficulty normal`, survival,
  `/tfp debug damage` on (one log line per hit).

| step | observed | verdict |
|---|---|---|
| Bot without armour, `summon minecraft:zombie` 2-3 blocks away | log `minecraft:zombie -> minecraft:player: minecraft:mob_attack 3.000 ... type none, resistance 0.00, factor 1.0000, health modifier 0.850 = 3.529`; 2 hits took the health text from 826/826 to 527/826 (`unarmoured-hud.png`, health modifier 0.826 by then) | pass |
| full steel armour (`ce item give Bot tfp:metal/{helmet,chestplate,greaves,boots}/steel`, moved to the armour slots with `item replace`) and the same zombie | log `... type none, resistance 120.00, factor 0.3012, health modifier 0.850 = 1.063` on every hit: the untyped zombie hit uses the highest steel resistance (piercing 30) x 4 pieces, `e^(-1.2) = 0.3012`, as TFC's `calculateMultiplier`; six hits took 20.0 HP to 17.06 (vanilla armour 15 then applies to the reduced base); HUD 814/850 after two hits (`steel-armour-hud.png`, `steel-armour-zombie.png`) | pass |
| other sources seen in the same run | `minecraft:in_wall` typed crushing (resistance 0 unarmoured, factor 1.0); `on_fire` untyped | as TFC |
| regeneration | food 17, after the fight: 11.80 -> 12.03 HP in 10 s (about 0.0113 HP per 10 ticks, TFC's `(1 + food bonus + thirst bonus) x 0.004`), no vanilla jumps | pass |

Done criterion "A zombie hit on a player in steel armour is reduced by TFC's resistance (debug log shows the factor)": pass
(`damage-log.txt` holds the hit lines of both runs).

| file | content |
|---|---|
| `unarmoured-hud.png` | HUD strip (2x) after two hits without armour: 527/826 |
| `steel-armour-hud.png` | HUD strip (2x) after two hits in full steel: 814/850 |
| `steel-armour-zombie.png` | the zombie attacking the armoured Bot (half size) |
| `damage-log.txt` | the `/tfp debug damage` lines of the run |
