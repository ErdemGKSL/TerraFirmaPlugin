---
id: PAR-INFRA-12
title: "Self-test case: item data PDC round trip on a real server (nested food/heat inside tfp:contents)"
area: INFRA
status: open
wave: 2
deps: [PAR-INFRA-04]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/ItemDataRoundTripCase.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-04 tests `tfp:contents` on MockBukkit with an in-memory slot codec, because MockBukkit's item serializer cannot write nested PDC containers; Paper's real `ItemStack.serializeAsBytes` path is untested."
  - "Add `ItemDataRoundTripCase` (registered in the SelfTestSuite hub): write a vessel holding a `tfp:food` bread and a `tfp:heat` ore through `ContentsPdc`, copy the stack through `ItemStack.serializeAsBytes`/`deserializeBytes` and a player inventory save, and read the nested food/heat back with `FoodPdc`/`HeatPdc`; also a `tfp:fluid` jug through `ItemFluids`."
tests:
  - "itest: ItemDataRoundTripCase passes on Paper 1.21.11 (run delegated per PROTOCOL 5.3)"
done_criteria:
  - "Nested food and heat data survive Paper's real item serializer inside `tfp:contents`"
---

# PAR-INFRA-12: Self-test case: item data PDC round trip on a real server (nested food/heat inside tfp:contents)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
