# PAR-INFRA-61 (and PAR-INFRA-46) verification: static info tooltip lines in game

- Date: 2026-11-25
- Plugin jar: built from commit 6e783d9 (clean worktree `:dist:shadowJar`; includes PAR-INFRA-46 and PAR-TOOL-03).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0.
  Client: vanilla 1.21.11 under Xvfb, pack applied, Bot in survival. GUI scale 2 (`gui2-*`) and 3 (`gui3-*`).
- Run by the worker session itself; every screenshot was looked at and judged against TFC's lang strings
  (`tfc.tooltip.deals_damage.*`, `resists_damage`, `fuel_burns_at`) and `ClientForgeEventHandler`'s order.

| item hovered | what the tooltip shows | judgement |
|---|---|---|
| `tfp:metal/sword/copper` | name, "Very Heavy / Very Large" (size line), "Deals Slashing Damage" ("Deals" and "Damage" grey, "Slashing" white, TFC's `§7Deals §fSlashing§7 Damage`), melt line, attributes | PASS: damage line directly after the size line |
| `tfp:metal/chestplate/copper` | size line, "Resistances: Slashing 6%, Piercing 10%, Crushing 10%" (label grey, type names white), melt line, "+4 Armor" | PASS |
| 4 `minecraft:charcoal` | "Burns at Yellow White** for 1:48" (1800 ticks = 1.8 h; TFC prints the calendar delta) | PASS: fuel line after the size line |
| `tfp:peat` | "Burns at Dark Red for 2:30" (2500 ticks) | PASS |
| `tfp:rock/raw/granite` | "Felsic Igneous Intrusive" grey italic directly under the name, then the size line | PASS |
| `tfp:rock/bricks/chalk` | "Sedimentary" grey italic directly under the name | PASS |

Evidence: `gui2-sword.png`, `gui2-chestplate.png`, `gui2-charcoal.png`, `gui2-peat.png`, `gui2-granite.png`, `gui2-chalk.png` and the
same at scale 3 as `gui3-*.png` (also copied to `docs/verification/PAR-INFRA-46/`).

| Criterion | Result |
|---|---|
| Each of the four tooltip families shows TFC's text, colour and position at GUI scale 2 and 3 | **Pass** |
