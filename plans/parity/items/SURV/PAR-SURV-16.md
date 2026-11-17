---
id: PAR-SURV-16
title: "Food items and food tooltip in-game verification: the checks PAR-SURV-01 and PAR-SURV-02 closed without (sampled gives, TFC tooltip lines)"
area: SURV
status: open
wave: 17
deps: [PAR-SURV-01, PAR-SURV-02, PAR-INFRA-05]
size: S
aliases: ["parity audit 2026-11-17: PAR-SURV-01 and PAR-SURV-02 are done with ui: true and point at docs/verification/PAR-SURV-01/ and docs/verification/PAR-SURV-02/, which were never written; PAR-SURV-02's only criterion is the screenshot"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java"
  - "src/main/java/net/dries007/tfc/common/component/food/FoodComponent.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "docs/verification/PAR-SURV-01/**"
  - "docs/verification/PAR-SURV-02/**"
  - "docs/verification/PAR-SURV-16/**"
ui: true
in_game_steps:
  - "for a sampled 10 ids (tfp:food/beef, barley_bread, red_apple, cooked_cod, cheese, tomato, rice_grain, soup bowl through the pot output if loaded, tfp:jar/plum_jam, tfp:food/fresh_seaweed): ce item give Bot <id> 1; screenshot the hotbar with each name and icon at GUI scale 2"
  - "hover tfp:food/beef: screenshot the tooltip (name, 'Expires on: ...' or TFC's expiry style, nutrition block, size/weight line) at GUI scale 2 and 3"
  - "put the beef in a barrel with brine until brined (tfp debug barrel fill 1000 tfp:brine; seal; tfp time add 1d); hover: the Brined trait line"
  - "tfp time add 30d; hover the plain beef: Rotten in red and no nutrition block"
  - "hover tfp:jar/plum_jam: grey italic Sealed line"
plan:
  - "PAR-SURV-01 (food items) and PAR-SURV-02 (food tooltip) were set done with the in-game step marked 'the workflow's screenshot step'; no evidence exists (PAR-INFRA-05's verdict even notes ce item give not stamping food while PAR-SURV-01 was open). Run the steps on Paper 1.21.11 with the headless client (docs/testing/headless.md), compare every line with TFC (FoodCapability.addTooltipInfo order: expiry, traits, nutrition with TFC's colours) and write verdict.md under docs/verification/PAR-SURV-16/ (copy the crops for the old ids into their folders so the items' paths resolve)."
  - "A failure is fixed through a follow-up item owning paper/food/** or the datagen food generator; do not reopen PAR-SURV-01/02 unless a criterion of theirs breaks."
tests:
  - "none (in-game check only)"
done_criteria:
  - "The sampled 10 foods give with TFC names and textures; the beef tooltip, brined trait, rotten state and jar Sealed line match TFC's text and colours at GUI scale 2 and 3"
  - "verdict.md with HEAD commit, versions, steps and pass/fail per criterion in docs/verification/PAR-SURV-16/"
---

# PAR-SURV-16: Food items and food tooltip in-game verification

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
