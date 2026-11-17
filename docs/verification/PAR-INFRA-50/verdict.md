# PAR-INFRA-50 in-game check

- Date: 2026-11-17
- Tree: d1a5fff (PAR-INFRA-50), checked out detached by the verify stage
- Server: Paper 1.21.11, CraftEngine self-hosted pack applied; client: vanilla 1.21.11 under Xvfb, bot `Bot`, RCON
- Run by a verification agent; screenshots judged by the main session (Opus)

## Steps

1. Survival Bot holding `minecraft:egg`: `tfp debug egg Bot fertilized 3`. Hover at GUI scale 2 and 3.
2. `tfp time add days 3`. Hover the same slot (stale, expected per NP-CLIENT-02), then move the egg so the server resends the stack and hover again.

## Evidence

- `scale2-egg-tooltip-states.png`, `scale3-egg-tooltip-states.png`: three tooltips stacked, top to bottom: after step 1, after the
  advance without a resend, after the resend.

## Criteria

| Criterion | Result |
|---|---|
| Egg: gold "Fertilized" then "Will hatch in 3 days" after the size line | PASS (both scales): name, size/weight line, gold `Fertilized`, white `Will hatch in 3 days` (TFC EggComponent order, `tfc.tooltip.fertilized` gold, `tfc.tooltip.egg_hatch` unstyled) |
| Egg after 3 days and a resend: "Will hatch today!" | PASS (both scales). Before the resend the slot still reads "in 3 days": NP-CLIENT-02 (tooltips refresh when the item packet is sent) |
| Javelins, pans, flower cuttings, lamps, crucible, blowpipe with glass | Not run: those items are still open; covered by `FeatureTooltipGoldenTest` / `FeatureTooltipMockBukkitTest` until they exist (in_game_steps 2) |
| Barrel contents screenshot | Deferred check tracked in PAR-INFRA-57 (PROTOCOL 6.5). The verify run could not obtain the barrel either: `ce` item give rejects the RCON console sender, so that step needs a player-run give |

## Build

`./gradlew check` at d1a5fff: every module green except `:dist:test` `ArchitectureTest.onlyThePacketLayerTouchesPacketEvents`, whose
violations are all in `dev.tfp.paper.heat` (HeatGlowHandler, HeatGlow, HeatTooltipDecoration), `dev.tfp.paper.itembar`
(ItemBarHandler, ItemBarRender) and `dev.tfp.paper.blocksound` (BlockSoundPacketHandler), none in PAR-INFRA-50's files. Those
packages are outside this item's `files_owned`; the fix is follow-up PAR-INFRA-58.

Verdict: PASS for the egg; the remaining lines wait for their items.
