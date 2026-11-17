# PAR-INFRA-56 verification: GUI button tooltips without the size/weight line

- Date: 2026-11-17
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at a1a0d65; plugin jar built from 4136b34 (the PAR-INFRA-56 change;
  a1a0d65 only adds the unrelated PAR-INFRA-59 ore overlay change).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed, world folders deleted, restarted) with
  CraftEngine, BetterModel, BetterHud and PacketEvents as in `docs/testing/headless.md`; pack self-hosted on 127.0.0.1.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then a rejoin with `MC_OPTIONS="guiScale:3"`; the pack
  applied 3 s after each join.
- Run by the worker session itself (the verify-stage agent could not delete the primed world folders).

## Steps

1. `op Bot`; as Bot `/tfp debug firepitgui 400`; mouse over the thermometer column at GUI scale 2 (01).
2. `/tfp debug barrelgui` (5000 mB water); mouse over the Seal button (02) and the tank column (03) at GUI scale 2.
3. Rejoin at GUI scale 3, `/tfp debug firepitgui 400`, mouse over the thermometer (04).

## Evidence

- `01-firepit-400C-thermometer-hover-gui2.png`: tooltip is the single line "Very Hot***" (TFC heat text); no
  "⚖ ... ⇲ ..." line (before the fix: `docs/verification/PAR-HEAT-07/03-debug-1200C-tooltip-gui2.png`).
- `02-barrel-seal-button-hover-gui2.png`: tooltip "Seal" only.
- `03-barrel-tank-hover-gui2.png`: tooltip "5000 mB of Water" only.
- `04-firepit-400C-thermometer-hover-gui3.png`: scale 3, "Very Hot***" only.

## Criteria

| Criterion | Result |
|---|---|
| Firepit thermometer hover shows only the temperature text at GUI scale 2 | Pass (01; also scale 3, 04) |
| Button slots of the other screens show no size/weight line | Pass (02, 03: barrel seal button and tank) |
