---
id: PAR-INFRA-27
title: "TFC metal tools count as correct tools: CE tag expansion of the blocks' correct_tools includes tfp:metal/* tools"
area: INFRA
status: done
wave: 4
deps: [PAR-INFRA-22]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/java/net/dries007/tfc/common/TFCTiers.java"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/ce/templates/settings.yml"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/ce/templates/items.yml"
  - "tools/datagen/src/test/java/dev/tfp/datagen/CorrectToolTagsTest.java"
ui: false
in_game_steps: []
plan:
  - "Current (found in the PAR-INFRA-22 review): CraftEngine's `BlockStateUtils.isCorrectTool` compares the CE item id against the block's expanded `correct_tools`. Tags there expand through `AbstractItemManager.itemIdsByTag`: the vanilla members of the tag plus the CE items whose `settings.tags` list it (a CE item's vanilla material, e.g. `iron_pickaxe`, does not count). The rock template `tfp:settings/rock` lists only `#minecraft:pickaxes`, while every TFC metal pickaxe (`tfp:item/tool`, `AssetPipeline` `tool_tag` = `tfp:tools/<kind>`) carries only `tfp:tools/pickaxe`. So a rock-template block (raw/hardened rock, cobble, bricks, smooth, spikes, and whatever else uses it) drops nothing with any TFC pickaxe, through a CE `loot:` today and through the PAR-INFRA-22 gate once PAR-INFRA-21 delegates. Only vanilla pickaxes pass. TFC @ c3b4d9536 has no `needs_*` / `incorrect_for_*` block tags: any pickaxe harvests these blocks."
  - "Fix in the templates (no AssetPipeline change needed, it is held by PAR-INFRA-21): make `tfp:settings/rock` list `#tfp:tools/pickaxe` beside `#minecraft:pickaxes` (and do the same for any other template with `correct_tools`). Alternative if a vanilla tag is wanted on the items themselves: have `tfp:item/tool` emit the vanilla kind tag (`minecraft:pickaxes`, `minecraft:axes`, `minecraft:shovels`, `minecraft:hoes`) beside `${tool_tag}` in `settings.tags`; that needs a new template argument from `AssetPipeline`, so coordinate with PAR-INFRA-21 or take it after it. Rerun datagen (`generateContent`, `verifyContent`); never edit `content/generated` by hand."
  - "Then correct the PAR-INFRA-22 divergence row in `docs/porting/ledger.md` (drop the 'TFC metal pickaxes are not correct tools' clause) and the M4-TOOL-02 note in `plans/gaps.md` if it still says so."
  - "Done: `tfp:settings/rock` lists `#tfp:tools/pickaxe` beside `#minecraft:pickaxes` (the only template with `correct_tools`; the device template's `device_tool_tag` is a block mineable tag); datagen `CorrectToolTagsTest` (2 tests) expands every generated block's `correct_tools` like CE over the template-resolved YAML: all `tfp:metal/pickaxe/*` pass on raw/cobble/hardened rock and on every pickaxe block (>500), no other TFC tool kind does. Ledger PAR-INFRA-22 rows corrected. `plans/gaps.md` left as history (PROTOCOL 7.4). The `CorrectToolTest` javadoc in integration/craftengine still says 'until PAR-INFRA-27' (not owned; the rule test stays green)."
tests:
  - "datagen `CorrectToolTagsTest` on the generated content: expand every block's `correct_tools` the way CraftEngine does (vanilla members of a `#minecraft:` tag from the pinned vanilla tag list, plus every generated CE item whose `settings.tags` lists the tag, after template resolution) and assert that every `tfp:metal/pickaxe/*` is in the set of a generated raw rock (e.g. `tfp:rock/raw/granite`), and that no TFC tool of another kind (axe, shovel, hoe) is"
  - "integration/craftengine: `CorrectToolTest.aCeToolCountsForATagOnlyThroughItsSettingsTags` stays green (the expansion rule)"
done_criteria:
  - "A generated TFC metal pickaxe (`tfp:metal/pickaxe/bronze`) passes `isCorrectTool` on a generated raw rock state (CE's tag expansion over the generated YAML), and a TFC axe does not"
---

# PAR-INFRA-27: TFC metal tools count as correct tools: CE tag expansion of the blocks' correct_tools includes tfp:metal/* tools

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
