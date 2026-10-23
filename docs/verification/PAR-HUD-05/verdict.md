# PAR-HUD-05: HUD vs vanilla armour row — verdict: pass

- Date: 2026-10-23. Tree: `1c7067e` rebased on `290b6b7` plus this item's test (no HUD generator change), jar from
  `:dist:shadowJar` with freshly generated content.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0
  (`spikes/client/server.sh`, priming start, world deleted, second start). Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, `guiScale:2`, then a relaunch with `guiScale:3`); the CraftEngine pack applied 3 s after
  each join.
- Judged by the main session from the crops below and from frame rows measured on the raw 1280x720 shots.

## Steps (RCON)

| state | commands |
|---|---|
| site | `forceload add -16 -16 16 16`, stone floor at y 99, `tp Bot 0 100 0 0 30`, `gamemode survival Bot`, difficulty easy, noon |
| full steel armour | `ce item give Bot tfp:metal/{helmet,chestplate,greaves,boots}/steel`, `item replace entity Bot armor.{head,chest,legs,feet} from entity Bot hotbar.0..3`, the hotbar slots emptied; `attribute Bot minecraft:armor get` = 15 |
| hurt | `damage Bot 14 minecraft:generic_kill` (health text over the grey empty channel) |
| XP number | `xp set Bot 120 levels` |
| no armour | `clear Bot` (armour 0) |
| riding | `summon minecraft:horse` (tame, saddled, max health 30, health 20), `ride Bot mount @e[type=horse,limit=1,sort=nearest]` |

## Crops

| file | scale | state |
|---|---|---|
| `gui2-full-armour-hotbar-3x.png` | 2 | full steel armour, full health, XP 120 |
| `gui2-armour-hurt-hotbar-3x.png`, `gui2-armour-health-left-8x.png` | 2 | full armour, hurt |
| `gui2-no-armour-hotbar-2x.png` | 2 | no armour: the armour bar is gone, no vanilla armour icons |
| `gui2-riding-horse-hotbar-3x.png` | 2 | full armour, riding a hurt horse (jump bar on the XP row) |
| `gui3-full-armour-hotbar-2x.png` | 3 | full armour |
| `gui3-riding-horse-hotbar-2x.png` | 3 | full armour, riding, player hurt |

## Checks

| check | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| With full steel armour no vanilla armour icon is drawn (the `armor_*` sprites are transparent) and nothing overlaps the health bar | pass | pass | `*-full-armour-*`, `gui2-armour-health-left-8x` |
| The armour bar sits on its planned rows: frame H-48..H-42, one empty row H-41, health frame H-40..H-31 (measured: the same rows at both scales; right column thirst H-45..H-39, hunger H-37..H-31) | pass | pass | raw shots, column scans |
| The health text (glyphs and shadow) stays inside the health interior, at full health and over the empty channel | pass | pass | `gui2-armour-health-left-8x`, `*-hurt-*`, `gui3-riding-*` |
| Riding a living mount: no vehicle hearts (hidden), hunger and thirst keep their rows, the jump bar replaces the XP bar on the XP row and touches no bar | pass | pass | `*-riding-horse-*` |
| The XP number (level 120) sits between the columns and touches neither | pass | pass | all hotbar crops |
| Without armour the armour bar is hidden | pass | — | `gui2-no-armour-hotbar-2x` |

The HUD values follow the server with a short delay (one capture shows the previous health), which is the snapshot
publish rate, not a layout fault. Mount health is not shown at all (vehicle hearts hidden, TFC draws a second health
bar): ledger divergence, follow-up PAR-HUD-09.
