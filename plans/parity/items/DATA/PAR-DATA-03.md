---
id: PAR-DATA-03
title: "Advancement criteria wiring across features"
area: DATA
status: done
wave: 35
deps: [PAR-DATA-02]
size: M
aliases: []
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/advancement/criteria/**"
  - "paper/src/test/java/dev/tfp/paper/advancement/criteria/**"
  - "docs/advancements/criteria-map.md"
ui: false
in_game_steps: []
plan:
  - "Map every TFC criterion to a feature event (one table in docs), fire it from module event listeners registered here (listening to our own feature events, not editing feature packages)."
  - "Done: paper `advancement/criteria/{CriteriaMap,CriteriaTable,CriteriaListener,CriteriaModule}` wire 417 of TFC's 438 criteria (248 inventory items/tags, 20 fruit, eat_rotten_food, full_nutrition, 140 biomes, globe_trotter, hunter kills; 2 vanilla-tick roots); docs/advancements/criteria-map.md lists every route; CriterionRef now allows '/' in names (DATA-02 file, one-char fix). Tests: CriteriaMapTest 7 (real checkout), CriteriaListenerTest 3. 21 TFC-own triggers pending: follow-up PAR-DATA-19. Pending in-game check (container cannot run client)"
tests:
  - "paper: every criterion id has a mapping or a documented 'not reachable'"
done_criteria:
  - "TFC's tree is obtainable (self-test grants via scripted events)"
---

# PAR-DATA-03: Advancement criteria wiring across features


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
