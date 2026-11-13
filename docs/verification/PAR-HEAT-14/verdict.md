# PAR-HEAT-14 verification: heat bar on hot items

- Date: 2026-11-13
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at 89501f7 plus the PAR-HEAT-14 change (the commit that adds this
  file)
- Server: Paper 1.21.11 (`spikes/client/server.sh`, TFC world), CraftEngine, BetterModel, BetterHud and PacketEvents 2.14.0
  as in `docs/testing/headless.md`. Log: `Item bars: heat bar on`; no packet handler errors, no client disconnect.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then a rejoin with `MC_OPTIONS="guiScale:3"`; pack
  applied 3 s after the join both times.
- Run by the worker session itself: it had no subagent tool to delegate the run. Crops are of hotbar slots 1 and 2; bar
  widths were also measured from the full screenshots (bar pixels / GUI scale).

## Steps

1. `gamemode survival Bot`, `clear Bot`, `ce item give Bot tfp:metal/ingot/copper`, `tfp debug heat Bot 540`, a second
   (cold) copper ingot into slot 2; screenshot.
2. `tfp debug heat Bot 0`; screenshot.
3. Own bar wins: the copper pickaxe could not be used (it is not heatable here: `tfp debug heat` reads 0 C, follow-up
   PAR-HEAT-16), so a copper ingot was made damageable in place with
   `item modify ... set_components {max_stack_size: 1, max_damage: 100, damage: 40}` and heated to 540 C; screenshot. Then
   `damage: 0` (no bar of its own) and heated again; screenshot.
4. Stack count: a stack of 3 copper ingots heated to 540 C (step run separately); screenshot.
5. Creative return: a stack of 3 heated to 900 C, `gamemode creative Bot`, inventory opened, the stack picked up from
   hotbar slot 1 and put into slot 3 by mouse; then `data get` of the server stack.
6. Steps 1 to 3 again at GUI scale 3.

## Evidence

- `01-g2-hot540-8x.png`, `11-g3-hot540-6x.png`: hot ingot (slot 1) with a yellow bar 7 px wide (13 * 540 / 1080 = 6.5 -> 7;
  a run that resent the stack a second later at about 530 C showed 6), cold ingot (slot 2) with none.
- `02-g2-cold-8x.png`, `12-g3-cold-6x.png`: after `heat 0` no bar.
- `03-g2-damaged-hot-own-bar-8x.png`, `13-g3-...`: damaged hot item shows its durability bar, 8 px
  (round(13 - 40 * 13 / 100)), not the heat bar's 7.
- `04-g2-undamaged-hot-heat-bar-8x.png`, `14-g3-...`: the same item with damage 0 shows the heat bar, 7 px.
- `05-g2-stack-of-3-8x.png`: a stack of 3 keeps its count with the bar (the client accepts `max_damage` on a stack with
  `max_stack_size` 64).
- `06-g2-creative-900-8x.png`: in creative, 900 C, green bar 11 px (13 * 900 / 1080 = 10.8).
- `07-g2-creative-moved-6x.png`: the stack after the creative move, still drawn with its bar (the server resent it).

## Criteria

| Criterion | Result |
|---|---|
| A hot ingot shows a bar whose width follows TFC | Pass (01, 11, 06: 7 px at 540 C, 11 px at 900 C) |
| A cold one shows none | Pass (01 slot 2, 02, 12) |
| A damaged tool keeps its durability bar | Pass with a damageable copper ingot (03, 13; 04 and 14 show the heat bar once undamaged). Real metal tools are not heatable yet: PAR-HEAT-16 |
| Stackable items keep their count | Pass (05) |
| (extra) Creative return leaves the server stack clean | Pass: after the move the server stack (slot 2, count 3) has no `max_damage`, `damage`, `max_stack_size`, `tfp:item_bar` or `tfp:heat_glow` |
| Tests green | Pass: core ItemBarGoldenTest 9, paper ItemBarRenderTest 13; `:core:test :paper:test :tools:datagen:test` |
