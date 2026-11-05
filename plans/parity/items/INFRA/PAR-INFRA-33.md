---
id: PAR-INFRA-33
title: "CE block sound volume and pitch: hit and level-event break sounds at the TFC SoundType's values, devices too"
area: INFRA
status: open
wave: 6
deps: [PAR-INFRA-17]
size: S
aliases: ["follow-up of PAR-INFRA-17 (review fix: sound pitch/volume)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/client/TFCSounds.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/blocksound/**"
  - "paper/src/test/java/dev/tfp/paper/blocksound/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSound.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSoundVolumeTest.java"
  - "docs/verification/PAR-INFRA-33/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 and Paper 26.3 + client 26.3 with the S2 spike pack (spikes/s2-destroy-stages, docs/spikes/S2-destroy-stages.md §2); its SoundLog prints `[S2] sound <id> volume=<v> pitch=<p>` for every block sound packet"
  - "Mine tfps2:metal/block/copper with tfp:metal/pickaxe/bronze while a second client watches; place and break a device (firepit, NETHER_WART sound) the same way"
plan:
  - "Current (PAR-INFRA-17): renderer cubes pass TFC's per-event volume/pitch to CraftEngine (`RenderedSound`: break/place (v+1)/2, p*0.8; step v*0.15, p; hit (v+1)/8, p*0.5; fall v*0.5, p*0.75); measured in game, place, step, fall and the breaker's own break sound are right (copper: 1.0/1.2, 0.15/1.5, 0.5/1.125, 1.0/1.2). Two sounds stay off because CraftEngine 26.9.2 does not read the config for them: its mining loop (`BukkitServerPlayer`, every hit) sends the server block's hit sound at a fixed volume 0.5 pitch 0.5 (TFC metal: 0.25/0.75), and its `LevelEventListener` turns the vanilla break level event 2001 into a `replaced.<break sound>` packet at a fixed 1.0/0.8 for every viewer (TFC metal: 1.0/1.2), so the breaker hears two break sounds and other players only the wrong-pitch one."
  - "Device blocks (PAR-INFRA-08 `DeviceSound.yaml`) still pass only the sound ids, so CraftEngine's own BlockSounds defaults apply to all five events; emit {id, volume, pitch} from `dev.tfp.datagen.rendered.RenderedSound` there too (golden in a new DeviceSoundVolumeTest; DeviceYamlGoldenTest changes go through this item's evidence)."
  - "Packet side (decide async, apply sync is not needed: a pure per-packet rewrite on the Netty thread): rewrite outgoing sound packets at a CE custom block's position whose id is that block's hit or `replaced.` break sound to the block's TFC volume/pitch (a lookup table from the generated content keyed by sound id + CE block id); drop the duplicate break packet for the breaker. Wire through a FeatureModule."
tests:
  - "paper: unit test of the rewrite table (copper hit 0.25/0.75, level-event break 1.0/1.2, a non-CE position untouched)"
  - "tools/datagen: DeviceSound volume/pitch golden for STONE, WOOD, NETHER_WART and a TFCSounds type"
done_criteria:
  - "SoundLog shows every metal-block sound at TFC's volume/pitch for the breaker and a second viewer on 1.21.11 and 26.3, one break sound per break; devices too; evidence in docs/verification/PAR-INFRA-33/"
---

# PAR-INFRA-33: CE block sound volume and pitch: hit and level-event break sounds at the TFC SoundType's values, devices too

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
