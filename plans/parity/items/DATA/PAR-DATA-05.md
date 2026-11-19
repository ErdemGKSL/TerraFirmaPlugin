---
id: PAR-DATA-05
title: "Localisation: Paper lang loading, all TFC locales, plugin strings"
area: DATA
status: done
wave: 36
deps: []
size: M
aliases: ["X-I18N-01"]
tfc_refs:
  - "src/main/resources/assets/tfc/lang"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/i18n/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Locales.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/LocalesTest.java"
ui: false
in_game_steps: []
plan:
  - "Messages loads every locale and the player's client locale (ClientLocaleHandler); every `tfp.*` key translated at least in en_us and tr_tr; a test that every generated item_lang_key and every Messages key exists in en_us."
  - "Done 2026-11-19: Messages and datagen Locales (13 TFC locales, coverage vs en_us, LocalesTest) already existed; added the 24 missing tr_tr instrument strings so tr_tr covers every en_us key, and MessagesCoverageTest (paper) fails on a missing or extra tr_tr key. Not done: a source scan that every Messages key used in code exists in en_us."
tests:
  - "tools/datagen + paper key coverage tests"
done_criteria:
  - "Zero missing keys for en_us; tr_tr coverage reported"
---

# PAR-DATA-05: Localisation: Paper lang loading, all TFC locales, plugin strings

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
