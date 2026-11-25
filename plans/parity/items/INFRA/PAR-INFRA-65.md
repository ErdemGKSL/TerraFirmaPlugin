---
id: PAR-INFRA-65
title: "Block flammability in-game verification and the flammability.spread self-test case"
area: INFRA
status: open
wave: 48
deps: [PAR-INFRA-47]
size: S
aliases: ["moved from PAR-INFRA-47 (2026-11-25, PROTOCOL 6.5): the screenshot steps and the itest case need a running server"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java"
files_owned:
  - "docs/verification/PAR-INFRA-65/**"
  - "itest/src/main/java/dev/tfp/itest/cases/Flammability*.java"
ui: true
in_game_steps:
  - "Build a 3x3 of tfp:wood/log/oak with tfp:wood/leaves/oak above it on stone; light the bottom with flint and steel; screenshots at 0, 10 and 30 s: fire spreads and consumes them"
  - "Place a tfp:wood/log/oak next to lava (one gap); wait 60 s; screenshot: it ignites (ignitedByLava)"
  - "Same setup with tfp:rock/raw/granite and a metal block: no fire spread"
plan:
  - "Add itest case flammability.spread (light a log and leaves cluster, burns within 60 s; granite cluster does not) and run the steps above. Datagen puts burnable/burn_chance/fire_spread_chance on the real CE blocks (PAR-INFRA-47)."
tests:
  - "itest: flammability.spread; screenshots + verdict.md in docs/verification/PAR-INFRA-65/"
done_criteria:
  - "Logs, leaves (and later thatch, planks) burn and catch from lava at vanilla pace for the same odds; granite and metal do not"
---

# PAR-INFRA-65: Block flammability in-game verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
