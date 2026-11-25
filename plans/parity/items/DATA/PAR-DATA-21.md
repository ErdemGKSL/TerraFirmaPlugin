---
id: PAR-DATA-21
title: "Field guide translations: TFC's nine other book locales as per-player books (follow-up of PAR-DATA-04)"
area: DATA
status: done
wave: 36
deps: [PAR-DATA-04]
size: M
aliases: ["follow-up of PAR-DATA-04"]
tfc_refs:
  - "src/main/resources/assets/tfc/patchouli_books/field_guide"
files_owned:
  - "core/src/main/java/dev/tfp/core/guide/**"
  - "core/src/test/java/dev/tfp/core/guide/**"
  - "paper/src/main/java/dev/tfp/paper/guide/**"
  - "paper/src/test/java/dev/tfp/paper/guide/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/guide/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/guide/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/FieldGuide.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FieldGuideTest.java"
ui: false
in_game_steps: []
plan:
  - "PAR-DATA-04 converts only en_us. TFC ships the book in ja_jp, ko_kr, pt_br, ru_ru, tr_tr, uk_ua, zh_cn, zh_hk and zh_tw (each a full entries/ tree, possibly partial). Convert each locale with GuidePages (recipe names from that locale's TFC lang, English fallback per page), write guide/field_guide_<locale>.json, and let FieldGuideService pick the book by the player's client locale (Player#locale), falling back to English per entry. CJK text needs BookFont widths for the unifont glyphs (8/9 px) or a conservative count; check line widths in the content test per locale."
  - "Done: datagen FieldGuide.read(tfc, locale) converts every locale folder next to en_us (GuideNames/GuidePages take the locale's lang over the English one) into guide/field_guide_<locale>.json, failing when a locale lacks an entry English has; core FieldGuide.withFallback fills any missing entry from English; paper GuideData.load(loader, locale), FieldGuideService.Variant per locale, localeKey(Player#locale) (zh_CN -> zh_cn, same-language fallback, else English), the module loads the nine books. CJK/Cyrillic/Turkish letters count 9 px in BookFont (conservative; recorded as a divergence). Tests: core FieldGuideFallbackTest 2; datagen FieldGuideTest +1 (all nine locales convert with English structure); paper FieldGuideLocaleTest 2 (every locale of the real output fits 14 lines x 112 px and 100 pages; zh_cn player gets the Chinese book, a missing entry falls back to English), existing guide tests pass.",
tests:
  - "tools/datagen: every TFC locale converts; paper: a zh_cn player gets the Chinese book, a missing entry falls back to English"
done_criteria:
  - "Every TFC book locale is readable in game in the player's language"
---

# PAR-DATA-21: Field guide translations

Follow-up created by PAR-DATA-04. Follow `plans/parity/PROTOCOL.md`.
