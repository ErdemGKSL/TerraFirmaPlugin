---
id: PAR-WORLD-53
title: "Visual budget for wood plants: the tripwire pool has 3 free states, saplings, fallen leaves and twigs need 101"
area: WORLD
status: open
wave: 22
deps: [PAR-WORLD-10, PAR-WORLD-12]
size: S
aliases: ["PAR-WORLD-44 blocker: tripwire 123 (plants) + 101 (wood plants) = 224 of 126"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
files_owned:
  - "plans/parity/items/WORLD/PAR-WORLD-53.md"
ui: false
plan:
  - "Owner decision, then update PAR-WORLD-44 and the ledger: options measured on the generated output: (a) fallen leaves as hitbox-only carrier plus entity_renderer item display (like the snow pile): 0 tripwire states, one display per layer pair; (b) one fallen leaves look per wood (layers share a height) 20 states, still too many; (c) free tripwire states by moving PAR-WORLD-12 plants to entity_renderer; (d) saplings on a vanilla sapling carrier is impossible (vanilla saplings occur in no TFC world but the sapling pool has 8/8). Recommended: (a) for fallen leaves and saplings (display items, no collision outlines from the pressure plate carrier), the shared twig on the 3 remaining tripwire states or a button."
tests:
  - "Total tripwire demand over configuration/** <= 126, checked by a datagen test summing every generator"
done_criteria:
  - "A decision is recorded in the ledger and PAR-WORLD-44 is unblocked"
---

# PAR-WORLD-53: Visual budget for wood plants

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
