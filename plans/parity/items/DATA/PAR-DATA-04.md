---
id: PAR-DATA-04
title: "Field guide: decide the form and convert the book"
area: DATA
status: done
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
  - "OWNER DECISION (2026-11-18, delegated to the manager: decide without asking): take the recommendation: vanilla written books per category (works on every client, no art) plus a web export; no textured GUI book. Record the form in the ledger and ARCHITECTURE.md/PRD.md open-question line in the same change."
  - "BLOCKED: PRD open question on the form (textured GUI book, written book, web guide). Recommendation for the owner: vanilla written books per category (works on every client, no art) + a web export."
  - "After the decision: convert pages (text, crafting/knapping/heating recipe pages as text tables), the book item, open via item use."
  - "Form recorded 2026-11-25 in ARCHITECTURE.md section 9 (Field guide row), PRD.md section 10 and the ledger: vanilla written books (a contents book with category page links and entry links, one book per entry, volumes over 100 pages) plus the web export content/guide/field_guide.html. Datagen (FieldGuide + datagen/guide) converts every page (recipes as text tables, $(cfg:...) with TFC's default config); core/guide lays the pages out with the vanilla font widths; paper/guide (FieldGuideModule) opens the contents when tfp:field_guide (crafted from one book) is used."
  - "Pending in-game check (container cannot run client)"
  - "Follow-ups: PAR-DATA-20 (BOOK inventory tab), PAR-DATA-21 (the nine other book locales)."
  - "Done: PAR-DATA-04 commit (Field guide: decide the form and convert the book); tests FieldGuideTest (15, real checkout + fixtures), GuideConfigTest (4), core guide PatchouliMarkupTest/BookPaginatorTest/GuideBooksTest (19), paper FieldGuideContentTest (4, real generated guide) and FieldGuideMockBukkitTest (5); in-game screenshot check pending (no client in the container)."
tests:
  - "tools/datagen: FieldGuideTest"
done_criteria:
  - "The guide covers every playable feature"
---

# PAR-DATA-04: Field guide: decide the form and convert the book

> see_not_parity: NP-GUIDE-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
