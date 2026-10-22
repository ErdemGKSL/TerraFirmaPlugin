---
id: PAR-POT-04
title: "Small vessel: inventory, melting contents into metal, pouring"
area: POTTERY
status: open
wave: 24
deps: [PAR-INFRA-04, PAR-INFRA-03, PAR-POT-02]
size: M
aliases: ["M3-POT-02 (vessel)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/VesselItem.java"
  - "src/main/java/net/dries007/tfc/common/component/mold/Vessel.java"
  - "src/main/java/net/dries007/tfc/common/container/SmallVesselInventoryContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/SmallVesselInventoryScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/vessel/**"
  - "core/src/test/java/dev/tfp/core/vessel/**"
  - "core/src/test/resources/dev/tfp/core/vessel/**"
  - "paper/src/main/java/dev/tfp/paper/vessel/**"
  - "paper/src/test/java/dev/tfp/paper/vessel/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/vessel"
  - "open, insert small native copper"
  - "tfp debug warm Bot 1100 2000"
  - "hover"
plan:
  - "Right-click a fired vessel opens the 4-slot GUI (catalogue `m3-pot-02-small-vessel`); contents stored with PAR-INFRA-04; heating the vessel (in a firepit/forge) melts contents via VesselState into a fluid alloy; sneak right-click with a mold in offhand pours (MoldLikeAlloy screen PAR-POT-05)."
tests:
  - "core: VesselGoldenTest exists"
  - "paper: MockBukkit insert size limits"
done_criteria:
  - "Native copper in a vessel heated to 1080 C becomes 'Copper 100 mB' in the tooltip"
---

# PAR-POT-04: Small vessel: inventory, melting contents into metal, pouring

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
