---
id: PAR-PROC-27
title: "Tool item build guards: self-test gives every item with a minecraft:tool component; datagen check that tool rules name only shipped block tags"
area: PROC
status: done
wave: 8
deps: [PAR-PROC-24]
size: S
aliases: ["follow-up of PAR-PROC-23 (re-review of the gem saw give failure)"]
tfc_refs:
  - "src/generated/resources/data/tfc/tags/block/mineable/glass_saw.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/knife.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/hammer.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/scythe.json"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/ToolItemsBuildCase.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/ToolRuleTagsTest.java"
ui: false
in_game_steps: []
plan:
  - "Found 2026-11-05 by the re-review of PAR-PROC-23: `ce item give Bot tfp:gem_saw` fails with 'Missing tag: tfp:mineable/glass_saw in minecraft:block' because vanilla's codec rejects a minecraft:tool rule naming a block tag the server does not have. The generated CE content has four such tags: tfp:mineable/glass_saw (gem saw, material/tool.yml) and tfp:mineable/knife, hammer, scythe (9 metal items each, metal.yml). PAR-PROC-24 fixes them; this item adds the guards so a new tool rule cannot bring the crash back."
  - "Datagen test: walk every generated CE item, collect each block tag in a minecraft:tool rule (and in tool templates' mineable_tag arguments), and fail unless the tag is a vanilla tag, a c: tag the server has, or a tag shipped in the bootstrap datapack by datagen."
  - "Self-test case (register in SelfTestSuite): for every CE item id whose built stack carries a minecraft:tool component (or whose config uses a tool template), build the item stack through the CE API on the main/global thread and give it to a fake or test inventory; fail with the id and exception on any error."
  - "Done: ToolRuleTagsTest (3: shipped/vanilla tags pass via template, nested template, raw component and data.tool; unshipped tfp:, c:, unknown minecraft: and unfilled placeholders fail; real checkout passes and fails once tfp:mineable/knife is removed from the datapack) and self-test case items.tool_build (ToolItemsBuildCase, registered in SelfTestSuite; Paper 1.21.11 run 2026-11-07 passed: 91 tool items of 2863 built and given, canary shows the server rejects an unshipped #tfp: rule tag). Note: itest SelfTestSuiteTest already expected 22 cases while the suite held 24 before this item (now 25); its count belongs to that test's owner."
tests:
  - "tools/datagen ToolRuleTagsTest: no generated tool rule names a block tag missing from the server or the bootstrap datapack"
  - ":itest:runSelfTest ToolItemsBuildCase: every item with a minecraft:tool component builds (gem saw, every metal knife, hammer, scythe, saw)"
done_criteria:
  - "Both guards pass on the content after PAR-PROC-24, and each fails when a tool rule names an unshipped tfp: tag"
---

# PAR-PROC-27: Tool item build guards: self-test gives every item with a minecraft:tool component; datagen check that tool rules name only shipped block tags

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
