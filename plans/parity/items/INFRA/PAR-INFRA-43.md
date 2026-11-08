---
id: PAR-INFRA-43
title: "Button and mud carriers vs our worldgen placeholders: no small-ground-piece or charcoal-pile carrier draws over placed loose-rock, stick or mud placeholders"
area: INFRA
status: open
wave: 10
deps: [PAR-INFRA-42]
size: S
aliases: ["PAR-INFRA-42 follow-up: stone_button, oak_button and mud stay presence absent on CarrierPolicyWorldgenTest's allow-list"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/CarrierPolicyWorldgenTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/VanillaCandidatesTest.java"
ui: false
plan:
  - "Current (PAR-INFRA-42): paper/worldgen/GroundcoverBlocks places minecraft:stone_button (loose rocks) and minecraft:oak_button (sticks), TfcTerrain places minecraft:mud (mud surface). The carrier policy keeps all three `presence: absent` on CarrierPolicyWorldgenTest's allow-list because HitboxCarrierTest pins oak_button for tfc:rock/loose[count=1], tfc:ore/small and tfc:groundcover[shape=small] and mud for tfc:charcoal_pile[layers=8], and VanillaCandidatesTest checks mud against docs/research/vanilla-fullcube-supply.csv (absent). No demand uses them today; the guard test fails as soon as one draws a non-vanilla look on them."
  - "Decide before the first groundcover / loose rock / charcoal pile demand ships: either the worldgen places the CE blocks instead of these placeholders (then the allow-list entries stand, with that reason), or the policy marks the blocks `reserved` (no integer property, no hide target) and the pins move to the next free carriers (re-derive from the reports). Mud: a model on the mud carrier would draw every generated mud surface as a charcoal pile."
  - "Remove the allow-list entries that no longer apply; regenerate and diff reports/hitbox-carriers-*.md."
tests:
  - "CarrierPolicyWorldgenTest with an empty (or justified) allow-list; HitboxCarrierTest pins updated to the re-derived carriers"
done_criteria:
  - "No allocation or pinned selector carrier is a worldgen placeholder that real instances would show with the carrier's look"
---

# PAR-INFRA-43: Button and mud carriers vs our worldgen placeholders

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
