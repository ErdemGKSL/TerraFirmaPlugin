---
id: PAR-INFRA-44
title: "Heavy weighted pressure plate is a TFC player block (wrought-iron recipe): no transparent carrier may draw placed plates empty"
area: INFRA
status: open
wave: 10
deps: [PAR-INFRA-42]
size: S
aliases: ["PAR-PROC-29 re-add (2026-11-13): tfp:scraping#base on heavy_weighted_pressure_plate[power=0] drew a placed vanilla heavy plate empty"]
tfc_refs:
  - "src/generated/resources/data/minecraft/recipe/heavy_weighted_pressure_plate.json"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/CarrierPolicyRecipeTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/CarrierPolicyWorldgenTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxCarrierTest.java"
ui: false
plan:
  - "Current: carrier-policy.json marks minecraft:heavy_weighted_pressure_plate `presence: absent` (\"owner: the placed-item carrier (power=0)\"), and minecraft:light_weighted_pressure_plate `absent` (\"no gold or iron plates in TFC\"). But TFC ships a shaped recipe for the heavy plate (src/generated/resources/data/minecraft/recipe/heavy_weighted_pressure_plate.json: two `c:ingots/wrought_iron`), which our datagen copies into content/data/minecraft/recipe/, so players can craft and place it. Any TRANSPARENT appearance on heavy_weighted_pressure_plate[power=0] draws every unpressed placed plate empty. Seen in game on 2026-11-13 (PAR-PROC-29 re-add, Paper 1.21.11 headless): a `setblock` heavy plate next to a light plate was invisible while tfp:scraping#base used power=0 (docs/verification/PAR-PROC-05/11-heavy-plate-drawn-empty.png)."
  - "Affected demands: tfp:scraping#base (PAR-PROC-29, TRANSPARENT, selector score 0.459) today; the owner rule of PAR-INFRA-13 / PAR-POT-01 (every placed item on heavy_weighted_pressure_plate[power=0], hitbox-only) and the hardcoded rows that pin it (charcoal pile layers=1, loose rock count=2, groundcover flat/twig/pixel_high in HitboxCarrierTest) later."
  - "Decide with the owner (the placed-item rule names this carrier): (a) mark the heavy plate `player` (or `natural` with a hide: power=0 has no other unpressed state, so a hide would show real plates pressed) and move the transparent carriers to minecraft:light_weighted_pressure_plate[power=0] (same 14 x 1 x 14 box, no collision; TFC has no gold-plate recipe: confirm the vanilla gold recipe is not craftable on our server, else it has the same problem), or (b) keep the heavy plate and make the plugin replace placed vanilla heavy plates by a CE block that draws the plate. Fix the light_weighted_pressure_plate note either way."
  - "Guard test CarrierPolicyRecipeTest: no block whose item is the result of a recipe in content/data (or TFC's recipe tree) is `presence: absent` without an allow-list entry and reason."
  - "Then PAR-PROC-29 re-adds its ScrapingBlock run line on the new carrier (its plan names the exact edits)."
tests:
  - "CarrierPolicyRecipeTest fails on today's policy (heavy_weighted_pressure_plate) and passes after the fix"
  - "Full datagen run: hitbox reports change only in the intended rows; GeneratedCarrierTest, HardcodedCarrierTest, CarrierPolicyWorldgenTest green"
done_criteria:
  - "No transparent or non-vanilla carrier look sits on a vanilla block players can craft and place in our world without a hide or an explicit owner-approved reason"
---

# PAR-INFRA-44: Heavy weighted pressure plate is a TFC player block

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
