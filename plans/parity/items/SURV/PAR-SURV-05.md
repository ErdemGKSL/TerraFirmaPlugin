---
id: PAR-SURV-05
title: "Health and damage: TFC health scale, physical damage types, entity and armour resistances"
area: SURV
status: open
wave: 8
deps: []
size: M
aliases: ["M3-SURV-04", "gaps: armor physical resistances"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/PhysicalDamage.java"
  - "src/main/java/net/dries007/tfc/util/PhysicalDamageType.java"
  - "src/main/java/net/dries007/tfc/util/data/EntityDamageResistance.java"
  - "src/main/java/net/dries007/tfc/util/data/ItemDamageResistance.java"
  - "src/generated/resources/data/tfc/tfc/entity_damage_resistance"
  - "src/generated/resources/data/tfc/tfc/item_damage_resistance"
  - "src/main/java/net/dries007/tfc/common/TFCDamageTypes.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/health/**"
  - "core/src/test/java/dev/tfp/core/health/**"
  - "core/src/test/resources/dev/tfp/core/health/**"
  - "paper/src/main/java/dev/tfp/paper/health/**"
  - "paper/src/test/java/dev/tfp/paper/health/**"
ui: true
in_game_steps:
  - "give Bot full steel armour via ce item give"
  - "summon minecraft:zombie ~ ~ ~2"
  - "observe health text drop vs unarmoured"
plan:
  - "Current: core/health rules golden-tested; HUD shows 850/850 from HealthDisplay."
  - "EntityDamageEvent hook (MONITOR-safe priority, entity thread): map the damage source to crush/pierce/slash (core PhysicalDamageType), apply the target's entity resistance and the worn armour's item resistances (data exported), then the player health modifier from nutrition."
  - "Forging bonus damage multiplier on the weapon (PAR-METAL-06 writes it; read 1.0 until then)."
  - "Natural regeneration replaced by HealthRegen (food/thirst gates); persist HealthState in PlayerData."
tests:
  - "core: HealthGoldenTest unchanged"
  - "paper: MockBukkit damage with a steel chestplate vs crush/pierce"
done_criteria:
  - "A zombie hit on a player in steel armour is reduced by TFC's resistance (debug log shows the factor)"
---

# PAR-SURV-05: Health and damage: TFC health scale, physical damage types, entity and armour resistances

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
