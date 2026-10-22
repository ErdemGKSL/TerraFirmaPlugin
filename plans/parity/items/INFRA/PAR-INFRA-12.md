---
id: PAR-INFRA-12
title: "Self-test case: item data PDC round trip on a real server (nested food/heat inside tfp:contents)"
area: INFRA
status: done
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
  - "Note: the self-test has no online player, so the player inventory save is stood in for by a server inventory copy (`setItem`/`getItem`, the vanilla-stack conversion every container and player save uses, on the global thread) plus Paper's inventory batch format (`serializeItemsAsBytes`); YAML and Paper item JSON are checked too. Test-only, no gameplay difference."
  - "Note: `itest/src/test/java/dev/tfp/itest/SelfTestSuiteTest.java` pins the case count; its one count line (16 to 17) and one name check were changed with the hub registration, as every new case needs."
  - "Done: `ItemDataRoundTripCase` (`itemdata.pdc_roundtrip`) registered in `SelfTestSuite`; vessel with salted bread (tfp:food), an empty slot and heated raw copper (tfp:heat), and a water jug (tfp:fluid via ItemFluids), plus foreign PDC keys, copied via bytes, items-bytes, yaml, json, inventory and all chained. `:itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper` passed (19 cases, 0 failures, 1 skipped; this case PASSED with food 48000 / heat 812.5 C read back)."
tests:
  - "itest: ItemDataRoundTripCase passes on Paper 1.21.11 (run delegated per PROTOCOL 5.3)"
done_criteria:
  - "Nested food and heat data survive Paper's real item serializer inside `tfp:contents`"
---

# PAR-INFRA-12: Self-test case: item data PDC round trip on a real server (nested food/heat inside tfp:contents)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
