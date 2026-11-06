# PAR-HUD-09: mount health bar: verdict: pass

- Date: 2026-11-06. Tree `10a0c33` plus this item's change, jar from `:dist:shadowJar` with the shared generated content
  (`tools/worktree/share-build.sh`, then `generateContent`).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start), CraftEngine
  26.9.2, BetterHud 2.2.0-SNAPSHOT-449. The log shows `Feature modules: ... mount_hud` and
  `BetterHud: registered 9 number placeholder(s) ... 7 listener(s)` and no error from our plugin (only the EULA flag notice
  and PacketEvents' update check, HTTP 403 from GitHub). Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, `guiScale:2`, then relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker).

## Steps (RCON)

Stone floor at y 199, air above, clear weather, noon, `gamemode survival Bot`, `clear Bot`, `tp Bot 0 200 0 0 30`;
`summon minecraft:horse 0 200 0 {Tame:1b,NoAI:1b,SaddleItem:{id:"minecraft:saddle",count:1},...}`;
`ride Bot mount @e[type=horse,limit=1,sort=nearest]` (horse max health 53, so 2650/2650);
`damage @e[type=horse,limit=1,sort=nearest] 6` (health 47: 2350/2650); full steel armour
(`ce item give Bot tfp:metal/{helmet,chestplate,greaves,boots}/steel`, moved into the armour slots, armour 15);
`ride Bot dismount`; remount; `gamemode creative Bot`; `gamemode survival Bot`. Same steps at GUI scale 2 and 3.

## Crops

| file | content |
|---|---|
| `gui{2,3}-riding-hurt-no-armour.png` | riding the hurt horse, no armour: mount bar 2350/2650 directly above the health bar, one empty row between |
| `gui{2,3}-riding-hurt-steel-armour.png` | the same with full steel armour: mount bar, empty row, armour bar, empty row, health bar |
| `gui2-dismounted-creative-remount-full.png` | top to bottom: dismounted (no mount bar), riding in creative (no TFC bar), back to survival while riding (bar back; the chat lines cover part of it), the first mount at full health (2650/2650) |

## Criteria

| criterion | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| Riding a damaged horse shows its health bar and text (2350/2650, fill 47/53) | pass | pass | `*-riding-hurt-*` |
| No overlap with armour or health: one empty row to the armour bar (armour worn) or to the health bar (no armour); text inside the bar | pass | pass | `*-riding-hurt-*` |
| The bar hides when not riding and in creative, and comes back in survival | pass | n/a (GUI 2 only) | `gui2-dismounted-creative-remount-full` |
