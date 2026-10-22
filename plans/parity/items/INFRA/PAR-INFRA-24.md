---
id: PAR-INFRA-24
title: "Packaging: :dist:shadowJar always ships the generated content (content/data/id_map.json) without share-build.sh"
area: INFRA
status: open
wave: 4
deps: []
size: S
aliases: ["follow-up of PAR-INFRA-13 (in-game run at b00239a: in-game problem 6)"]
tfc_refs: []
files_owned:
  - "content/build.gradle.kts"
  - "dist/build.gradle.kts"
ui: false
in_game_steps: []
plan:
  - "Current: the PAR-INFRA-13 in-game run found that `./gradlew :dist:shadowJar` alone builds a plugin jar without `content/data/id_map.json` (and the rest of the generated content); it works only after `tools/worktree/share-build.sh`, which links `content/generated` to a store entry holding `content/...`. `content/build.gradle.kts` packs `from(\"generated\")` with no dependency on `:tools:datagen:generateContent` and no check that `content/generated/content` exists, so an empty or missing tree silently gives a content-less jar."
  - "Fix: make the content jar fail (or depend on generateContent when `-Ptfc.checkout` is set) when `content/generated/content/data/id_map.json` is missing, and add a dist check (like `verifyIntegrationServices`) that the shaded jar contains `content/data/id_map.json` and `content/craftengine/tfp/pack.yml`. Do not copy the ~110 MB tree into build/ (see the comment in content/build.gradle.kts)."
tests:
  - "dist: a verification task fails on a jar without content/data/id_map.json and passes on a normal build"
done_criteria:
  - "`./gradlew :dist:shadowJar` in a fresh worktree either ships the content or fails with a message naming generateContent / share-build.sh; never a silent content-less jar"
---

# PAR-INFRA-24: Packaging: :dist:shadowJar always ships the generated content (content/data/id_map.json) without share-build.sh

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
