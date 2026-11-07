---
id: PAR-SURV-10
title: "TFC damage types in the server registry (tfp:dehydration and the others), dehydration hits through it"
area: SURV
status: done
wave: 8
deps: [PAR-SURV-04]
size: S
aliases: ["PAR-SURV-04 follow-up"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/TFCDamageTypes.java"
  - "src/generated/resources/data/tfc/damage_type"
  - "src/generated/resources/data/minecraft/tags/damage_type/no_knockback.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/damagetypes/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/damagetypes/**"
  - "paper/src/main/java/dev/tfp/paper/player/ThirstModule.java"
  - "paper/src/test/java/dev/tfp/paper/player/ThirstModule*"
ui: false
in_game_steps: []
plan:
  - "Found by PAR-SURV-04: no damage type tfp:dehydration exists on the server (the generated no_knockback tag names it, but no damage_type JSON is shipped), so ThirstModule hurts with vanilla minecraft:starve and puts TFC's death message back itself. starve bypasses armour; TFC's dehydration does not (scaling when_caused_by_living_non_player, exhaustion 0, only no_knockback)."
  - "Datagen: export TFC's data/tfc/damage_type/*.json renamed (message_id tfc.* kept for the lang keys) into the bootstrap datapack tfp_seasonal_biomes (as PAR-PROC-24/PAR-INFRA-30 do), with the no_knockback tag beside it; one run line in Datagen.generate."
  - "ThirstModule.STARVE: look up tfp:dehydration in RegistryAccess (RegistryKey.DAMAGE_TYPE) and fall back to starve when absent; drop the death-message override once the type's own message_id gives it."
  - "The itest case (needs a new file under itest/, outside files_owned) is follow-up PAR-SURV-12. The death-message override stays as a fallback-only touch: the vanilla client has no translation for death.attack.tfc.* (ledger divergence)."
  - "Done: datagen damagetypes/DamageTypes (5 types + no_knockback in the bootstrap datapack, DamageTypesTest 3), ThirstModule hurts with tfp:dehydration from RegistryAccess (starve fallback, warning at enable) and keeps the server's death message with TFC's English fallback (ThirstModuleMockBukkitTest 15); Paper 1.21.11 self-test run: pack loaded, type resolved, no fallback warning."
tests:
  - "datagen: the five damage types and the tag land in the bootstrap datapack, equal to TFC's renamed"
  - "itest: a player at 0 thirst is hurt by tfp:dehydration and armour reduces it"
done_criteria:
  - "Dehydration damage is tfp:dehydration on a real server; the divergence row of PAR-SURV-04 (starve) is closed"
---

# PAR-SURV-10: TFC damage types in the server registry (tfp:dehydration and the others), dehydration hits through it

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
