---
id: PAR-METAL-13
title: "Channels and mold tables: pouring flow"
area: METAL
status: done
wave: 34
deps: [PAR-METAL-12, PAR-INFRA-10]
size: M
aliases: ["M4-CRUC-01 (channels)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/ChannelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/MoldTableBlock.java"
  - "src/main/java/net/dries007/tfc/util/ChannelFlow.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java"
  - "src/main/java/net/dries007/tfc/network/PourFasterPacket.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/channel/**"
  - "core/src/test/java/dev/tfp/core/channel/**"
  - "core/src/test/resources/dev/tfp/core/channel/**"
  - "paper/src/main/java/dev/tfp/paper/channel/**"
  - "paper/src/test/java/dev/tfp/paper/channel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/channel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/channel/**"
  - "content/generated/content/craftengine/tfp/configuration/channel/**"
ui: true
in_game_steps:
  - "build crucible -> channels -> 2 mold tables with ingot molds; pour; screenshot"
plan:
  - "Channel path planned async (ChannelFlow.plan) when the crucible pours; molten flow visual along the path for the pour duration; molds on mold tables fill; fast pour via sneak (replaces PourFasterPacket)."
  - "Done: PAR-METAL-13 commit. Blocks tfp:channel and tfp:mold_table (datagen/channel/ChannelBlocks: 128 + 16 states, TFC shapes in tools/datagen hitbox/TfcShapes + HitboxDemands, item-display looks, our molten flow models), devices paper/channel (ChannelDevice, MoldTableDevice, PourService, ConnectionRules, MoldTableDisplays, debug commands), core/channel (ChannelData, MoldTableData, FlowCheck, BrokenLinks, MoldTableRules) over the existing core/crucible/ChannelFlow. Tests: core ChannelFlowRulesTest 13 + MoldTableRulesTest 4 + ChannelFlowGoldenTest, paper ChannelMockBukkitTest 11, datagen ChannelBlocksTest 6. In-game check docs/verification/PAR-METAL-13/ (pass). Not here: the fast pour shortcut (TFC's PourFasterPacket speeds the molds in the crucible's own slots: PAR-METAL-23); the crucible's connection nubs: follow-up PAR-METAL-24."
tests:
  - "core: ChannelFlowGoldenTest exists"
done_criteria:
  - "A crucible fills two molds through channels"
---

# PAR-METAL-13: Channels and mold tables: pouring flow


> see_not_parity: NP-CLIENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
