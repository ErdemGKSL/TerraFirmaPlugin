---
id: PAR-INFRA-42
title: "Carrier policy matches our worldgen: lily pad, sea pickle and other placed placeholders are not presence absent; guard test"
area: INFRA
status: open
wave: 9
deps: [PAR-INFRA-13]
size: S
aliases: ["PAR-PROC-29 review: the scraping block's transparent lily-pad carrier would draw every worldgen floating plant empty"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/CarrierPolicyWorldgenTest.java"
ui: false
plan:
  - "Current: carrier-policy.json marks minecraft:lily_pad `presence: absent` (\"TFC uses its own lily pad\"), so the selector may use it as is. But our worldgen places real lily pads: paper/.../worldgen/PlantBlocks.java `case FLOATING, FLOATING_FRESH -> Material.LILY_PAD` (duckweed, algae, lotus, pistia, sargassum, water canna, the water lilies; ledger placeholder row; SnowService treats it as ice-pileable). A TRANSPARENT appearance on it (PAR-PROC-29's tfp:scraping, selector score 0.554) writes minecraft:block/empty as the lily pad's model, so every floating plant would be an invisible 14 x 1.5 x 14 hitbox. PAR-PROC-29 has withdrawn its Datagen run line until this item is done."
  - "Same mismatch, no carrier using it yet: GroundcoverBlocks places minecraft:sea_pickle (clam, mollusk, mussel), minecraft:stone_button (loose rocks) and minecraft:oak_button (sticks); PlantBlocks places minecraft:pink_petals (flowerbeds); TfcTerrain places minecraft:mud (mud surface). All are `absent` in the policy. Decide per block: `natural` (usable only with a one-hop hide; a one-state block like lily_pad then has no hide target and drops out) or keep `absent` with a note when the placeholder is itself the carrier the CE block replaces it with (check the groundcover/button carriers before changing them)."
  - "Fix the policy, regenerate (`./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc`), diff reports/hitbox-carriers-*.md: only the intended rows may move (tfp:scraping#base should move to the next free carrier, today minecraft:heavy_weighted_pressure_plate[power=0] at 0.459, owned by the transparent placed-item carrier, which a TRANSPARENT look may share)."
  - "Guard test CarrierPolicyWorldgenTest: no block that the selector may use without a hide (`presence: absent`) is a material that paper worldgen places (read the Material.X names from paper/src/main/java/dev/tfp/paper/worldgen/{PlantBlocks,GroundcoverBlocks,TreeBlocks,VolcanoBlocks,TfcTerrain}.java as text, the datagen module cannot import paper), except an explicit allow-list with the reason."
  - "Then PAR-PROC-29 (blocked on this item) re-adds its ScrapingBlock run line in Datagen, updates its divergence row and redoes the outline check."
tests:
  - "CarrierPolicyWorldgenTest fails on today's policy (lily_pad, sea_pickle) and passes after the fix"
  - "Full datagen run: hitbox reports change only in the intended rows; GeneratedCarrierTest, HardcodedCarrierTest green"
done_criteria:
  - "No presence-absent carrier is a block our worldgen places (guard test); the scraping demand no longer picks minecraft:lily_pad"
---

# PAR-INFRA-42: Carrier policy matches our worldgen

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
