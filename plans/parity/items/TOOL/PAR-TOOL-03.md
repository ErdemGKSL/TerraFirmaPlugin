---
id: PAR-TOOL-03
title: "In-game verification of metal tools and armour"
area: TOOL
status: open
wave: 19
deps: []
size: S
aliases: ["gaps: M4-TOOL-02 tools/armor unverified", "M4-TOOL-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/TFCArmorMaterials.java"
  - "src/main/java/net/dries007/tfc/common/TFCTiers.java"
files_owned:
  - "docs/verification/metal-tools-armor.md"
  - "itest/src/main/java/dev/tfp/itest/cases/MetalArmorCase.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/helmet/steel (and the other 3)"
  - "attribute Bot minecraft:armor get"
  - "F5 screenshot"
plan:
  - "Checklist from plans/gaps.md: CE loads metal.yml with 0 issues; full steel set armor 15 / toughness 4 (copper 9/0, blue steel 20/12, knockback 0.4); max_damage per piece; attribute modifiers replace iron's; rarity/enchantable present; `remove_components: [repairable]` works; worn layers and horse armour render; wolf refuses horse armour; knife/scythe/hammer mine their tags (hammer on #minecraft:logs)."
  - "Write an itest case asserting the attribute values; screenshots for the visuals."
tests:
  - "itest: MetalArmorCase"
done_criteria:
  - "Every checklist line verified or turned into a fix item"
---

# PAR-TOOL-03: In-game verification of metal tools and armour

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
