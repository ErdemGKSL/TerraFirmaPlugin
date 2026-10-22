---
id: PAR-INFRA-07
title: "Tag runtime: TFC item/block/fluid/entity tags resolved for CE and vanilla ids"
area: INFRA
status: done
wave: 1
deps: []
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/TFCTags.java"
  - "src/generated/resources/data/tfc/tags"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/tags/**"
  - "paper/src/test/java/dev/tfp/paper/tags/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/tag loads tag JSON; `external_tags.json` maps vanilla tags; no Paper lookup from a live ItemStack/Block."
  - "Add `Tags` service: `is(ItemStack, tagId)`, `is(Block/CE state, tagId)`, `is(EntityType, tagId)` with CE ids via the CustomItems/CustomBlocks adapters and vanilla tags via Bukkit Tag; snapshot per data reload."
  - "Done: paper/tags (Tags service via TagsModule, TagSnapshot, TagRules include/remove graph, ServerTags over Bukkit.getTag); 10 MockBukkit tests in paper/src/test/java/dev/tfp/paper/tags (synthetic and the shipped data); ledger row and divergence. PAR-INFRA-06 gets it with services().require(Tags.class)."
  - "Review fix: Paper ships no c: tags, so NeoForge's vanilla members of them (minecraft:stick in #c:rods/wooden, bow in #c:tools/bow, glass in #c:glass_blocks, sand in #c:sands, ...) were lost. TagRules now reads data/neoforge_common_tags.json as the base layer of foreign tags (server tag OR table), tested with a synthetic NeoForge-shaped table; datagen emitting the real table needs a pinned NeoForge jar input outside this item's files: follow-up PAR-INFRA-14 (must land before PAR-INFRA-06 is done). Also: remove guard for vanilla ids listed directly, retrying rules swap, conditional-file count asserted zero on real data."
tests:
  - "paper: MockBukkit tests for a CE id in a TFC tag, a vanilla id via a vanilla tag, nested tags"
done_criteria:
  - "Tag lookups used by PAR-INFRA-06 tests; reload swaps the snapshot atomically"
---

# PAR-INFRA-07: Tag runtime: TFC item/block/fluid/entity tags resolved for CE and vanilla ids

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
