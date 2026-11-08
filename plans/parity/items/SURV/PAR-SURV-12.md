---
id: PAR-SURV-12
title: "Dehydration self-test: a tfp:dehydration hit through the registry, armour reduces it"
area: SURV
status: done
wave: 9
deps: [PAR-SURV-10]
size: S
aliases: ["PAR-SURV-10 follow-up (its itest case)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/TFCDamageTypes.java"
  - "src/generated/resources/data/tfc/damage_type/dehydration.json"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/DehydrationDamageCase.java"
ui: false
in_game_steps: []
plan:
  - "From PAR-SURV-10: the bootstrap datapack ships tfp:dehydration and ThirstModule hurts with it (ThirstModule.DEHYDRATION, starve fallback). Its itest case needs a new file under itest/, outside PAR-SURV-10's files_owned."
  - "Self-test case (line in SelfTestSuite): RegistryAccess has tfp:dehydration (and grill, lobster_trap, pluck, pot) with TFC's values; minecraft:no_knockback contains tfp:dehydration. With no player online, hurt two zombies (one in full iron armour) with DamageSource.builder(tfp:dehydration) for the same amount: the armoured one loses less health (TFC: dehydration does not bypass armour)."
  - "Note: with no player online Paper's activation range keeps every mob inactive, so the equipment's attribute modifiers (vanilla applies them in the next active tick) never land; the case applies the iron items' default modifiers itself, then checks the armour attribute before the hits. The spawn chunk is force-loaded for the case."
  - "Done: itest DehydrationDamageCase (case `dehydration`, after player data in SelfTestSuite): the five tfp: types match TFC's JSON and sit in minecraft:no_knockback; 4.0 tfp:dehydration takes 3.936 from a bare zombie, 1.600 from one in full iron. Passed on Paper and Folia 1.21.11 and 26.3. The other failures in those runs (worldgen.terrain_generates, loot.vanilla_overrides, and the stale case count in SelfTestSuiteTest) were there before this item and belong to other items."
tests:
  - "itest: tfp:dehydration is registered and armour reduces its damage"
done_criteria:
  - "The self-test case passes on Paper and Folia 1.21.11 and 26.3"
---

# PAR-SURV-12: Dehydration self-test: a tfp:dehydration hit through the registry, armour reduces it

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
