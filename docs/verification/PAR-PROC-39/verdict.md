# PAR-PROC-39 in-game verdict

- Date: 2026-11-17 (repair round after the verify-stage FAIL of commit 157d394)
- Tree: 157d394 plus the repair commit that adds this file (the unsealed carrier moved from `composter[level=1]` to
  `cactus[age=13]`; the jar was built from that working tree)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, TFC world
  (`TFP_WORLD=1`), no errors in the server log
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), pack applied, GUI scale 2, Bot in creative (immune to
  the blast; the keg has no game-mode-dependent behaviour)
- Run by the main session (no Haiku agent tool in this session); judged by the main session

## Steps run (RCON on port+10, bot `Bot`)

1. A 21x21 `minecraft:stone` floor at y 199 around (3328, 200, 1658), air above. `ce item give Bot tfp:powderkeg 1`, right
   click on the floor: keg at (3328, 200, 1658). Screenshot of the unsealed keg (first on the old composter carrier, then,
   after the fix and a restart that rebuilt the pack, on the cactus carrier).
2. Right click opened the screen; 13 x 64 gunpowder given, 12 stacks shift-clicked in. Screenshot.
3. Seal button pressed. Screenshot with the button tooltip. With the screen sealed: shift-click of the 13th stack from the
   inventory, left click and shift-click on keg slots, number key 1 over a keg slot. `clear Bot minecraft:gunpowder 0`
   reported 64 before and after (nothing moved), the cursor stayed empty and every keg slot still showed 64. Screenshot.
4. Escape; screenshot of the sealed keg in the world. `setblock` bedrock east of the keg (the only `#tfc:explosion_proof`
   block), `time query gametime` = 9893, `setblock` redstone_block west of the keg in the same tick (9893), screenshot of
   the lit keg, Bot teleported away, `fill 3313 185 1643 3343 215 1673 minecraft:stone replace minecraft:air` (the keg
   stayed), then `time query gametime` + `execute if block 3328 200 1658 minecraft:air` polled every ~10 ms.
5. Crater: `execute if block` along five axes; `fill ... glass replace air` over the cube counted the air, then reverted.
   The north half of the cube cut away for a section screenshot.

## Measurements

| Check | Expected | Measured |
|---|---|---|
| Fuse (redstone signal to keg gone) | 80 ticks (TFC `powderKegFuseTime`) | first poll with the keg gone at 9974: 81 ticks, i.e. 80 + the 1-tick poll |
| Crater volume in stone (air in the cube minus the keg cell) | vanilla `ServerExplosion` rays at strength 64 in a stone cube, bedrock east, redstone west, origin at the block corner (scratch simulation, 10 seeds): 3902 to 4014, mean 3953; at strength 48: ~2046; at 32: ~788 | 3991 |
| Axis reach (up, down, +z, -z, -x) | up to 12 at strength 64 | 12, 11, 10, 11, 10 |
| Bedrock next to the keg | survives | still bedrock |
| Redstone block next to the keg | destroyed | gone |

The lighting route is TFC's: `PowderkegBlock.useItemOn` opens the menu, and `neighborChanged` / `onPlace` light a sealed
keg from a redstone signal (flint and steel is not a TFC lighting route for the keg; a strong fire start via
`FireLightable` is, tested in PAR-PROC-18's MockBukkit tests).

## Criteria

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. TFC's unsealed, sealed and lit models on the selector's carriers | PASS after the fix. The first run showed the open keg on `composter[level=1]` with the world visible through the 1 px inset under it (the composter's full bottom plate culls the floor's top face): `unsealed-composter-carrier-before.png`. Every appearance now takes TFC's sealed outline row; the open keg draws TFC's `powderkeg` model on `cactus[age=13]` with the floor intact | `unsealed-cactus-carrier.png`, `sealed.png`, `lit-redstone.png` (lit fuse, lit side texture) |
| 2. Strength 64 with 12 x 64 gunpowder, 80 ticks after lighting, explosion_proof blocks survive | PASS | table above, `crater-section.png` (crater section in the stone cube, bedrock at the cut edge) |
| 3. The 12-slot screen lines up at GUI scale 2 and 3; the seal button toggles and locks the slots | PASS (scale 2 here; scale 3 alignment passed in the verify stage's run of 157d394, `gui3-*` screenshots, the screen code is unchanged). The seal button tooltip still shows "⚖ Very Light ⇲ Very Small" under "Unseal": the size/weight contributor does not skip `tfp:gui_button` items, tracked by PAR-INFRA-56 (ledger divergence row; `paper/.../itemsize`, outside this item's files) | `gui2-12x64-gunpowder.png`, `gui2-sealed-unseal-tooltip.png`, `gui2-sealed-locked.png` |

Overall: PASS, with the button tooltip line left to PAR-INFRA-56.
