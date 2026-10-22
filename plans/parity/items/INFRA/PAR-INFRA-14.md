---
id: PAR-INFRA-14
title: "NeoForge common tags: datagen emits the vanilla members of the c: tags (neoforge_common_tags.json)"
area: INFRA
status: blocked
wave: 2
deps: [PAR-INFRA-07]
size: S
aliases: ["follow-up of PAR-INFRA-07 (ledger divergence: NeoForge ships the c: common tags)"]
tfc_refs:
  - "src/generated/resources/data/c/tags"
  - "src/generated/resources/data/tfc/tags/item/deals_piercing_damage.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/glass_saw.json"
  - "src/generated/resources/data/tfc/tags/block/pet_sits_on.json"
  - "src/generated/resources/data/minecraft/tags/block/bamboo_plantable_on.json"
  - "gradle.properties"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/CommonTags.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/CommonTagsTest.java"
  - "tools/datagen/build.gradle.kts"
  - "settings.gradle.kts"
  - "gradle/libs.versions.toml"
  - "NOTICE"
ui: false
in_game_steps: []
plan:
  - "Blocked: core's DataLoader rejects the new top-level file (`data/neoforge_common_tags.json: not inside <namespace>/<folder>/`), which fails ContentDataLoadTest, FoodDefinitionsTest, ItemHeatDefinitionsTest, KnappingDefinitionsTest, ClimateRangeDataTest, RegistryServiceTest and TagsContentDataMockBukkitTest and would break the plugin's data load. The fix is one entry in `core/src/main/java/dev/tfp/core/registry/DataLoader.java` `ROOT_FILES` (add `\"neoforge_common_tags.json\"`, e.g. via a KnownIds/TagRules-free constant in core), outside files_owned. Add that file to files_owned (or land it in another item) and rerun. The rest was built and passed in the worker's worktree: CommonTags reading the NeoForge 21.1.234 universal jar (Gradle config `neoforgeJar`, exclusive repo maven.neoforged.net for net.neoforged:neoforge, `-Dtfp.neoforgeJar` on generateContent/verifyContent/test), one Datagen run line, CommonTagsTest (6 tests, green), generateContent + verifyContent green; open points then: the jar SHA-256 can only be recorded inside the table (TfcCheckout inputs are not exposed) and third-party.json is fixed to `[]` by a Datagen line."
  - "Current: TFC's tags nest NeoForge common tags (`#c:rods/wooden`, `#c:tools/bow`, `#c:glass_blocks`, `#c:chests`, `#c:cobblestones`, `#c:sands`, `#c:leathers`, `#c:feathers`, `#c:dusts/redstone`, `#c:dyes/black`, `#c:tools/shear`, ...). On NeoForge their vanilla members come from NeoForge's own `data/c/tags`; TFC's files only add to them. Paper ships no `c:` tags. PAR-INFRA-07's runtime (`paper/tags/TagRules`) already reads `data/neoforge_common_tags.json` as the base layer of those tags and is tested with a synthetic table; nothing emits the real table, so `minecraft:stick` is not in `#c:rods/wooden` and so on (ledger divergence row on `c:` tags lists every affected tag and recipe count)."
  - "Input: the NeoForge universal jar of the version TFC pins (21.1.234 at c3b4d9536; read it from `/opt/tfc/gradle.properties` or pin it next to the TFC commit). Resolve it as a Gradle dependency (`net.neoforged:neoforge:<v>:universal`, repository https://maven.neoforged.net/releases with a content filter to `net.neoforged`) in a datagen-only configuration, pass its path to `generateContent`, `verifyContent` and the datagen tests as a system property, and record its SHA-256 in the manifest inputs. Fixture checkouts without the property skip the step."
  - "Generator `CommonTags.run(...)`: read every `data/c/tags/{item,block,fluid,entity_type}/**.json` of the jar (not `worldgen/`), keep entries in tag-file form (`ns:id`, `#ns:tag`, `{id, required}`), drop entries whose namespace is not `minecraft` or `c` (mod-optional ids), and write `data/neoforge_common_tags.json` = {\"source\": \"NeoForge <v> universal jar, data/c/tags\", \"license\": \"LGPL-2.1-only (NeoForge); vanilla ids only\", \"item\": {tag: [entries]}, \"block\": {...}, \"fluid\": {...}, \"entity_type\": {...}} with sorted keys. Add one `run` line in `Datagen.generate`. Check that the core DataLoader and `KnownIds.fromDataRoot` ignore the new top-level file. Add the NeoForge attribution to NOTICE and keep `:checkNotice` green."
  - "Then close the ledger divergence row on `c:` tags (\"NeoForge ships the `c:` common tags\"): its runtime part is done when the real table ships; keep only the datagen-export sentence if anything remains."
tests:
  - "tools/datagen CommonTagsTest on the real jar: `c:rods/wooden` holds `minecraft:stick`, `c:tools/bow` holds `minecraft:bow`, `c:glass_blocks` reaches `minecraft:glass`, `c:chests` reaches `minecraft:chest`, `c:cobblestones` reaches `minecraft:cobblestone`, `c:sands` reaches `minecraft:sand`; every one of the 16 `c:` tags in `external_tags.json` and every `c:` tag TFC adds to has an entry; deterministic output"
  - "paper TagsContentDataMockBukkitTest (run with -Dtfp.contentData on fresh datagen output): its common-tag block runs (not skipped): `minecraft:stick` in `#c:rods/wooden`, `minecraft:bow` in `tfc:deals_piercing_damage`, glass in `tfc:mineable/glass_saw`, `minecraft:sand` not in `#minecraft:bamboo_plantable_on`"
done_criteria:
  - "`data/neoforge_common_tags.json` is generated from the pinned NeoForge jar, verifyContent covers it, the real-data tag test passes its `c:` assertions, and the ledger `c:` divergence row is closed"
---

# PAR-INFRA-14: NeoForge common tags: datagen emits the vanilla members of the c: tags (neoforge_common_tags.json)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Ordering note: PAR-INFRA-06 (recipe runtime, wave 3) must not be called done before this item: without the table,
vanilla sticks, leather, feathers, redstone, black dye and shears never match TFC's `c:` recipe ingredients. Its
`deps` list is not edited here (another item's file); the orchestrator should add `PAR-INFRA-14` to it.
