---
id: PAR-SURV-02
title: "Food tooltip: expiry/rotten, traits, nutrition lines"
area: SURV
status: done
wave: 6
deps: [PAR-INFRA-05, PAR-SURV-01]
size: S
aliases: ["M3-SURV-01 (2)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java"
  - "src/main/java/net/dries007/tfc/config/FoodExpiryTooltipStyle.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/food/FoodTooltip*"
  - "paper/src/test/java/dev/tfp/paper/food/FoodTooltip*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:food/mutton (hotbar slot 0; the give stamps it at the current date)"
  - "ce item give Bot tfp:food/cooked_mutton (hotbar slot 1), then add two traits to its existing tfp:food record (set_custom_data merges, so tfp:created stays): item modify entity Bot hotbar.1 {\"function\":\"minecraft:set_custom_data\",\"tag\":\"{PublicBukkitValues:{\\\"tfp:food\\\":{\\\"tfp:traits\\\":[\\\"tfp:salted\\\",\\\"tfp:charcoal_grilled\\\"]}}}\"}"
  - "Hover slot 0 (fresh): 'Raw Mutton', 'Expires on: ... (in ...)' dark green, 'Nutrition:' grey, ' - Protein: 1.5' red"
  - "Hover slot 1 (traits): expiry line, 'Nutrition:', ' - Saturation: 10%' grey, ' - Protein: 2.5' red, then 'Salted' in the default white and 'Charcoal Grilled' in red (decay modifier 1.25 > 1); brined would not be red (TFC modifier 1.0)"
  - "Only now: tfp time add days 30 (the items must already exist, otherwise they are stamped at the new date and stay fresh)"
  - "Hover slot 0 (rotten): 'Raw Mutton' then 'Rotten!' in red, no expiry line, no Nutrition block"
  - "Hover slot 1 (rotten with traits): 'Rotten!' in red, no Nutrition block, the 'Salted' and 'Charcoal Grilled' lines still listed (TFC lists traits on rotten food)"
  - "Save the captures under docs/verification/PAR-SURV-02/"
plan:
  - "Register a TooltipContributor: 'Expires on <date>' / 'Rotten' per FoodExpiryTooltipStyle, trait lines (Salted, Brined...), nutrition lines (hunger, saturation, water, the 5 nutrients) as TFC shows with shift (no shift on vanilla: always shown, divergence row)."
  - "Note: TFC's IFood.addTooltipInfo shows no hunger line (only saturation, water and the nutrients), so none is added here."
  - "Done: paper/food/FoodTooltipContributor (replaces the default `food` contributor by id; expiry/rotten via FoodExpiryContributor, the 1-in-256 rotten 'Ewwww' line, nutrition block from the food definition unless rotten, trait lines red when decay > 1, grey italic Sealed/Unsealed on tfp:jar/*) and FoodTooltipModule (module food_tooltip, service line after tooltip); lang keys en_us + tr_tr. Tests: FoodTooltipContributorTest 6 (NBT-level fresh, rotten, traits, nutrient colours, jar lore, tr_tr). Ledger feature row + 3 divergences (Shift always, Ewwww hash, Locale.ROOT decimals), jar lore divergence updated; ARCHITECTURE §10.1 Food bullet. In-game check: the workflow's screenshot step, evidence in docs/verification/PAR-SURV-02/."
tests:
  - "paper: NBT-level tooltip test for fresh, rotten, trait"
done_criteria:
  - "Screenshot of a food tooltip matching TFC's lines"
---

# PAR-SURV-02: Food tooltip: expiry/rotten, traits, nutrition lines

> see_not_parity: NP-CLIENT-04 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
