# PAR-PROC-11 / PAR-PROC-34 verification: the composter in game

- Date: 2026-11-14
- Code under test: the PAR-PROC-34 working tree on top of bdceff8 (the jar built from it with `:tools:datagen:generateContent`
  and `:dist:shadowJar`); the commit that carries this verdict is that tree.
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`), CraftEngine, BetterModel, BetterHud and PacketEvents as
  the script installs them. Primed and restarted: the priming start logged `RESTART REQUIRED`, the restart did not;
  `Loaded blocks (1418)`, no CraftEngine issues, feature module `composter` enabled, no plugin errors (only PacketEvents'
  update check, HTTP 403).
- Client: headless 1.21.11 as `Bot` (`spikes/client/session.sh`, `MC_OPTIONS="guiScale:2"`, pack applied after 3 s).
  Captures cropped to the composter.

## Steps (RCON + `xdotool`, Bot op, survival for every interaction, spectator only to frame the captures)

1. Stone floor at y 63..64 around (-1792, -1099); a stone block at (-1792, 65, -1099); Bot types
   `/tfp debug place tfp:composter` looking at it: "Placed tfp:composter at world -1792 65 -1099". TFC's bin model is
   drawn (empty, stage 0).
2. First fill (exploration): 4 `tfp:food/wheat_grain` (`#c:foods/grain`, green medium, 2 each) and 4 paper (brown high).
   The bin showed stage 3 with the compost drawn as the `entity_renderer` display inside the bin
   (`01-stage3-compost-display.png`). A further 4 carrots and wood ash filled it; 12 days later it was ready and a sneak
   extraction gave one compost (the same result as run 2 below).
3. Run 2, from an emptied bin: 4 `tfp:food/carrot` (`#c:foods/vegetable`, green high, 4 each: green 16) and wood ash until
   the brown side refused (brown 16). `/tfp debug block`: `tfp:composter[stage=8,type=normal]`; TFC's normal compost to
   the rim, the merged stage-8 model on the solid pool state (`02-full-stage8-normal.png`).
4. `tfp time add days 12` (288,000 calendar ticks = `CompostRules.DEFAULT_READY_TICKS`, rainfall factor 1 at this
   spot). The block keeps `type=normal` until the next callback (ledger: a calendar skip leaves the unripe type showing
   until an interaction); one empty-hand right click settles it: TFC's ready texture (`03-ready-after-12-days.png`),
   `/tfp debug block`: `tfp:composter[stage=8,type=ready]` (`04-ready-debug-state.png`).
5. Sneak + empty hand + right click on the bin's side: the bin is emptied (stage 0, the bin model) and one item entity
   `tfp:compost` (count 1, the only item entity near the composter) is spawned above the block
   (`05-sneak-extracted-one-compost.png`).

Harness note: a sneaking player's eye is 0.35 lower, so the first sneak clicks at pitch 35 hit the floor in front of the
composter (nothing happened); at pitch 20 the click hits the bin. Not a defect.

## Results

| Criterion | Result |
|---|---|
| The bin shows `type=ready` after `composterTicks` (12 days) | **Pass** (03, 04) |
| Sneak extraction gives one compost | **Pass**: one `tfp:compost` above the block, the bin reset to stage 0 (05) |
| The CE block draws TFC's models per stage and type | **Pass**: bin model, compost display at stage 3 (01), merged full model at stage 8 normal (02) and ready (03) |
