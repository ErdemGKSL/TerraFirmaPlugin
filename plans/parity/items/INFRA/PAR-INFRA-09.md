---
id: PAR-INFRA-09
title: "Block and entity loot runtime: TFC loot tables evaluated on Paper"
area: INFRA
status: open
wave: 3
deps: [PAR-INFRA-07]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/loot"
  - "src/main/resources/data/tfc/loot_table"
  - "src/generated/resources/data/tfc/loot_table"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/loot/**"
  - "paper/src/test/java/dev/tfp/paper/loot/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/gather has a small loot interpreter (match_tool, random_chance, block_state_property, alternatives, set_count) and core/hunt has animal yields; CE blocks use `tfp:loot_table/self`."
  - "Add a Paper `LootService` that evaluates TFC block loot tables for CE blocks on break (tool from the player's hand, explosion_decay, TFC conditions: is_isolated, is_burnt_out, not_predated, is_male, is_sluice; functions: copy_fluid, rotten, apply_stack_size, crop yield, animal yield)."
  - "Generate CE `loot:` entries that delegate to `tfp:loot` when a table needs a TFC condition, keep plain CE loot otherwise."
tests:
  - "core: golden rows for each TFC condition/function"
  - "paper: MockBukkit break test with tool and explosion"
done_criteria:
  - "A loose rock and a leaves block drop per their TFC tables in a self-test"
---

# PAR-INFRA-09: Block and entity loot runtime: TFC loot tables evaluated on Paper

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
