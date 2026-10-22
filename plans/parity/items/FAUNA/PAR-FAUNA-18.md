---
id: PAR-FAUNA-18
title: "Fishing: rod, hook, bait"
area: FAUNA
status: open
wave: 44
deps: [PAR-FAUNA-11]
size: M
aliases: ["M5-FISH-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/TFCFishingHook.java"
  - "src/main/java/net/dries007/tfc/common/component/Bait.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/GetHookedGoal.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/fishing/**"
  - "core/src/test/java/dev/tfp/core/fishing/**"
  - "core/src/test/resources/dev/tfp/core/fishing/**"
  - "paper/src/main/java/dev/tfp/paper/fishing/**"
  - "paper/src/test/java/dev/tfp/paper/fishing/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/fishing_rod/copper; bait; cast near fish"
plan:
  - "Vanilla fishing rod carrier with bait component; fish entities approach baited hooks (GetHookedGoal) instead of vanilla loot; pull strength and break (Hook)."
tests:
  - "core: FishingRulesTest exists"
done_criteria:
  - "Catching a fish with a baited rod"
---

# PAR-FAUNA-18: Fishing: rod, hook, bait

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
