---
id: PAR-DATA-04
title: "Field guide: decide the form and convert the book"
area: DATA
status: blocked
wave: 35
deps: []
size: M
aliases: ["M6-GUIDE-01"]
tfc_refs:
  - "src/main/templates/data/tfc/patchouli_books/field_guide"
  - "src/main/resources/assets/tfc/patchouli_books"
files_owned:
  - "core/src/main/java/dev/tfp/core/guide/**"
  - "core/src/test/java/dev/tfp/core/guide/**"
  - "core/src/test/resources/dev/tfp/core/guide/**"
  - "paper/src/main/java/dev/tfp/paper/guide/**"
  - "paper/src/test/java/dev/tfp/paper/guide/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/guide/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/guide/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/FieldGuide.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FieldGuideTest.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:field_guide; right-click; screenshot"
plan:
  - "BLOCKED: PRD open question on the form (textured GUI book, written book, web guide). Recommendation for the owner: vanilla written books per category (works on every client, no art) + a web export."
  - "After the decision: convert pages (text, crafting/knapping/heating recipe pages as text tables), the book item, open via item use."
tests:
  - "tools/datagen: FieldGuideTest"
done_criteria:
  - "The guide covers every playable feature"
---

# PAR-DATA-04: Field guide: decide the form and convert the book

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
